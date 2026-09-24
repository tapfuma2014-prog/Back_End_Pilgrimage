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
@RequestMapping("/exhibitions")
public class ExhibitionController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ExhibitionController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> listExhibitions(@RequestParam(name = "status", required = false) String status) {
        String sql = """
            SELECT id,
                   title,
                   description,
                   start_date,
                   end_date,
                   featured_artists,
                   image_url,
                   status,
                   location,
                   created_date
            FROM exhibition
        """;

        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql += " WHERE status = ?";
            params.add(status);
        }
        sql += " ORDER BY start_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toExhibitionMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toExhibitionMap(rs), params.toArray());
    }

    private Map<String, Object> toExhibitionMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("description", rs.getString("description"));
        map.put("start_date", rs.getString("start_date"));
        map.put("end_date", rs.getString("end_date"));
        map.put("featured_artists", parseJsonArray(rs.getString("featured_artists")));
        map.put("image_url", rs.getString("image_url"));
        map.put("status", rs.getString("status"));
        map.put("location", rs.getString("location"));
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
