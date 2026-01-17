package com.pilgrimage.backend.service;

import java.util.Map;

public interface NotificationDispatchService {
    Map<String, Object> sendEmail(Map<String, Object> payload);
    Map<String, Object> sendSms(Map<String, Object> payload);
}
