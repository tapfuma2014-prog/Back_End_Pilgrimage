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
import java.util.Map;

@Service
public class XperiencesService {
    private static final Logger LOGGER = LoggerFactory.getLogger(XperiencesService.class);
    private static final URI GALLERY_STATUS_UPDATE_URI = URI.create(
        "https://pink-fifty-three-quest.base44.app/functions/galleryStatusUpdate"
    );
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper;
    private final String appSecret;

    public XperiencesService(
        ObjectMapper objectMapper,
        @Value("${xperiences.app-secret:}") String appSecret
    ) {
        this.objectMapper = objectMapper;
        this.appSecret = appSecret;
    }

    public void notifyPaintingCompleted(String commissionId) {
        if (appSecret == null || appSecret.isBlank()) {
            LOGGER.warn("Xperiences integration is not configured; skipping commission {}", commissionId);
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "commission_id", commissionId,
                "stage", "painting_completed"
            ));
            HttpRequest request = HttpRequest.newBuilder(GALLERY_STATUS_UPDATE_URI)
                .header("Content-Type", "application/json")
                .header("X-App-Secret", appSecret)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                    "Xperiences gallery status update returned HTTP " + response.statusCode()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to notify Xperiences of painting completion", exception);
        }
    }
}
