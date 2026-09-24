package com.pilgrimage.backend.service;

public interface EmailService {
    void sendPasswordResetEmail(String recipientEmail, String resetLink);

    void sendPlainEmail(String recipientEmail, String subject, String body);
}
