package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.AuctionSearchResult;
import com.pilgrimage.backend.dto.ArtworkSearchResult;
import com.pilgrimage.backend.dto.ExhibitionSearchResult;
import com.pilgrimage.backend.dto.SearchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final JdbcTemplate jdbcTemplate;

    public SearchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public SearchResponse search(@RequestParam(name = "query", required = false) String query) {
        if (query == null || query.trim().length() < 2) {
            return SearchResponse.empty();
        }

        String likeQuery = "%" + query.trim().toLowerCase() + "%";

        List<ArtworkSearchResult> artworks = jdbcTemplate.query(
            """
                SELECT id, title, artist, image_url, art_style
                FROM artworks
                WHERE LOWER(title) LIKE ?
                   OR LOWER(artist) LIKE ?
                   OR LOWER(art_style) LIKE ?
                   OR LOWER(COALESCE(description, '')) LIKE ?
                ORDER BY created_date DESC
                LIMIT 4
            """,
            (rs, rowNum) -> new ArtworkSearchResult(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("image_url"),
                rs.getString("art_style")
            ),
            likeQuery, likeQuery, likeQuery, likeQuery
        );

        List<ExhibitionSearchResult> exhibitions = jdbcTemplate.query(
            """
                SELECT id, title, status, image_url
                FROM exhibitions
                WHERE LOWER(title) LIKE ?
                   OR LOWER(COALESCE(array_to_string(featured_artists, ','), '')) LIKE ?
                   OR LOWER(COALESCE(description, '')) LIKE ?
                ORDER BY created_date DESC
                LIMIT 3
            """,
            (rs, rowNum) -> new ExhibitionSearchResult(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("image_url")
            ),
            likeQuery, likeQuery, likeQuery
        );

        List<AuctionSearchResult> auctions = jdbcTemplate.query(
            """
                SELECT id, title, status
                FROM auctions
                WHERE LOWER(title) LIKE ?
                   OR LOWER(status) LIKE ?
                   OR LOWER(COALESCE(description, '')) LIKE ?
                ORDER BY created_date DESC
                LIMIT 3
            """,
            (rs, rowNum) -> new AuctionSearchResult(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("status")
            ),
            likeQuery, likeQuery, likeQuery
        );

        return new SearchResponse(artworks, exhibitions, auctions);
    }
}
