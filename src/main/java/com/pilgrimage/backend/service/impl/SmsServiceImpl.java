package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.HashMap;
import java.util.Map;

@Service
public class SmsServiceImpl implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsServiceImpl.class);
    private static final int MAX_SMS_LENGTH = 1600; // SNS hard limit per publish

    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String senderId;
    private final String smsType;
    private final String topicArn;

    private volatile SnsClient snsClient;

    public SmsServiceImpl(@Value("${aws.sns.region:us-east-1}") String region,
                          @Value("${aws.sns.access-key:}") String accessKey,
                          @Value("${aws.sns.secret-key:}") String secretKey,
                          @Value("${aws.sns.sender-id:53CoxRoad}") String senderId,
                          @Value("${aws.sns.sms-type:Transactional}") String smsType,
                          @Value("${aws.sns.topic-arn:}") String topicArn) {
        this.region = region;
        this.accessKey = accessKey == null ? "" : accessKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.senderId = senderId;
        this.smsType = smsType;
        this.topicArn = topicArn == null ? "" : topicArn.trim();
    }

    @Override
    public boolean isConfigured() {
        return !accessKey.isBlank() && !secretKey.isBlank();
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        String normalized = normalizePhone(phoneNumber);
        if (normalized == null) {
            throw new RuntimeException("Invalid phone number format");
        }
        publish(PublishRequest.builder()
                .phoneNumber(normalized)
                .message(truncate(message))
                .messageAttributes(smsAttributes())
                .build(), normalized);
    }

    @Override
    public void broadcastToTopic(String message) {
        if (topicArn.isBlank()) {
            throw new RuntimeException("SNS topic is not configured");
        }
        publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(truncate(message))
                .messageAttributes(smsAttributes())
                .build(), topicArn);
    }

    private void publish(PublishRequest request, String target) {
        if (!isConfigured()) {
            log.error("SMS not sent to {} because AWS SNS is not configured", maskTarget(target));
            throw new RuntimeException("Unable to send SMS");
        }
        try {
            client().publish(request);
            log.info("SMS sent to {}", maskTarget(target));
        } catch (SnsException e) {
            log.error("Failed to send SMS to {}: {}", maskTarget(target), e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorMessage() : e.getMessage());
            throw new RuntimeException("Unable to send SMS");
        }
    }

    private Map<String, MessageAttributeValue> smsAttributes() {
        Map<String, MessageAttributeValue> attrs = new HashMap<>();
        attrs.put("AWS.SNS.SMS.SMSType", MessageAttributeValue.builder()
                .dataType("String").stringValue(smsType).build());
        if (senderId != null && !senderId.isBlank()) {
            attrs.put("AWS.SNS.SMS.SenderID", MessageAttributeValue.builder()
                    .dataType("String").stringValue(senderId).build());
        }
        return attrs;
    }

    private SnsClient client() {
        if (snsClient == null) {
            synchronized (this) {
                if (snsClient == null) {
                    snsClient = SnsClient.builder()
                            .region(Region.of(region))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKey, secretKey)))
                            .build();
                }
            }
        }
        return snsClient;
    }

    /**
     * Normalizes to E.164: keeps digits, handles leading +, 00 prefix, and
     * Australian local numbers (04XXXXXXXX -> +614XXXXXXXX). Returns null when
     * the result is not a plausible international number.
     */
    private String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("[\\s\\-()]", "");
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }
        if (!value.startsWith("+")) {
            String digits = value.replaceAll("[^0-9]", "");
            if (digits.matches("0\\d{9}")) {           // AU local mobile/landline
                value = "+61" + digits.substring(1);
            } else if (digits.length() >= 8 && digits.length() <= 15 && value.matches("^\\d+$")) {
                value = "+" + digits;                  // already international digits
            } else {
                return null;
            }
        }
        return value.matches("^\\+[1-9]\\d{7,14}$") ? value : null;
    }

    private String truncate(String message) {
        if (message == null) {
            throw new RuntimeException("SMS message is required");
        }
        return message.length() > MAX_SMS_LENGTH ? message.substring(0, MAX_SMS_LENGTH) : message;
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 6) {
            return "***";
        }
        return target.substring(0, 4) + "***" + target.substring(target.length() - 2);
    }
}
