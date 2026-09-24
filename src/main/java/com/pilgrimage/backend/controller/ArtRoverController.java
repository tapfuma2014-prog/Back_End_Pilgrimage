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
@RequestMapping("/art-rover")
public class ArtRoverController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ArtRoverController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tours")
    public List<Map<String, Object>> listTours(@RequestParam(name = "status", required = false) String status,
                                               @RequestParam(name = "eventType", required = false) String eventType) {
        String sql = """
            SELECT id,
                   title,
                   description,
                   state,
                   location_name,
                   address,
                   latitude,
                   longitude,
                   date,
                   start_time,
                   end_time,
                   event_type,
                   slots_available,
                   slots_booked,
                   is_live,
                   featured_artworks,
                   status,
                   created_date
            FROM art_rover_tour
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (eventType != null && !eventType.isBlank()) {
            conditions.add("event_type = ?");
            params.add(eventType);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY date ASC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toTourMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toTourMap(rs), params.toArray());
    }

    private Map<String, Object> toTourMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("description", rs.getString("description"));
        map.put("state", rs.getString("state"));
        map.put("location_name", rs.getString("location_name"));
        map.put("address", rs.getString("address"));
        map.put("latitude", rs.getObject("latitude"));
        map.put("longitude", rs.getObject("longitude"));
        map.put("date", rs.getString("date"));
        map.put("start_time", rs.getString("start_time"));
        map.put("end_time", rs.getString("end_time"));
        map.put("event_type", rs.getString("event_type"));
        map.put("slots_available", rs.getObject("slots_available"));
        map.put("slots_booked", rs.getObject("slots_booked"));
        map.put("is_live", rs.getObject("is_live"));
        map.put("featured_artworks", parseJsonArray(rs.getString("featured_artworks")));
        map.put("status", rs.getString("status"));
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
