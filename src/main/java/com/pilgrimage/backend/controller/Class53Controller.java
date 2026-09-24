package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/class53")
public class Class53Controller {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public Class53Controller(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                             UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @GetMapping("/events")
    public List<Map<String, Object>> listEvents(@RequestParam(name = "status", required = false) String status,
                                                 @RequestParam(name = "category", required = false) String category) {
        String sql = """
            SELECT id,
                   title,
                   slug,
                   description,
                   short_description,
                   category,
                   event_type,
                   organiser_name,
                   organiser_email,
                   image_url,
                   gallery_urls,
                   date,
                   start_time,
                   end_time,
                   duration_minutes,
                   location,
                   price,
                   early_bird_price,
                   early_bird_deadline,
                   capacity,
                   tickets_sold,
                   expected_outcomes,
                   whats_included,
                   requirements,
                   tags,
                   commission_rate,
                   is_featured,
                   status,
                   created_date
            FROM class53_event
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (category != null && !category.isBlank()) {
            conditions.add("category = ?");
            params.add(category);
        }

        if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
            // Non-admin callers must never see draft events.
            conditions.add("status IS DISTINCT FROM 'draft'");
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY date ASC";

        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        List<Map<String, Object>> events = params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toEventMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toEventMap(rs), params.toArray());
        if (!isAdmin) {
            // Organiser email is PII - strip it for non-admin callers.
            events.forEach(event -> event.remove("organiser_email"));
        }
        return events;
    }

    @GetMapping("/affiliates")
    public List<Map<String, Object>> listAffiliates(@RequestParam(name = "referralCode", required = false) String referralCode) {
        String caller = EntityAuthorizationHelper.currentUserEmail();
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);

        // Referral-code lookups are used during checkout attribution; return only
        // non-sensitive fields so codes can be validated without exposing PII.
        if (!isAdmin && referralCode != null && !referralCode.isBlank()) {
            return jdbcTemplate.query(
                "SELECT id, name, referral_code, status FROM class53_affiliates WHERE referral_code = ?",
                (rs, rowNum) -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", rs.getString("id"));
                    map.put("name", rs.getString("name"));
                    map.put("referral_code", rs.getString("referral_code"));
                    map.put("status", rs.getString("status"));
                    return map;
                },
                referralCode
            );
        }

        String sql = """
            SELECT id,
                   name,
                   email,
                   referral_code,
                   commission_rate,
                   total_referrals,
                   total_earned,
                   status,
                   created_date
            FROM class53_affiliates
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (referralCode != null && !referralCode.isBlank()) {
            conditions.add("referral_code = ?");
            params.add(referralCode);
        }
        if (!isAdmin) {
            // Non-admin callers may only see their own affiliate record.
            conditions.add("(LOWER(email) = LOWER(?) OR LOWER(created_by) = LOWER(?))");
            params.add(caller);
            params.add(caller);
        }
        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toAffiliateMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toAffiliateMap(rs), params.toArray());
    }

    private Map<String, Object> toEventMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("title", rs.getString("title"));
        map.put("slug", rs.getString("slug"));
        map.put("description", rs.getString("description"));
        map.put("short_description", rs.getString("short_description"));
        map.put("category", rs.getString("category"));
        map.put("event_type", rs.getString("event_type"));
        map.put("organiser_name", rs.getString("organiser_name"));
        map.put("organiser_email", rs.getString("organiser_email"));
        map.put("image_url", rs.getString("image_url"));
        map.put("gallery_urls", parseJsonArray(rs.getString("gallery_urls")));
        map.put("date", rs.getString("date"));
        map.put("start_time", rs.getString("start_time"));
        map.put("end_time", rs.getString("end_time"));
        map.put("duration_minutes", rs.getObject("duration_minutes"));
        map.put("location", rs.getString("location"));
        map.put("price", rs.getObject("price"));
        map.put("early_bird_price", rs.getObject("early_bird_price"));
        map.put("early_bird_deadline", rs.getString("early_bird_deadline"));
        map.put("capacity", rs.getObject("capacity"));
        map.put("tickets_sold", rs.getObject("tickets_sold"));
        map.put("expected_outcomes", parseJsonArray(rs.getString("expected_outcomes")));
        map.put("whats_included", rs.getString("whats_included"));
        map.put("requirements", rs.getString("requirements"));
        map.put("tags", parseJsonArray(rs.getString("tags")));
        map.put("commission_rate", rs.getObject("commission_rate"));
        map.put("is_featured", rs.getObject("is_featured"));
        map.put("status", rs.getString("status"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }

    private Map<String, Object> toAffiliateMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("name", rs.getString("name"));
        map.put("email", rs.getString("email"));
        map.put("referral_code", rs.getString("referral_code"));
        map.put("commission_rate", rs.getObject("commission_rate"));
        map.put("referral_clicks", null);
        map.put("referral_conversions", rs.getObject("total_referrals"));
        map.put("total_earnings", rs.getObject("total_earned"));
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
