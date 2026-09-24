package com.pilgrimage.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.service.AuctionCloseService;
import com.pilgrimage.backend.service.NotificationDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AuctionCloseServiceImpl implements AuctionCloseService {

    private static final Logger log = LoggerFactory.getLogger(AuctionCloseServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDispatchService notificationDispatchService;
    private final ObjectMapper objectMapper;

    public AuctionCloseServiceImpl(
        JdbcTemplate jdbcTemplate,
        NotificationDispatchService notificationDispatchService,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationDispatchService = notificationDispatchService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void closeExpiredAuctions() {
        List<Map<String, Object>> expiredAuctions = jdbcTemplate.queryForList(
            """
            SELECT id, title, status, reserve_price, featured_artworks::text AS featured_artworks
            FROM auction
            WHERE end_date <= NOW()
              AND status IS DISTINCT FROM 'completed'
              AND status IS DISTINCT FROM 'cancelled'
            """
        );

        for (Map<String, Object> auction : expiredAuctions) {
            closeAuction(auction);
        }
    }

    private void closeAuction(Map<String, Object> auction) {
        String auctionId = stringValue(auction.get("id"));
        String auctionTitle = stringValue(auction.get("title"));
        Set<String> artworkIds = resolveArtworkIds(auctionId, stringValue(auction.get("featured_artworks")));

        jdbcTemplate.update(
            "UPDATE auction SET status = 'completed', updated_date = NOW() WHERE id = ?",
            auctionId
        );
        log.info("Closed auction {} ({})", auctionId, auctionTitle);

        BigDecimal reservePrice = toBigDecimal(auction.get("reserve_price"));

        for (String artworkId : artworkIds) {
            recordWinnerIfEligible(auctionId, auctionTitle, artworkId, reservePrice);
        }
    }

    private Set<String> resolveArtworkIds(String auctionId, String featuredArtworksJson) {
        Set<String> artworkIds = new LinkedHashSet<>();
        artworkIds.addAll(parseFeaturedArtworks(featuredArtworksJson));

        List<String> bidArtworkIds = jdbcTemplate.queryForList(
            "SELECT DISTINCT artwork_id FROM auction_bid WHERE auction_id = ? AND artwork_id IS NOT NULL",
            String.class,
            auctionId
        );
        artworkIds.addAll(bidArtworkIds);
        artworkIds.remove("");
        return artworkIds;
    }

    private List<String> parseFeaturedArtworks(String featuredArtworksJson) {
        if (featuredArtworksJson == null || featuredArtworksJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(featuredArtworksJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse featured_artworks for auction close: {}", featuredArtworksJson);
            return List.of();
        }
    }

    private void recordWinnerIfEligible(
        String auctionId,
        String auctionTitle,
        String artworkId,
        BigDecimal reservePrice
    ) {
        Integer existingWinnerCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auction_winner
            WHERE auction_id = ? AND artwork_id = ?
            """,
            Integer.class,
            auctionId,
            artworkId
        );
        if (existingWinnerCount != null && existingWinnerCount > 0) {
            return;
        }

        List<Map<String, Object>> winningBids = jdbcTemplate.queryForList(
            """
            SELECT id, bidder_id, bidder_name, bid_amount, created_by
            FROM auction_bid
            WHERE auction_id = ? AND artwork_id = ?
            ORDER BY is_winning DESC, bid_amount DESC, bid_time DESC
            LIMIT 1
            """,
            auctionId,
            artworkId
        );
        if (winningBids.isEmpty()) {
            return;
        }

        Map<String, Object> winningBid = winningBids.get(0);
        String winnerId = stringValue(winningBid.get("bidder_id"));
        BigDecimal winningAmount = toBigDecimal(winningBid.get("bid_amount"));
        if (winnerId.isBlank() || winningAmount == null) {
            return;
        }
        if (reservePrice != null && winningAmount.compareTo(reservePrice) < 0) {
            log.info(
                "Reserve not met for auction {} artwork {}: highest bid {} below reserve {}",
                auctionId,
                artworkId,
                winningAmount,
                reservePrice
            );
            return;
        }

        String artworkTitle = jdbcTemplate.query(
            "SELECT title FROM artwork WHERE id = ?",
            rs -> rs.next() ? rs.getString("title") : artworkId,
            artworkId
        );

        // Resolve the winner's verified email up-front so it can be stored as
        // created_by (used by ownership checks) and for the notification.
        String winnerEmail = resolveWinnerEmail(winnerId);

        String winnerRecordId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
            INSERT INTO auction_winner (
                id, auction_id, artwork_id, winner_id, winning_bid_amount,
                payment_status, created_by, created_date, updated_date
            ) VALUES (?, ?, ?, ?, ?, 'pending', ?, NOW(), NOW())
            """,
            winnerRecordId,
            auctionId,
            artworkId,
            winnerId,
            winningAmount,
            winnerEmail.isBlank() ? null : winnerEmail
        );
        log.info(
            "Recorded auction winner {} for auction {} artwork {} amount {}",
            winnerRecordId,
            auctionId,
            artworkId,
            winningAmount
        );

        notifyWinner(auctionTitle, artworkTitle, winningAmount, winnerId, winningBid);
    }

    private void notifyWinner(
        String auctionTitle,
        String artworkTitle,
        BigDecimal winningAmount,
        String winnerId,
        Map<String, Object> winningBid
    ) {
        String recipientEmail = resolveWinnerEmail(winnerId);
        if (recipientEmail.isBlank()) {
            log.warn("No email found for auction winner {}", winnerId);
            return;
        }

        String subject = "Congratulations! You won \"" + artworkTitle + "\"";
        String body = """
            Congratulations on winning "%s" at the %s!

            Winning Bid: $%s

            Your artwork is now available in My Auction Wins. Please proceed to checkout to complete your purchase.

            Thank you for participating in our auction!

            Best regards,
            53 Cox Road
            """.formatted(
            artworkTitle,
            auctionTitle,
            winningAmount.stripTrailingZeros().toPlainString()
        );

        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("to", recipientEmail);
        emailPayload.put("subject", subject);
        emailPayload.put("body", body);
        notificationDispatchService.sendEmail(emailPayload);
    }

    private String resolveWinnerEmail(String winnerId) {
        // Only the verified users table is trusted for the recipient address;
        // the client-influenced auction_bid.created_by field is never used.
        List<String> emails = jdbcTemplate.query(
            "SELECT email FROM users WHERE id = ?",
            (rs, rowNum) -> rs.getString("email"),
            winnerId
        );
        if (!emails.isEmpty() && emails.get(0) != null && !emails.get(0).isBlank()) {
            return emails.get(0).trim();
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
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
