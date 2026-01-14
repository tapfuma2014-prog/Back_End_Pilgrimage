package com.pilgrimage.backend.dto;

import java.time.LocalDateTime;

public class NotificationDto {
    private final String id;
    private final String userEmail;
    private final String type;
    private final String title;
    private final String message;
    private final String link;
    private final boolean isRead;
    private final LocalDateTime createdDate;

    public NotificationDto(String id,
                           String userEmail,
                           String type,
                           String title,
                           String message,
                           String link,
                           boolean isRead,
                           LocalDateTime createdDate) {
        this.id = id;
        this.userEmail = userEmail;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.isRead = isRead;
        this.createdDate = createdDate;
    }

    public String getId() {
        return id;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public boolean isRead() {
        return isRead;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
