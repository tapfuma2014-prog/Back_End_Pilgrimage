package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@RequestMapping("/merchandise")
public class MerchandiseController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MerchandiseController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> listMerchandise(@RequestParam(name = "category", required = false) String category,
                                                      @RequestParam(name = "featured", required = false) Boolean featured) {
        String sql = """
            SELECT id,
                   name,
                   description,
                   category,
                   price,
                   image_url,
                   images,
                   sizes,
                   in_stock,
                   stock_quantity,
                   featured,
                   created_date
            FROM merchandise
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            conditions.add("category = ?");
            params.add(category);
        }
        if (featured != null) {
            conditions.add("featured = ?");
            params.add(featured);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY featured DESC, created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toMerchandiseMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toMerchandiseMap(rs), params.toArray());
    }

    private Map<String, Object> toMerchandiseMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("name", rs.getString("name"));
        map.put("description", rs.getString("description"));
        map.put("category", rs.getString("category"));
        map.put("price", rs.getObject("price"));
        map.put("image_url", rs.getString("image_url"));
        map.put("images", parseJsonArray(rs.getString("images")));
        map.put("sizes", parseJsonArray(rs.getString("sizes")));
        map.put("in_stock", rs.getObject("in_stock"));
        map.put("stock_quantity", rs.getObject("stock_quantity"));
        map.put("featured", rs.getObject("featured"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
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
