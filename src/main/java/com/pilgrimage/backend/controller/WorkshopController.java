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
@RequestMapping("/workshops")
public class WorkshopController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkshopController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Map<String, Object>> listWorkshops(@RequestParam(name = "status", required = false) String status) {
        String sql = """
            SELECT id,
                   title,
                   slug,
                   category,
                   thumbnail_url,
                   gallery_urls,
                   short_description,
                   what_youll_do,
                   whats_included,
                   who_its_for,
                   what_to_bring,
                   instructor_name,
                   instructor_bio,
                   duration_minutes,
                   price,
                   max_participants,
                   location,
                   tags,
                   status,
                   created_date
            FROM workshop
        """;

        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql += " WHERE status = ?";
            params.add(status);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toWorkshopMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toWorkshopMap(rs), params.toArray());
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> listSessions(@RequestParam(name = "workshopId", required = false) String workshopId,
                                                   @RequestParam(name = "status", required = false) String status) {
        String sql = """
            SELECT id,
                   workshop_id,
                   date,
                   start_time,
                   end_time,
                   spots_available,
                   spots_total,
                   status,
                   created_date
            FROM workshop_session
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (workshopId != null && !workshopId.isBlank()) {
            conditions.add("workshop_id = ?");
            params.add(workshopId);
        }
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY date ASC, start_time ASC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toSessionMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toSessionMap(rs), params.toArray());
    }

    private Map<String, Object> toWorkshopMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("slug", rs.getString("slug"));
        map.put("category", rs.getString("category"));
        map.put("thumbnail_url", rs.getString("thumbnail_url"));
        map.put("gallery_urls", parseJsonArray(rs.getString("gallery_urls")));
        map.put("short_description", rs.getString("short_description"));
        map.put("what_youll_do", rs.getString("what_youll_do"));
        map.put("whats_included", rs.getString("whats_included"));
        map.put("who_its_for", rs.getString("who_its_for"));
        map.put("what_to_bring", rs.getString("what_to_bring"));
        map.put("instructor_name", rs.getString("instructor_name"));
        map.put("instructor_bio", rs.getString("instructor_bio"));
        map.put("duration_minutes", rs.getObject("duration_minutes"));
        map.put("price", rs.getObject("price"));
        map.put("max_participants", rs.getObject("max_participants"));
        map.put("location", rs.getString("location"));
        map.put("tags", parseJsonArray(rs.getString("tags")));
        map.put("status", rs.getString("status"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }

    private Map<String, Object> toSessionMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("workshop_id", rs.getString("workshop_id"));
        map.put("date", rs.getString("date"));
        map.put("start_time", rs.getString("start_time"));
        map.put("end_time", rs.getString("end_time"));
        map.put("spots_available", rs.getObject("spots_available"));
        map.put("spots_total", rs.getObject("spots_total"));
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
