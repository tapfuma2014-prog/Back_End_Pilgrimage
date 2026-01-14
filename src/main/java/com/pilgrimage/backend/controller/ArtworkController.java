package com.pilgrimage.backend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/artworks")
public class ArtworkController {

    private final JdbcTemplate jdbcTemplate;

    public ArtworkController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> listArtworks(@RequestParam(name = "inStock", required = false) Boolean inStock) {
        String sql = """
            SELECT id,
                   title,
                   artist,
                   description,
                   price,
                   currency,
                   is_in_stock,
                   image_url,
                   garden_origin,
                   art_style,
                   created_date
            FROM artworks
        """;

        List<Object> params = new ArrayList<>();
        if (inStock != null) {
            sql += " WHERE is_in_stock = ?";
            params.add(inStock);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toArtworkMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toArtworkMap(rs), params.toArray());
    }

    private Map<String, Object> toArtworkMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("artist", rs.getString("artist"));
        map.put("description", rs.getString("description"));
        map.put("price", rs.getBigDecimal("price"));
        map.put("currency", rs.getString("currency"));
        map.put("is_in_stock", rs.getObject("is_in_stock"));
        map.put("image_url", rs.getString("image_url"));
        map.put("garden_origin", rs.getString("garden_origin"));
        map.put("art_style", rs.getString("art_style"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }
}
