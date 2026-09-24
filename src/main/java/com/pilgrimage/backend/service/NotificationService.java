package com.pilgrimage.backend.service;

import com.pilgrimage.backend.dto.CreateNotificationRequest;
import com.pilgrimage.backend.dto.NotificationDto;

import java.util.List;

public interface NotificationService {
    NotificationDto create(CreateNotificationRequest request);

    List<NotificationDto> listByUserEmail(String userEmail);
}
