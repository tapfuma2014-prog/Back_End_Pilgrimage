package com.pilgrimage.backend.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal in-memory sliding-window rate limiter for sensitive endpoints.
 *
 * Suitable for a single-instance deployment. If the service is ever scaled
 * horizontally, replace with a distributed limiter (e.g. Bucket4j + Redis)
 * or enforce limits at the API gateway / reverse proxy.
 */
public final class SimpleRateLimiter {

    private final Map<String, List<Long>> timestamps = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;
    private final String errorMessage;

    public SimpleRateLimiter(int maxRequests, long windowMillis, String errorMessage) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
        this.errorMessage = errorMessage;
    }

    /**
     * Records an attempt for the given key and throws HTTP 429 when the
     * caller has exceeded the configured number of requests in the window.
     */
    public void check(String key) {
        String normalized = key == null || key.isBlank() ? "anonymous" : key.toLowerCase().trim();
        long now = System.currentTimeMillis();
        long windowStart = now - windowMillis;
        List<Long> hits = timestamps.computeIfAbsent(normalized, k -> new ArrayList<>());
        synchronized (hits) {
            hits.removeIf(t -> t < windowStart);
            if (hits.size() >= maxRequests) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, errorMessage);
            }
            hits.add(now);
        }
    }
}
