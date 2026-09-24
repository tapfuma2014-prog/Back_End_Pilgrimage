package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.AuctionPaymentIntentRequest;
import com.pilgrimage.backend.dto.OrderCaptureRequest;
import com.pilgrimage.backend.dto.OrderCheckoutRequest;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.service.CheckoutService;
import com.pilgrimage.backend.service.StripeService;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import com.stripe.model.PaymentIntent;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final StripeService stripeService;
    private final JdbcTemplate jdbcTemplate;
    private final CheckoutService checkoutService;
    private final UserRepository userRepository;

    public PaymentController(
        StripeService stripeService,
        JdbcTemplate jdbcTemplate,
        CheckoutService checkoutService,
        UserRepository userRepository
    ) {
        this.stripeService = stripeService;
        this.jdbcTemplate = jdbcTemplate;
        this.checkoutService = checkoutService;
        this.userRepository = userRepository;
    }

    @PostMapping("/auction-intent")
    public Map<String, String> createAuctionPaymentIntent(@RequestBody AuctionPaymentIntentRequest request) {
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        validateRequest(request);

        Integer ownership = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM auction_winner WHERE auction_id = ? AND artwork_id = ? AND winner_id = ? AND LOWER(created_by) = LOWER(?)",
            Integer.class,
            request.getAuctionId(),
            request.getArtworkId(),
            request.getWinnerId(),
            email
        );
        if (ownership == null || ownership == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payment request does not match authenticated winner");
        }

        List<Map<String, Object>> winners = jdbcTemplate.queryForList(
            """
            SELECT id, winning_bid_amount, payment_status
            FROM auction_winner
            WHERE auction_id = ? AND artwork_id = ? AND winner_id = ?
            ORDER BY created_date DESC
            LIMIT 1
            """,
            request.getAuctionId(),
            request.getArtworkId(),
            request.getWinnerId()
        );
        if (winners.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction winner record not found");
        }

        Map<String, Object> winner = winners.get(0);
        String paymentStatus = winner.get("payment_status") == null
            ? ""
            : winner.get("payment_status").toString();
        if ("completed".equalsIgnoreCase(paymentStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already completed");
        }

        BigDecimal winningAmount = toBigDecimal(winner.get("winning_bid_amount"));
        if (winningAmount == null || winningAmount.compareTo(request.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount does not match winning bid");
        }

        long amountCents = winningAmount.multiply(new BigDecimal("100"))
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();
        if (amountCents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment amount");
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("auction_id", request.getAuctionId());
        metadata.put("artwork_id", request.getArtworkId());
        metadata.put("winner_id", request.getWinnerId());
        metadata.put("winner_record_id", winner.get("id").toString());

        PaymentIntent paymentIntent = stripeService.createAuctionPaymentIntent(amountCents, metadata);

        jdbcTemplate.update(
            """
            UPDATE auction_winner
            SET payment_intent_id = ?,
                payment_status = 'processing',
                updated_date = NOW()
            WHERE id = ?
            """,
            paymentIntent.getId(),
            winner.get("id")
        );

        Map<String, String> response = new LinkedHashMap<>();
        response.put("clientSecret", paymentIntent.getClientSecret());
        response.put("paymentIntentId", paymentIntent.getId());
        return response;
    }

    @PostMapping("/order-intent")
    public Map<String, String> createOrderPaymentIntent(@RequestBody OrderCheckoutRequest request) {
        String email = requireAuthenticatedUser();
        if (request.getUserEmail() == null || !request.getUserEmail().equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order email does not match authenticated user");
        }
        return checkoutService.prepareOrderPaymentIntent(request);
    }

    @PostMapping("/order-capture")
    public Map<String, Object> captureOrderPayment(@RequestBody OrderCaptureRequest request) {
        String email = requireAuthenticatedUser();
        requireOrderOwnershipOrAdmin(request.getOrderId(), email);
        return checkoutService.captureOrderPayment(request);
    }

    private String requireAuthenticatedUser() {
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return email;
    }

    private void requireOrderOwnershipOrAdmin(String orderId, String email) {
        if (EntityAuthorizationHelper.isAdmin(userRepository)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE id = ? AND LOWER(created_by) = LOWER(?)",
            Integer.class,
            orderId,
            email
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order does not belong to authenticated user");
        }
    }

    private void validateRequest(AuctionPaymentIntentRequest request) {
        if (request == null
            || isBlank(request.getAuctionId())
            || isBlank(request.getArtworkId())
            || isBlank(request.getWinnerId())
            || request.getAmount() == null
            || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required payment fields");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
}
