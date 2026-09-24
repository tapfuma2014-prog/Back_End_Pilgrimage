package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.dto.PlaceBidRequest;
import com.pilgrimage.backend.service.AuctionBidService;
import com.pilgrimage.backend.service.NotificationDispatchService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuctionBidServiceImpl implements AuctionBidService {

    private static final int DEFAULT_EXTENSION_MINUTES = 5;
    // Hard cap on proxy-bid resolution rounds to prevent unbounded loops.
    private static final int MAX_PROXY_ROUNDS = 500;

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDispatchService notificationDispatchService;

    public AuctionBidServiceImpl(
        JdbcTemplate jdbcTemplate,
        NotificationDispatchService notificationDispatchService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationDispatchService = notificationDispatchService;
    }

    @Override
    @Transactional
    public List<Map<String, Object>> placeBid(PlaceBidRequest request) {
        validateRequest(request);

        lockAuction(request.getAuctionId());
        validateAuctionOpen(request.getAuctionId());

        String artworkTitle = loadArtworkTitle(request.getArtworkId());
        BigDecimal startingPrice = loadArtworkPrice(request.getArtworkId());
        BigDecimal currentHigh = loadCurrentHighBid(request.getAuctionId(), request.getArtworkId(), startingPrice);
        BigDecimal minimumBid = minimumNextBid(currentHigh);

        if (request.getBidAmount().compareTo(minimumBid) < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Bid must be at least $" + minimumBid.toPlainString()
            );
        }
        if (request.getBidAmount().compareTo(currentHigh) <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Bid must be higher than $" + currentHigh.toPlainString()
            );
        }
        if (request.getMaxProxyBid() != null
            && request.getMaxProxyBid().compareTo(request.getBidAmount()) < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Max proxy bid must be greater than or equal to the bid amount"
            );
        }

        List<Map<String, Object>> createdBids = new ArrayList<>();
        createdBids.add(insertBid(
            request.getAuctionId(),
            request.getArtworkId(),
            request.getBidderId(),
            request.getBidderName(),
            request.getBidAmount(),
            request.getMaxProxyBid(),
            false
        ));

        resolveProxyBids(request.getAuctionId(), request.getArtworkId(), artworkTitle, createdBids);
        maybeAutoExtendEndDate(request.getAuctionId());

        return createdBids;
    }

    private void maybeAutoExtendEndDate(String auctionId) {
        Map<String, Object> auction = jdbcTemplate.query(
            """
            SELECT auto_extend, extension_minutes, end_date
            FROM auction
            WHERE id = ?
            """,
            rs -> {
                if (!rs.next()) {
                    return Map.<String, Object>of();
                }
                Map<String, Object> row = new HashMap<>();
                row.put("auto_extend", rs.getObject("auto_extend"));
                row.put("extension_minutes", rs.getObject("extension_minutes"));
                row.put("end_date", rs.getTimestamp("end_date"));
                return row;
            },
            auctionId
        );
        if (auction.isEmpty() || !Boolean.TRUE.equals(auction.get("auto_extend"))) {
            return;
        }

        Timestamp endTimestamp = (Timestamp) auction.get("end_date");
        if (endTimestamp == null) {
            return;
        }

        int extensionMinutes = toExtensionMinutes(auction.get("extension_minutes"));
        Instant now = Instant.now();
        Instant end = endTimestamp.toInstant();
        Instant windowStart = end.minus(extensionMinutes, ChronoUnit.MINUTES);
        if (now.isBefore(windowStart) || !now.isBefore(end)) {
            return;
        }

        jdbcTemplate.update(
            """
            UPDATE auction
            SET end_date = ?, updated_date = NOW()
            WHERE id = ?
            """,
            Timestamp.from(end.plus(extensionMinutes, ChronoUnit.MINUTES)),
            auctionId
        );
    }

    private int toExtensionMinutes(Object value) {
        if (value == null) {
            return DEFAULT_EXTENSION_MINUTES;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return DEFAULT_EXTENSION_MINUTES;
        }
    }

    private void resolveProxyBids(
        String auctionId,
        String artworkId,
        String artworkTitle,
        List<Map<String, Object>> createdBids
    ) {
        jdbcTemplate.update(
            """
            UPDATE auction_bid
            SET is_winning = FALSE,
                status = CASE WHEN status = 'active' THEN 'outbid' ELSE status END,
                updated_date = NOW()
            WHERE auction_id = ? AND artwork_id = ?
            """,
            auctionId,
            artworkId
        );

        int rounds = 0;
        String lastLeaderId = null;
        while (rounds++ < MAX_PROXY_ROUNDS) {
            BidSnapshot leader = loadLeadingBid(auctionId, artworkId);
            if (leader == null) {
                break;
            }
            // Defensive: never process the same leading bid twice.
            if (leader.id.equals(lastLeaderId)) {
                markWinningBid(leader.id);
                break;
            }
            lastLeaderId = leader.id;

            Map<String, BigDecimal> proxyMaxima = loadProxyMaxima(auctionId, artworkId);
            BigDecimal requiredBid = minimumNextBid(leader.bidAmount);

            String counterBidderId = null;
            BigDecimal counterMax = null;
            for (Map.Entry<String, BigDecimal> entry : proxyMaxima.entrySet()) {
                String bidderId = entry.getKey();
                BigDecimal proxyMax = entry.getValue();
                if (bidderId.equals(leader.bidderId)) {
                    continue;
                }
                if (proxyMax.compareTo(requiredBid) < 0) {
                    continue;
                }
                if (counterMax == null || proxyMax.compareTo(counterMax) > 0) {
                    counterMax = proxyMax;
                    counterBidderId = bidderId;
                }
            }

            if (counterBidderId == null || counterMax == null) {
                markWinningBid(leader.id);
                break;
            }

            BigDecimal counterAmount = counterMax.min(requiredBid);
            if (counterAmount.compareTo(leader.bidAmount) <= 0) {
                markWinningBid(leader.id);
                break;
            }

            String counterBidderName = loadBidderName(counterBidderId, auctionId, artworkId);
            Map<String, Object> counterBid = insertBid(
                auctionId,
                artworkId,
                counterBidderId,
                counterBidderName,
                counterAmount,
                counterMax,
                true
            );
            createdBids.add(counterBid);
            notifyOutbid(leader.bidderId, artworkTitle, counterAmount);
        }
    }

    private void markWinningBid(String bidId) {
        jdbcTemplate.update(
            """
            UPDATE auction_bid
            SET is_winning = TRUE,
                status = 'active',
                updated_date = NOW()
            WHERE id = ?
            """,
            bidId
        );
    }

    private Map<String, Object> insertBid(
        String auctionId,
        String artworkId,
        String bidderId,
        String bidderName,
        BigDecimal bidAmount,
        BigDecimal maxProxyBid,
        boolean isProxyBid
    ) {
        String bidId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO auction_bid (
                id, auction_id, artwork_id, bidder_id, bidder_name,
                bid_amount, max_proxy_bid, is_proxy_bid, bid_time,
                is_winning, status, created_date, updated_date, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), FALSE, 'active', NOW(), NOW(), 'system')
            """,
            bidId,
            auctionId,
            artworkId,
            bidderId,
            bidderName,
            bidAmount,
            maxProxyBid,
            isProxyBid
        );
        return fetchBidById(bidId);
    }

    private Map<String, Object> fetchBidById(String bidId) {
        // Explicit column list: never expose max_proxy_bid or created_by to callers.
        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
            SELECT id, auction_id, artwork_id, bidder_id, bidder_name,
                   bid_amount, is_proxy_bid, bid_time, is_winning, status,
                   created_date, updated_date
            FROM auction_bid WHERE id = ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                var meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            },
            bidId
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private BidSnapshot loadLeadingBid(String auctionId, String artworkId) {
        List<BidSnapshot> leaders = jdbcTemplate.query(
            """
            SELECT id, bidder_id, bid_amount
            FROM auction_bid
            WHERE auction_id = ? AND artwork_id = ?
            ORDER BY bid_amount DESC, bid_time DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new BidSnapshot(
                rs.getString("id"),
                rs.getString("bidder_id"),
                rs.getBigDecimal("bid_amount")
            ),
            auctionId,
            artworkId
        );
        return leaders.isEmpty() ? null : leaders.get(0);
    }

    private Map<String, BigDecimal> loadProxyMaxima(String auctionId, String artworkId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT bidder_id, MAX(max_proxy_bid) AS proxy_max
            FROM auction_bid
            WHERE auction_id = ?
              AND artwork_id = ?
              AND max_proxy_bid IS NOT NULL
            GROUP BY bidder_id
            """,
            auctionId,
            artworkId
        );
        Map<String, BigDecimal> maxima = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object bidderId = row.get("bidder_id");
            Object proxyMax = row.get("proxy_max");
            if (bidderId != null && proxyMax instanceof BigDecimal decimal) {
                maxima.put(bidderId.toString(), decimal);
            }
        }
        return maxima;
    }

    private BigDecimal loadCurrentHighBid(String auctionId, String artworkId, BigDecimal startingPrice) {
        BigDecimal highest = jdbcTemplate.query(
            """
            SELECT MAX(bid_amount) AS highest
            FROM auction_bid
            WHERE auction_id = ? AND artwork_id = ?
            """,
            rs -> rs.next() ? rs.getBigDecimal("highest") : null,
            auctionId,
            artworkId
        );
        if (highest == null) {
            return startingPrice;
        }
        return highest;
    }

    private BigDecimal loadArtworkPrice(String artworkId) {
        BigDecimal price = jdbcTemplate.query(
            "SELECT price FROM artwork WHERE id = ?",
            rs -> rs.next() ? rs.getBigDecimal("price") : BigDecimal.ZERO,
            artworkId
        );
        return price == null ? BigDecimal.ZERO : price;
    }

    private String loadArtworkTitle(String artworkId) {
        return jdbcTemplate.query(
            "SELECT title FROM artwork WHERE id = ?",
            rs -> rs.next() ? rs.getString("title") : artworkId,
            artworkId
        );
    }

    private String loadBidderName(String bidderId, String auctionId, String artworkId) {
        List<String> names = jdbcTemplate.query(
            """
            SELECT bidder_name
            FROM auction_bid
            WHERE bidder_id = ? AND auction_id = ? AND artwork_id = ?
            ORDER BY bid_time DESC
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getString("bidder_name"),
            bidderId,
            auctionId,
            artworkId
        );
        if (!names.isEmpty() && names.get(0) != null && !names.get(0).isBlank()) {
            return names.get(0);
        }
        return bidderId;
    }

    private void lockAuction(String auctionId) {
        jdbcTemplate.query(
            "SELECT id FROM auction WHERE id = ? FOR UPDATE",
            rs -> null,
            auctionId
        );
    }

    private void validateAuctionOpen(String auctionId) {
        Map<String, Object> auction = jdbcTemplate.query(
            "SELECT start_date, end_date, status FROM auction WHERE id = ?",
            rs -> {
                if (!rs.next()) {
                    return Map.of();
                }
                Map<String, Object> row = new HashMap<>();
                row.put("start_date", rs.getTimestamp("start_date"));
                row.put("end_date", rs.getTimestamp("end_date"));
                row.put("status", rs.getString("status"));
                return row;
            },
            auctionId
        );
        if (auction.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found");
        }
        if ("cancelled".equalsIgnoreCase(String.valueOf(auction.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auction is cancelled");
        }

        Instant now = Instant.now();
        Timestamp start = (Timestamp) auction.get("start_date");
        Timestamp end = (Timestamp) auction.get("end_date");
        if (start != null && now.isBefore(start.toInstant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auction has not started");
        }
        if (end != null && !now.isBefore(end.toInstant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Auction has ended");
        }
    }

    private void notifyOutbid(String outbidBidderId, String artworkTitle, BigDecimal newBidAmount) {
        // Only the verified users table is trusted for the recipient address;
        // the client-influenced auction_bid.created_by field is never used.
        String email = jdbcTemplate.query(
            "SELECT email FROM users WHERE id = ?",
            rs -> rs.next() ? rs.getString("email") : null,
            outbidBidderId
        );
        if (email == null || !email.contains("@")) {
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", email);
        payload.put("subject", "You've been outbid!");
        payload.put(
            "body",
            "Someone outbid you on \"" + artworkTitle + "\". Current bid: $"
                + newBidAmount.stripTrailingZeros().toPlainString()
        );
        notificationDispatchService.sendEmail(payload);
    }

    private BigDecimal minimumNextBid(BigDecimal currentHigh) {
        if (currentHigh == null || currentHigh.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return currentHigh.multiply(new BigDecimal("1.05"))
            .setScale(0, RoundingMode.CEILING);
    }

    private void validateRequest(PlaceBidRequest request) {
        if (request == null
            || isBlank(request.getAuctionId())
            || isBlank(request.getArtworkId())
            || isBlank(request.getBidderId())
            || request.getBidAmount() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required bid fields");
        }
        if (request.getBidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bid amount must be positive");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final class BidSnapshot {
        private final String id;
        private final String bidderId;
        private final BigDecimal bidAmount;

        private BidSnapshot(String id, String bidderId, BigDecimal bidAmount) {
            this.id = id;
            this.bidderId = bidderId;
            this.bidAmount = bidAmount;
        }
    }
}
