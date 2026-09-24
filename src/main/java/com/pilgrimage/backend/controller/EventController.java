package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
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
@RequestMapping("/events")
public class EventController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public EventController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                           UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listEvents(@RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(name = "type", required = false) String type) {
        String sql = """
            SELECT id,
                   title,
                   description,
                   date,
                   start_time,
                   end_time,
                   type,
                   location,
                   ticket_price,
                   is_free,
                   capacity,
                   tickets_sold,
                   image_url,
                   featured_artists,
                   media_gallery,
                   status,
                   created_date
            FROM event
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (type != null && !type.isBlank()) {
            conditions.add("type = ?");
            params.add(type);
        }
        if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
            // Non-admin callers must never see draft events.
            conditions.add("status IS DISTINCT FROM 'draft'");
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY date ASC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toEventMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toEventMap(rs), params.toArray());
    }

    private Map<String, Object> toEventMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("description", rs.getString("description"));
        map.put("date", rs.getString("date"));
        map.put("start_time", rs.getString("start_time"));
        map.put("end_time", rs.getString("end_time"));
        map.put("type", rs.getString("type"));
        map.put("location", rs.getString("location"));
        map.put("ticket_price", rs.getObject("ticket_price"));
        map.put("is_free", rs.getObject("is_free"));
        map.put("capacity", rs.getObject("capacity"));
        map.put("tickets_sold", rs.getObject("tickets_sold"));
        map.put("image_url", rs.getString("image_url"));
        map.put("featured_artists", parseJsonArray(rs.getString("featured_artists")));
        map.put("media_gallery", parseJsonArray(rs.getString("media_gallery")));
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
