package com.pilgrimage.backend.service;

public interface SmsService {
    /** Send a transactional SMS directly to a single phone number (E.164). */
    void sendSms(String phoneNumber, String message);

    /** Broadcast a message to every subscriber of the configured SNS topic. */
    void broadcastToTopic(String message);

    /** True when AWS credentials are present and the client can be built. */
    boolean isConfigured();
}
