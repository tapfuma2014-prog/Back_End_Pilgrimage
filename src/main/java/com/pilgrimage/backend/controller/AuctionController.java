package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.dto.PlaceBidRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.service.AuctionBidService;
import com.pilgrimage.backend.util.SimpleRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbcTemplate;
    private final AuctionBidService auctionBidService;
    private final UserRepository userRepository;
    // Throttle bid spam: max 30 bids per user per minute.
    private final SimpleRateLimiter bidRateLimiter =
        new SimpleRateLimiter(30, 60_000L, "Too many bids. Please slow down.");

    public AuctionController(JdbcTemplate jdbcTemplate, AuctionBidService auctionBidService, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.auctionBidService = auctionBidService;
        this.userRepository = userRepository;
    }

    @GetMapping("/bids")
    public List<Map<String, Object>> listBids(
            @RequestParam("auctionId") String auctionId,
            @RequestParam(name = "artworkId", required = false) String artworkId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "auctionId is required");
        }

        StringBuilder sql = new StringBuilder("""
            SELECT id, auction_id, artwork_id, bidder_id, bidder_name,
                   bid_amount, is_proxy_bid, bid_time, is_winning, status,
                   max_proxy_bid, created_date, updated_date
            FROM auction_bid
            WHERE auction_id = ?
        """);
        List<Object> params = new ArrayList<>();
        params.add(auctionId);

        if (artworkId != null && !artworkId.isBlank()) {
            sql.append(" AND (artwork_id = ? OR artwork_id = 'sample-' || ?)");
            params.add(artworkId);
            params.add(artworkId);
        }
        sql.append(" ORDER BY bid_amount DESC, bid_time DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getString("id"));
            map.put("auction_id", rs.getString("auction_id"));
            map.put("artwork_id", rs.getString("artwork_id"));
            map.put("bidder_id", rs.getString("bidder_id"));
            map.put("bidder_name", rs.getString("bidder_name"));
            map.put("bid_amount", rs.getBigDecimal("bid_amount"));
            map.put("max_proxy_bid", rs.getBigDecimal("max_proxy_bid"));
            map.put("is_proxy_bid", rs.getBoolean("is_proxy_bid"));
            map.put("bid_time", rs.getTimestamp("bid_time"));
            map.put("is_winning", rs.getBoolean("is_winning"));
            map.put("status", rs.getString("status"));
            map.put("created_date", rs.getTimestamp("created_date"));
            map.put("updated_date", rs.getTimestamp("updated_date"));
            return map;
        }, params.toArray());
    }

    @PostMapping("/bids")
    public List<Map<String, Object>> placeBid(@RequestBody PlaceBidRequest request) {
        String email = requireAuthenticatedUser();
        bidRateLimiter.check(email);
        User currentUser = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (!currentUser.getId().equals(request.getBidderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bidder ID does not match authenticated user");
        }
        if (request.getBidderName() == null || request.getBidderName().isBlank()) {
            request.setBidderName(currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName()
                : currentUser.getEmail());
        }
        return auctionBidService.placeBid(request);
    }

    private String requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName().toLowerCase().trim();
    }

    @GetMapping
    public List<Map<String, Object>> listAuctions() {
        String sql = """
            SELECT a.id,
                   a.title,
                   a.description,
                   a.start_date,
                   a.end_date,
                   a.status,
                   COALESCE(a.featured_artworks, '[]'::jsonb) AS featured_artworks,
                   a.quarter,
                   a.buy_now_enabled,
                   a.image_url,
                   (SELECT COUNT(*) FROM auction_bid b WHERE b.auction_id = a.id) AS total_bids,
                   (SELECT MAX(b.bid_amount) FROM auction_bid b WHERE b.auction_id = a.id) AS current_bid,
                   a.created_date
            FROM auction a
            ORDER BY a.start_date DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            String auctionId = rs.getString("id");
            map.put("id", auctionId);
            map.put("title", rs.getString("title"));
            map.put("description", rs.getString("description"));
            map.put("start_date", rs.getTimestamp("start_date"));
            map.put("end_date", rs.getTimestamp("end_date"));
            map.put("status", rs.getString("status"));
            
            String featuredArtworksJson = rs.getString("featured_artworks");
            if (featuredArtworksJson == null || featuredArtworksJson.isBlank()) {
                featuredArtworksJson = "[]";
            }
            try {
                List<String> featuredArtworks = JSON.readValue(featuredArtworksJson, new TypeReference<List<String>>() {});
                map.put("featured_artworks", featuredArtworks);
            } catch (Exception e) {
                map.put("featured_artworks", List.of());
            }
            
            map.put("quarter", rs.getString("quarter"));
            map.put("buy_now_enabled", rs.getBoolean("buy_now_enabled"));
            map.put("image_url", rs.getString("image_url"));
            map.put("total_bids", rs.getInt("total_bids"));
            BigDecimal currentBid = rs.getBigDecimal("current_bid");
            map.put("current_bid", currentBid);
            map.put("created_date", rs.getTimestamp("created_date"));
            return map;
        });
    }
}
