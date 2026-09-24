package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String configuredFromAddress;
    private final String mailUsername;
    private final String mailHost;

    public EmailServiceImpl(
        JavaMailSender mailSender,
        @Value("${spring.mail.from:}") String configuredFromAddress,
        @Value("${spring.mail.username:}") String mailUsername,
        @Value("${spring.mail.host:}") String mailHost
    ) {
        this.mailSender = mailSender;
        this.configuredFromAddress = configuredFromAddress;
        this.mailUsername = mailUsername;
        this.mailHost = mailHost;
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new RuntimeException("Recipient email is required");
        }
        if (resetLink == null || resetLink.isBlank()) {
            throw new RuntimeException("Reset link is required");
        }
        if (!isConfigured()) {
            log.error("Password reset email not sent because SMTP is not configured");
            throw new RuntimeException("Unable to send password reset email");
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(configuredFromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject("Password Reset Request – 53 Cox Road Pilgrimage");
            helper.setText(buildPasswordResetBody(resetLink), true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", maskEmail(recipientEmail));
        } catch (MessagingException | MailException e) {
            log.error("Failed to send password reset email to {}: {}", maskEmail(recipientEmail), e.getMessage());
            throw new RuntimeException("Unable to send password reset email");
        }
    }

    @Override
    public void sendPlainEmail(String recipientEmail, String subject, String body) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new RuntimeException("Recipient email is required");
        }
        if (!isConfigured()) {
            log.error("Email not sent because SMTP is not configured");
            throw new RuntimeException("Unable to send email");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(configuredFromAddress);
            helper.setTo(recipientEmail);
            helper.setSubject(subject == null || subject.isBlank() ? "Notification" : subject);
            // Only treat the body as HTML when it actually looks like an HTML
            // fragment (starts with a tag). A plain-text body that merely
            // contains '<' — e.g. user input like "Bob <3" or an injected
            // "<img onerror=...>" — must be sent as text so markup is not
            // rendered by the recipient's mail client.
            boolean isHtml = body != null && body.stripLeading().startsWith("<");
            helper.setText(body == null || body.isBlank() ? "No message body provided." : body, isHtml);
            mailSender.send(message);
            log.info("Email sent to {}", maskEmail(recipientEmail));
        } catch (MessagingException | MailException e) {
            log.error("Failed to send email to {}: {}", maskEmail(recipientEmail), e.getMessage());
            throw new RuntimeException("Unable to send email");
        }
    }

    /** Masks an email address for logging: keeps first char + domain. */
    private String maskEmail(String email) {
        if (email == null) {
            return "unknown";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private boolean isConfigured() {
        return mailHost != null && !mailHost.isBlank()
            && mailUsername != null && !mailUsername.isBlank()
            && configuredFromAddress != null && !configuredFromAddress.isBlank();
    }

    private String buildPasswordResetBody(String resetLink) {
        return """
            <div style="font-family: Arial, Helvetica, sans-serif; color: #2D3436; line-height: 1.6; max-width: 600px;">
              <p>Hello,</p>
              <p>We received a request to reset the password for your <strong>53 Cox Road Pilgrimage</strong> account.</p>
              <p>Please click the button below to choose a new password:</p>
              <p style="margin: 28px 0;">
                <a href="%s" style="background-color: #9CAF88; color: #ffffff; text-decoration: none; padding: 12px 24px; border-radius: 6px; display: inline-block; font-weight: 600;">
                  Reset Password
                </a>
              </p>
              <p>If the button does not work, copy and paste this link into your browser:</p>
              <p style="word-break: break-all;"><a href="%s">%s</a></p>
              <p><strong>Important:</strong> This reset link is time-limited and will expire in 1 hour for your security.</p>
              <p>If you did not request a password reset, you can safely ignore this email. Your password will remain unchanged.</p>
              <p style="margin-top: 32px; color: #5A6C5E; font-size: 14px;">
                Kind regards,<br>
                The 53 Cox Road Pilgrimage Team
              </p>
            </div>
            """.formatted(resetLink, resetLink, resetLink);
    }
}
