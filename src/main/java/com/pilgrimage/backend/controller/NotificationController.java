package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.NotificationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final JdbcTemplate jdbcTemplate;

    public NotificationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<NotificationDto> listNotifications(@RequestParam(name = "userEmail", required = false) String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Collections.emptyList();
        }

        return jdbcTemplate.query(
            """
                SELECT id, user_email, type, title, message, link, is_read, created_date
                FROM notifications
                WHERE user_email = ?
                ORDER BY created_date DESC
                LIMIT 20
            """,
            (rs, rowNum) -> new NotificationDto(
                rs.getString("id"),
                rs.getString("user_email"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("link"),
                rs.getBoolean("is_read"),
                rs.getTimestamp("created_date").toLocalDateTime()
            ),
            userEmail
        );
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") String id) {
        jdbcTemplate.update(
            "UPDATE notifications SET is_read = true, updated_date = NOW() WHERE id = ?",
            id
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestParam(name = "userEmail", required = false) String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.ok().build();
        }
        jdbcTemplate.update(
            "UPDATE notifications SET is_read = true, updated_date = NOW() WHERE user_email = ?",
            userEmail
        );
        return ResponseEntity.ok().build();
    }
}
