package com.pilgrimage.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GalleryStatusUpdateService {
    private static final Logger log = LoggerFactory.getLogger(GalleryStatusUpdateService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.gallery-status-update-url:}")
    private String updateUrl;

    @Value("${app.secret:}")
    private String appSecret;

    public GalleryStatusUpdateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public void triggerPaintingCompleted(String commissionId) {
        if (updateUrl == null || updateUrl.isBlank() || appSecret == null || appSecret.isBlank()) {
            log.debug("galleryStatusUpdate is not configured");
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "event", "galleryStatusUpdate",
                "commission_id", commissionId,
                "stage", "painting_completed"
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(updateUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-App-Secret", appSecret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        log.warn("galleryStatusUpdate returned HTTP {}", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    log.warn("galleryStatusUpdate failed: {}", error.getMessage());
                    return null;
                });
        } catch (Exception e) {
            log.warn("Unable to send galleryStatusUpdate: {}", e.getMessage());
        }
    }
}
