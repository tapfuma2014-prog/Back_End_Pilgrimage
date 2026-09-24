package com.pilgrimage.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.dto.OrderCaptureRequest;
import com.pilgrimage.backend.dto.OrderCheckoutRequest;
import com.pilgrimage.backend.service.CheckoutService;
import com.pilgrimage.backend.service.StripeService;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import com.stripe.model.PaymentIntent;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private static final BigDecimal MAX_SHIPPING_COST = new BigDecimal("10000");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final StripeService stripeService;
    private final ObjectMapper objectMapper;

    public CheckoutServiceImpl(
        JdbcTemplate jdbcTemplate,
        StripeService stripeService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.stripeService = stripeService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public Map<String, String> prepareOrderPaymentIntent(OrderCheckoutRequest request) {
        validateCheckoutRequest(request);

        // Identity always comes from the authenticated principal - never trust
        // a client-supplied email for cart/order ownership.
        String userEmail = EntityAuthorizationHelper.currentUserEmail();
        if (userEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        userEmail = normalizeEmail(userEmail);

        BigDecimal shippingCost = clampMoney(request.getShippingCost(), MAX_SHIPPING_COST);
        boolean merchandiseCheckout = isMerchandiseCheckout(request);

        String orderId = UUID.randomUUID().toString();
        BigDecimal total;
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("order_id", orderId);
        metadata.put("user_email", userEmail);

        if (merchandiseCheckout) {
            CheckoutCart cart = loadMerchandiseCheckoutCart(userEmail);
            // Discount can never exceed the merchandise subtotal.
            BigDecimal discount = clampMoney(request.getDiscount(), cart.subtotal);
            total = cart.subtotal.subtract(discount).add(shippingCost);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO;
            }
            insertPendingMerchOrder(orderId, request, userEmail, cart, total, shippingCost, discount);
            metadata.put("checkout_type", "merchandise");
        } else {
            CheckoutCart cart = loadArtworkCheckoutCart(userEmail);
            total = cart.subtotal.add(shippingCost);
            insertPendingArtOrder(orderId, request, userEmail, cart, total, shippingCost);
            metadata.put("checkout_type", "artwork");
        }

        long amountCents = toAmountCents(total);
        PaymentIntent paymentIntent = stripeService.createPaymentIntent(amountCents, metadata);

        if (merchandiseCheckout) {
            jdbcTemplate.update(
                """
                UPDATE merch_order
                SET payment_intent_id = ?,
                    payment_status = 'processing',
                    updated_date = NOW()
                WHERE id = ?
                """,
                paymentIntent.getId(),
                orderId
            );
        } else {
            jdbcTemplate.update(
                """
                UPDATE orders
                SET payment_intent_id = ?,
                    payment_status = 'processing',
                    updated_date = NOW()
                WHERE id = ?
                """,
                paymentIntent.getId(),
                orderId
            );
        }

        Map<String, String> response = new LinkedHashMap<>();
        response.put("orderId", orderId);
        response.put("clientSecret", paymentIntent.getClientSecret());
        response.put("paymentIntentId", paymentIntent.getId());
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> captureOrderPayment(OrderCaptureRequest request) {
        if (request == null
            || isBlank(request.getOrderId())
            || isBlank(request.getPaymentIntentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order ID and payment intent ID are required");
        }

        Map<String, Object> order = fetchArtOrder(request.getOrderId());
        boolean merchandiseOrder = order.isEmpty();
        if (merchandiseOrder) {
            order = fetchMerchOrder(request.getOrderId());
        }

        if (order.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        String paymentStatus = stringValue(order.get("payment_status"));
        if ("completed".equalsIgnoreCase(paymentStatus) || "paid".equalsIgnoreCase(paymentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already completed");
        }

        String storedIntentId = stringValue(order.get("payment_intent_id"));
        if (storedIntentId != null && !storedIntentId.equals(request.getPaymentIntentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment intent does not match order");
        }

        PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(request.getPaymentIntentId());
        if (!"succeeded".equalsIgnoreCase(paymentIntent.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment has not been completed");
        }

        BigDecimal orderTotal = merchandiseOrder
            ? toBigDecimal(order.get("total"))
            : toBigDecimal(order.get("total_price"));
        long expectedCents = toAmountCents(orderTotal);
        if (paymentIntent.getAmount() == null || paymentIntent.getAmount() != expectedCents) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount does not match order total");
        }

        List<Map<String, Object>> items = parseItems(order.get("items"));
        if (merchandiseOrder) {
            clearMerchandiseCartForOrder(stringValue(order.get("created_by")), items);
            jdbcTemplate.update(
                """
                UPDATE merch_order
                SET payment_status = 'paid',
                    order_status = 'processing',
                    payment_intent_id = ?,
                    updated_date = NOW()
                WHERE id = ?
                """,
                request.getPaymentIntentId(),
                request.getOrderId()
            );
            return fetchMerchOrderById(request.getOrderId());
        }

        reserveInventory(items);
        clearArtworkCartForOrder(stringValue(order.get("created_by")), items);

        jdbcTemplate.update(
            """
            UPDATE orders
            SET payment_status = 'completed',
                status = 'confirmed',
                payment_intent_id = ?,
                updated_date = NOW()
            WHERE id = ?
            """,
            request.getPaymentIntentId(),
            request.getOrderId()
        );

        return fetchArtOrderById(request.getOrderId());
    }

    private boolean isMerchandiseCheckout(OrderCheckoutRequest request) {
        return request.getCheckoutType() != null
            && "merchandise".equalsIgnoreCase(request.getCheckoutType().trim());
    }

    private Map<String, Object> fetchArtOrder(String orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, items::text AS items, total_price, payment_status, payment_intent_id, status, created_by
            FROM orders
            WHERE id = ?
            """,
            orderId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> fetchMerchOrder(String orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id, items::text AS items, total, payment_status, payment_intent_id, order_status, created_by
            FROM merch_order
            WHERE id = ?
            """,
            orderId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private void validateCheckoutRequest(OrderCheckoutRequest request) {
        if (request == null
            || isBlank(request.getUserEmail())
            || isBlank(request.getShippingName())
            || isBlank(request.getShippingAddress())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required checkout fields");
        }
    }

    private CheckoutCart loadArtworkCheckoutCart(String userEmail) {
        List<Map<String, Object>> cartRows = jdbcTemplate.queryForList(
            """
            SELECT c.id, c.artwork_id, c.quantity, c.price_at_addition
            FROM cart c
            WHERE LOWER(c.created_by) = LOWER(?)
              AND c.artwork_id IS NOT NULL
            """,
            userEmail
        );
        if (cartRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        List<Map<String, Object>> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map<String, Object> cartRow : cartRows) {
            String artworkId = stringValue(cartRow.get("artwork_id"));
            Map<String, Object> artwork = loadAvailableArtwork(artworkId);

            BigDecimal quantity = toBigDecimal(cartRow.get("quantity"));
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                quantity = BigDecimal.ONE;
            }

            BigDecimal unitPrice = toBigDecimal(artwork.get("price"));
            if (unitPrice == null) {
                unitPrice = toBigDecimal(cartRow.get("price_at_addition"));
            }
            if (unitPrice == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artwork price is unavailable");
            }

            Map<String, Object> orderItem = new LinkedHashMap<>();
            orderItem.put("artwork_id", artworkId);
            orderItem.put("title", stringValue(artwork.get("title")));
            orderItem.put("artist", stringValue(artwork.get("artist")));
            orderItem.put("quantity", quantity);
            orderItem.put("price", unitPrice);
            orderItems.add(orderItem);

            subtotal = subtotal.add(unitPrice.multiply(quantity));
        }

        return new CheckoutCart(orderItems, subtotal);
    }

    private CheckoutCart loadMerchandiseCheckoutCart(String userEmail) {
        List<Map<String, Object>> cartRows = jdbcTemplate.queryForList(
            """
            SELECT c.id, c.merchandise_id, c.artwork_id, c.quantity, c.price_at_addition, c.selected_size
            FROM cart c
            WHERE LOWER(c.created_by) = LOWER(?)
              AND (c.merchandise_id IS NOT NULL OR c.artwork_id IS NOT NULL)
            """,
            userEmail
        );
        if (cartRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        List<Map<String, Object>> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map<String, Object> cartRow : cartRows) {
            String merchandiseId = stringValue(cartRow.get("merchandise_id"));
            String artworkId = stringValue(cartRow.get("artwork_id"));
            BigDecimal quantity = toBigDecimal(cartRow.get("quantity"));
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                quantity = BigDecimal.ONE;
            }

            Map<String, Object> orderItem = new LinkedHashMap<>();
            BigDecimal unitPrice = toBigDecimal(cartRow.get("price_at_addition"));

            if (merchandiseId != null && !merchandiseId.isBlank()) {
                Map<String, Object> product = loadMerchandise(merchandiseId);
                if (unitPrice == null) {
                    unitPrice = toBigDecimal(product.get("price"));
                }
                orderItem.put("merchandise_id", merchandiseId);
                orderItem.put("name", stringValue(product.get("name")));
                orderItem.put("image_url", stringValue(product.get("image_url")));
            } else if (artworkId != null && !artworkId.isBlank()) {
                Map<String, Object> artwork = loadAvailableArtwork(artworkId);
                if (unitPrice == null) {
                    unitPrice = toBigDecimal(artwork.get("price"));
                }
                orderItem.put("artwork_id", artworkId);
                orderItem.put("name", stringValue(artwork.get("title")));
                orderItem.put("image_url", stringValue(artwork.get("image_url")));
            } else {
                continue;
            }

            if (unitPrice == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item price is unavailable");
            }

            Object selectedSize = cartRow.get("selected_size");
            if (selectedSize != null) {
                orderItem.put("size", selectedSize);
            }
            orderItem.put("price", unitPrice);
            orderItem.put("quantity", quantity);
            orderItems.add(orderItem);
            subtotal = subtotal.add(unitPrice.multiply(quantity));
        }

        if (orderItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        return new CheckoutCart(orderItems, subtotal);
    }

    private Map<String, Object> loadMerchandise(String merchandiseId) {
        List<Map<String, Object>> products = jdbcTemplate.queryForList(
            """
            SELECT id, name, price, image_url, in_stock
            FROM merchandise
            WHERE id = ?
            """,
            merchandiseId
        );
        if (products.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchandise not found: " + merchandiseId);
        }
        Map<String, Object> product = products.get(0);
        Object inStock = product.get("in_stock");
        if (inStock instanceof Boolean available && !available) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merchandise is out of stock: " + merchandiseId);
        }
        return product;
    }

    private Map<String, Object> loadAvailableArtwork(String artworkId) {
        List<Map<String, Object>> artworks = jdbcTemplate.queryForList(
            """
            SELECT id, title, artist, price, "isInStock"
            FROM artwork
            WHERE id = ?
            """,
            artworkId
        );
        if (artworks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artwork not found: " + artworkId);
        }

        Map<String, Object> artwork = artworks.get(0);
        Object inStock = artwork.get("isInStock");
        if (inStock instanceof Boolean available && !available) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Artwork is no longer available: " + artworkId);
        }
        return artwork;
    }

    private void insertPendingArtOrder(
        String orderId,
        OrderCheckoutRequest request,
        String userEmail,
        CheckoutCart cart,
        BigDecimal total,
        BigDecimal shippingCost
    ) {
        String itemsJson = toJson(cart.items);
        String currency = isBlank(request.getCurrency()) ? "AUD" : request.getCurrency();

        jdbcTemplate.update(
            """
            INSERT INTO orders (
                id, items, total_price, currency,
                shipping_name, shipping_address, shipping_city, shipping_state, shipping_postcode,
                shipping_method, shipping_cost, status, notes,
                payment_status, created_by, created_date, updated_date
            ) VALUES (?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'awaiting_payment', ?, 'pending', ?, NOW(), NOW())
            """,
            orderId,
            itemsJson,
            total,
            currency,
            request.getShippingName(),
            request.getShippingAddress(),
            request.getShippingCity(),
            request.getShippingState(),
            request.getShippingPostcode(),
            defaultString(request.getShippingMethod(), "standard"),
            shippingCost,
            request.getNotes(),
            userEmail
        );
    }

    private void insertPendingMerchOrder(
        String orderId,
        OrderCheckoutRequest request,
        String userEmail,
        CheckoutCart cart,
        BigDecimal total,
        BigDecimal shippingCost,
        BigDecimal discount
    ) {
        String itemsJson = toJson(cart.items);
        // Random suffix so order numbers are not predictable from the timestamp alone.
        String orderNumber = "ORD-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
            + "-" + Integer.toString(SECURE_RANDOM.nextInt(36 * 36 * 36 * 36), 36).toUpperCase();

        jdbcTemplate.update(
            """
            INSERT INTO merch_order (
                id, order_number, items, subtotal, total,
                shipping_name, shipping_email, shipping_phone,
                shipping_address, shipping_city, shipping_state, shipping_postcode, shipping_country,
                shipping_cost, discount, promo_code,
                payment_method, payment_status, order_status, notes,
                created_by, created_date, updated_date
            ) VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'card', 'pending', 'pending', ?, ?, NOW(), NOW())
            """,
            orderId,
            orderNumber,
            itemsJson,
            cart.subtotal,
            total,
            request.getShippingName(),
            defaultString(request.getShippingEmail(), userEmail),
            request.getShippingPhone(),
            request.getShippingAddress(),
            request.getShippingCity(),
            request.getShippingState(),
            request.getShippingPostcode(),
            defaultString(request.getShippingCountry(), "Australia"),
            shippingCost,
            discount,
            request.getPromoCode(),
            request.getNotes(),
            userEmail
        );
    }

    private void reserveInventory(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String artworkId = stringValue(item.get("artwork_id"));
            int updated = jdbcTemplate.update(
                """
                UPDATE artwork
                SET "isInStock" = FALSE,
                    updated_date = NOW()
                WHERE id = ?
                  AND ("isInStock" IS TRUE OR "isInStock" IS NULL)
                """,
                artworkId
            );
            if (updated == 0) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Artwork is no longer available: " + artworkId
                );
            }
        }
    }

    private void clearArtworkCartForOrder(String userEmail, List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            jdbcTemplate.update(
                """
                DELETE FROM cart
                WHERE LOWER(created_by) = LOWER(?)
                  AND artwork_id = ?
                """,
                userEmail,
                stringValue(item.get("artwork_id"))
            );
        }
    }

    private void clearMerchandiseCartForOrder(String userEmail, List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String merchandiseId = stringValue(item.get("merchandise_id"));
            String artworkId = stringValue(item.get("artwork_id"));
            if (merchandiseId != null && !merchandiseId.isBlank()) {
                jdbcTemplate.update(
                    """
                    DELETE FROM cart
                    WHERE LOWER(created_by) = LOWER(?)
                      AND merchandise_id = ?
                    """,
                    userEmail,
                    merchandiseId
                );
            } else if (artworkId != null && !artworkId.isBlank()) {
                jdbcTemplate.update(
                    """
                    DELETE FROM cart
                    WHERE LOWER(created_by) = LOWER(?)
                      AND artwork_id = ?
                    """,
                    userEmail,
                    artworkId
                );
            }
        }
    }

    private Map<String, Object> fetchArtOrderById(String orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM orders WHERE id = ?",
            orderId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private Map<String, Object> fetchMerchOrderById(String orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM merch_order WHERE id = ?",
            orderId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> parseItems(Object itemsValue) {
        if (itemsValue == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order items are missing");
        }
        try {
            return objectMapper.readValue(
                itemsValue.toString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order items are invalid");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize order items");
        }
    }

    private long toAmountCents(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment amount");
        }
        return amount.multiply(new BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Returns the client-supplied monetary value clamped to [0, max].
     * Negative or absurd values are rejected rather than silently trusted.
     */
    private BigDecimal clampMoney(BigDecimal value, BigDecimal max) {
        BigDecimal amount = defaultZero(value);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amounts cannot be negative");
        }
        if (max != null && amount.compareTo(max) > 0) {
            return max;
        }
        return amount;
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class CheckoutCart {
        private final List<Map<String, Object>> items;
        private final BigDecimal subtotal;

        private CheckoutCart(List<Map<String, Object>> items, BigDecimal subtotal) {
            this.items = items;
            this.subtotal = subtotal;
        }
    }
}
