package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.dto.CreateNotificationRequest;
import com.pilgrimage.backend.service.EmailService;
import com.pilgrimage.backend.service.NotificationDispatchService;
import com.pilgrimage.backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationDispatchServiceImpl implements NotificationDispatchService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchServiceImpl.class);

    private final NotificationService notificationService;
    private final EmailService emailService;

    public NotificationDispatchServiceImpl(
        NotificationService notificationService,
        EmailService emailService
    ) {
        this.notificationService = notificationService;
        this.emailService = emailService;
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

        try {
            emailService.sendPlainEmail(recipient, subject, body);
            response.put("status", "sent");
            response.put("delivery", "smtp");
        } catch (RuntimeException ex) {
            log.warn("SMTP email failed for {}: {}", recipient, ex.getMessage());
            response.put("status", "failed");
            response.put("message", ex.getMessage());
            return response;
        }

        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserEmail(recipient);
        request.setType("email");
        request.setTitle(subject.isBlank() ? "Notification" : subject);
        request.setMessage(body.isBlank() ? "No message body provided." : body);
        request.setCreatedBy("system");
        notificationService.create(request);

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

        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setUserEmail(recipient);
        request.setType("sms");
        request.setTitle("SMS Message");
        request.setMessage(body.isBlank() ? "No message body provided." : body);
        request.setCreatedBy("system");
        notificationService.create(request);

        response.put("status", "queued");
        response.put("recipient", recipient);
        return response;
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
