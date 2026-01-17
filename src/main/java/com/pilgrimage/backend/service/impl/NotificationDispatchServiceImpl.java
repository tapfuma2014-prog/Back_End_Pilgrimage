package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.NotificationDispatchService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationDispatchServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> sendEmail(Map<String, Object> payload) {
        String recipient = getString(payload, "to");
        String subject = getString(payload, "subject");
        String body = getString(payload, "body");
        if (body.isBlank()) {
            body = getString(payload, "message");
        }

        Map<String, Object> response = new HashMap<>();
        if (recipient.isBlank()) {
            response.put("status", "failed");
            response.put("message", "Recipient email is required");
            return response;
        }

        insertNotification(recipient, "email", subject, body);
        response.put("status", "sent");
        response.put("recipient", recipient);
        return response;
    }

    @Override
    public Map<String, Object> sendSms(Map<String, Object> payload) {
        String recipient = getString(payload, "to");
        String body = getString(payload, "body");
        if (body.isBlank()) {
            body = getString(payload, "message");
        }

        Map<String, Object> response = new HashMap<>();
        if (recipient.isBlank()) {
            response.put("status", "failed");
            response.put("message", "Recipient phone is required");
            return response;
        }

        insertNotification(recipient, "sms", "SMS Message", body);
        response.put("status", "queued");
        response.put("recipient", recipient);
        return response;
    }

    private void insertNotification(String recipient, String type, String title, String message) {
        String id = UUID.randomUUID().toString();
        String safeTitle = title.isBlank() ? "Notification" : title;
        String safeMessage = message.isBlank() ? "No message body provided." : message;

        jdbcTemplate.update(
            "INSERT INTO notifications (id, user_email, type, title, message, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            id,
            recipient,
            type,
            safeTitle,
            safeMessage,
            "system"
        );
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
