package com.pilgrimage.backend.util;

import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;

import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Maps Vote entity API field names (frontend) to votes table columns (database).
 */
public final class VoteEntityMapper {

    private VoteEntityMapper() {
    }

    public static boolean isVoteEntity(String entity) {
        return "Vote".equals(entity);
    }

    public static String mapFilterColumn(String column) {
        if (column == null) {
            return column;
        }
        return switch (column) {
            case "vote_category_id" -> "category_id";
            case "voted_item_id" -> "artist_id";
            case "voter_id" -> "voter_email";
            case "points" -> "score";
            case "timestamp" -> "created_date";
            default -> column;
        };
    }

    public static String mapSortColumn(String column) {
        return mapFilterColumn(column);
    }

    public static Object mapFilterValue(String column, Object value, UserRepository userRepository) {
        if (value == null) {
            return null;
        }
        if ("voter_id".equals(column)) {
            return resolveEmailFromUserId(value.toString(), userRepository);
        }
        return value;
    }

    public static Map<String, Object> toApiRow(Map<String, Object> row, UserRepository userRepository) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        Map<String, Object> api = new LinkedHashMap<>(row);
        Object voterEmail = row.get("voter_email");
        if (voterEmail != null) {
            api.put("voter_id", resolveUserIdFromEmail(voterEmail.toString(), userRepository));
        }
        if (row.containsKey("category_id")) {
            api.put("vote_category_id", row.get("category_id"));
        }
        if (row.containsKey("artist_id")) {
            api.put("voted_item_id", row.get("artist_id"));
        }
        if (row.containsKey("score")) {
            api.put("points", row.get("score"));
        }
        if (row.containsKey("created_date")) {
            api.put("timestamp", row.get("created_date"));
        }
        return api;
    }

    public static Map<String, Object> toDbPayload(Map<String, Object> payload, UserRepository userRepository) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (db.containsKey("voter_id")) {
            String email = resolveEmailFromUserId(db.remove("voter_id").toString(), userRepository);
            if (email != null) {
                db.put("voter_email", email);
            }
        }
        if (db.containsKey("vote_category_id")) {
            db.put("category_id", db.remove("vote_category_id"));
        }
        if (db.containsKey("voted_item_id")) {
            db.put("artist_id", db.remove("voted_item_id"));
        }
        if (db.containsKey("points")) {
            db.put("score", db.remove("points"));
        }
        db.remove("timestamp");
        db.remove("audit_status");
        if (!db.containsKey("year")) {
            db.put("year", Year.now().getValue());
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
