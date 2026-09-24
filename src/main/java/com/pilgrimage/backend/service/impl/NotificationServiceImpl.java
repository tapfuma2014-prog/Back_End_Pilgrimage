package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.dto.CreateNotificationRequest;
import com.pilgrimage.backend.dto.NotificationDto;
import com.pilgrimage.backend.service.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final JdbcTemplate jdbcTemplate;

    public NotificationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public NotificationDto create(CreateNotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Notification request is required");
        }

        String userEmail = normalize(request.getUserEmail());
        if (userEmail.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }

        String title = normalize(request.getTitle());
        String message = normalize(request.getMessage());
        if (title.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("Title and message are required");
        }

        String id = UUID.randomUUID().toString();
        String type = normalize(request.getType());
        if (type.isBlank()) {
            type = "general";
        }
        String link = normalize(request.getLink());
        String createdBy = normalize(request.getCreatedBy());
        if (createdBy.isBlank()) {
            createdBy = "system";
        }

        jdbcTemplate.update(
            """
                INSERT INTO notifications (id, user_email, type, title, message, link, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            userEmail,
            type,
            title,
            message,
            link.isBlank() ? null : link,
            createdBy
        );

        return findById(id);
    }

    @Override
    public List<NotificationDto> listByUserEmail(String userEmail) {
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
            (rs, rowNum) -> mapRow(rs),
            userEmail
        );
    }

    private NotificationDto findById(String id) {
        List<NotificationDto> results = jdbcTemplate.query(
            """
                SELECT id, user_email, type, title, message, link, is_read, created_date
                FROM notifications
                WHERE id = ?
            """,
            (rs, rowNum) -> mapRow(rs),
            id
        );
        if (results.isEmpty()) {
            throw new IllegalStateException("Notification was not persisted");
        }
        return results.get(0);
    }

    private NotificationDto mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NotificationDto(
            rs.getString("id"),
            rs.getString("user_email"),
            rs.getString("type"),
            rs.getString("title"),
            rs.getString("message"),
            rs.getString("link"),
            rs.getBoolean("is_read"),
            rs.getTimestamp("created_date").toLocalDateTime()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
