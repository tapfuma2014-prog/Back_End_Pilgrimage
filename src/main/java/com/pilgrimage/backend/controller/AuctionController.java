package com.pilgrimage.backend.controller;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

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
                   (SELECT COUNT(*) FROM auction_bid b WHERE b.auction_id = a.id) AS total_bids,
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
            
            Object featuredArtworks = rs.getObject("featured_artworks");
            
            if (featuredArtworks instanceof List<?> list && list.isEmpty()) {
                String artworkIdsSql = """
                    SELECT DISTINCT 
                        CASE 
                            WHEN ab.artwork_id LIKE 'sample-%' THEN SUBSTRING(ab.artwork_id FROM 8)
                            ELSE ab.artwork_id
                        END as artwork_id
                    FROM auction_bid ab
                    WHERE ab.auction_id = ?
                    AND ab.artwork_id IS NOT NULL
                """;
                List<String> artworkIds = jdbcTemplate.queryForList(artworkIdsSql, String.class, auctionId);
                map.put("featured_artworks", artworkIds);
            } else {
                map.put("featured_artworks", featuredArtworks);
            }
            
            map.put("quarter", rs.getString("quarter"));
            map.put("buy_now_enabled", rs.getBoolean("buy_now_enabled"));
            map.put("total_bids", rs.getInt("total_bids"));
            map.put("created_date", rs.getTimestamp("created_date"));
            return map;
        });
    }
}
