package com.pilgrimage.backend.util;

import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Notification entity API field names (frontend) to notifications table columns (database).
 */
public final class NotificationEntityMapper {

    private NotificationEntityMapper() {
    }

    public static boolean isNotificationEntity(String entity) {
        return "Notification".equals(entity);
    }

    public static boolean isNotificationPreferenceEntity(String entity) {
        return "NotificationPreference".equals(entity);
    }

    public static String mapFilterColumn(String entity, String column) {
        if (column == null) {
            return column;
        }
        if (isNotificationEntity(entity) || isNotificationPreferenceEntity(entity)) {
            if ("user_id".equals(column)) {
                return "user_email";
            }
            if (isNotificationEntity(entity) && "related_id".equals(column)) {
                return "link";
            }
        }
        return column;
    }

    public static String mapSortColumn(String entity, String column) {
        return mapFilterColumn(entity, column);
    }

    public static Object mapFilterValue(String entity, String originalColumn, Object value, UserRepository userRepository) {
        if (value == null) {
            return null;
        }
        if (isNotificationEntity(entity) || isNotificationPreferenceEntity(entity)) {
            if ("user_id".equals(originalColumn)) {
                return resolveEmailFromUserId(value.toString(), userRepository);
            }
        }
        return value;
    }

    public static Map<String, Object> toApiRow(String entity, Map<String, Object> row, UserRepository userRepository) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        Map<String, Object> api = new LinkedHashMap<>(row);
        if (isNotificationEntity(entity)) {
            Object userEmail = row.get("user_email");
            if (userEmail != null) {
                api.put("user_id", resolveUserIdFromEmail(userEmail.toString(), userRepository));
            }
            Object link = row.get("link");
            if (link != null) {
                api.put("related_id", link);
            }
        }
        if (isNotificationPreferenceEntity(entity)) {
            Object userEmail = row.get("user_email");
            if (userEmail != null) {
                api.put("user_id", resolveUserIdFromEmail(userEmail.toString(), userRepository));
            }
        }
        return api;
    }

    public static Map<String, Object> toDbPayload(String entity, Map<String, Object> payload, UserRepository userRepository) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (isNotificationEntity(entity) || isNotificationPreferenceEntity(entity)) {
            if (db.containsKey("user_id")) {
                String email = resolveEmailFromUserId(db.remove("user_id").toString(), userRepository);
                if (email != null) {
                    db.put("user_email", email);
                }
            }
        }
        if (isNotificationEntity(entity) && db.containsKey("related_id")) {
            db.put("link", db.remove("related_id"));
        }
        return db;
    }

    private static String resolveEmailFromUserId(String userId, UserRepository userRepository) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        if (userId.contains("@")) {
            return userId.toLowerCase();
        }
        Optional<User> user = userRepository.findById(userId);
        return user.map(u -> u.getEmail() != null ? u.getEmail().toLowerCase() : null).orElse(null);
    }

    private static String resolveUserIdFromEmail(String email, UserRepository userRepository) {
        if (email == null || email.isBlank()) {
            return null;
        }
        if (!email.contains("@")) {
            return email;
        }
        return userRepository.findByEmail(email)
            .map(User::getId)
            .orElse(email);
    }
}
