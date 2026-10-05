package com.pilgrimage.backend.service;

public interface EmailService {
    void sendPasswordResetEmail(String recipientEmail, String resetLink);

    void sendVerificationEmail(String recipientEmail, String verificationLink);

    void sendPlainEmail(String recipientEmail, String subject, String body);
}
