package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/artists")
public class ArtistController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ArtistController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> listArtists(@RequestParam(name = "isFeatured", required = false) Boolean isFeatured,
                                                 @RequestParam(name = "isAvailable", required = false) Boolean isAvailable) {
        String sql = """
            SELECT id,
                   name,
                   bio,
                   statement,
                   location,
                   website,
                   instagram,
                   profile_image,
                   specialties,
                   follower_count,
                   is_available,
                   accepts_messages,
                   accepts_donations,
                   is_featured,
                   created_by,
                   created_date
            FROM artist
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (isFeatured != null) {
            conditions.add("is_featured = ?");
            params.add(isFeatured);
        }
        if (isAvailable != null) {
            conditions.add("is_available = ?");
            params.add(isAvailable);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY is_featured DESC, created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toArtistMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toArtistMap(rs), params.toArray());
    }

    private Map<String, Object> toArtistMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("name", rs.getString("name"));
        map.put("bio", rs.getString("bio"));
        map.put("statement", rs.getString("statement"));
        map.put("location", rs.getString("location"));
        map.put("website", rs.getString("website"));
        map.put("instagram", rs.getString("instagram"));
        map.put("profile_image", rs.getString("profile_image"));
        map.put("specialties", parseJsonArray(rs.getString("specialties")));
        map.put("follower_count", rs.getObject("follower_count"));
        map.put("is_available", rs.getObject("is_available"));
        map.put("accepts_messages", rs.getObject("accepts_messages"));
        map.put("accepts_donations", rs.getObject("accepts_donations"));
        map.put("is_featured", rs.getObject("is_featured"));
        map.put("created_by", rs.getString("created_by"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }

    @GetMapping("/with-artworks")
    public List<Map<String, Object>> listArtistsWithArtworks(@RequestParam(name = "isFeatured", required = false) Boolean isFeatured,
                                                             @RequestParam(name = "isAvailable", required = false) Boolean isAvailable) {
        List<Map<String, Object>> artists = listArtists(isFeatured, isAvailable);

        // Only approved, non-sample artworks are exposed in the public listing.
        List<Map<String, Object>> artworks = jdbcTemplate.query("""
            SELECT id,
                   title,
                   artist,
                   description,
                   price,
                   currency,
                   "isInStock" AS is_in_stock,
                   image_url,
                   art_style,
                   dimensions,
                   year_created,
                   created_by,
                   created_date
            FROM artwork
            WHERE status = 'approved'
              AND COALESCE(is_sample, false) = false
            ORDER BY created_date DESC
        """, (rs, rowNum) -> toArtworkSummaryMap(rs));

        // artwork.artist stores the artist's display name; artwork.created_by stores
        // the uploader's email which matches artist.created_by for profile owners.
        Map<String, List<Map<String, Object>>> byArtistName = new HashMap<>();
        Map<String, List<Map<String, Object>>> byCreatorEmail = new HashMap<>();
        for (Map<String, Object> artwork : artworks) {
            String artistName = normalize(artwork.get("artist"));
            if (artistName != null) {
                byArtistName.computeIfAbsent(artistName, k -> new ArrayList<>()).add(artwork);
            }
            String creator = normalize(artwork.get("created_by"));
            if (creator != null) {
                byCreatorEmail.computeIfAbsent(creator, k -> new ArrayList<>()).add(artwork);
            }
        }

        for (Map<String, Object> artist : artists) {
            Set<Map<String, Object>> works = new LinkedHashSet<>();
            List<Map<String, Object>> byName = byArtistName.get(normalize(artist.get("name")));
            if (byName != null) {
                works.addAll(byName);
            }
            List<Map<String, Object>> byCreator = byCreatorEmail.get(normalize(artist.get("created_by")));
            if (byCreator != null) {
                works.addAll(byCreator);
            }
            artist.put("artworks", new ArrayList<>(works));
            artist.put("artwork_count", works.size());
        }

        return artists;
    }

    private Map<String, Object> toArtworkSummaryMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("artist", rs.getString("artist"));
        map.put("description", rs.getString("description"));
        map.put("price", rs.getBigDecimal("price"));
        map.put("currency", rs.getString("currency"));
        map.put("is_in_stock", rs.getObject("is_in_stock"));
        map.put("image_url", rs.getString("image_url"));
        map.put("art_style", rs.getString("art_style"));
        map.put("dimensions", rs.getString("dimensions"));
        map.put("year_created", rs.getObject("year_created"));
        map.put("created_by", rs.getString("created_by"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
