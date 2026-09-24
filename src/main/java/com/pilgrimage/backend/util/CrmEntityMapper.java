package com.pilgrimage.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

/**
 * Maps CRM entity API field names (frontend) to crm_* plural table columns (database).
 */
public final class CrmEntityMapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Set<String> CRM_ENTITIES = Set.of(
        "CRMClient", "CRMInteraction", "CRMCampaign", "CRMSegment", "CRMTask", "CRMWorkflow"
    );

    private CrmEntityMapper() {
    }

    public static boolean isCrmEntity(String entity) {
        return entity != null && CRM_ENTITIES.contains(entity);
    }

    public static String mapSortColumn(String entity, String column) {
        if (column == null) {
            return column;
        }
        return switch (entity) {
            case "CRMClient" -> mapClientSortColumn(column);
            case "CRMInteraction" -> mapInteractionSortColumn(column);
            case "CRMCampaign" -> mapCampaignSortColumn(column);
            default -> column;
        };
    }

    public static String mapFilterColumn(String entity, String column) {
        return mapSortColumn(entity, column);
    }

    public static Map<String, Object> toApiRow(String entity, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        return switch (entity) {
            case "CRMClient" -> toApiClient(row);
            case "CRMInteraction" -> toApiInteraction(row);
            case "CRMCampaign" -> toApiCampaign(row);
            case "CRMSegment" -> toApiSegment(row);
            case "CRMTask" -> toApiTask(row);
            case "CRMWorkflow" -> row;
            default -> row;
        };
    }

    public static Map<String, Object> toDbPayload(String entity, Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return switch (entity) {
            case "CRMClient" -> toDbClient(payload);
            case "CRMInteraction" -> toDbInteraction(payload);
            case "CRMCampaign" -> toDbCampaign(payload);
            case "CRMSegment" -> toDbSegment(payload);
            case "CRMTask" -> toDbTask(payload);
            case "CRMWorkflow" -> payload;
            default -> payload;
        };
    }

    private static String mapClientSortColumn(String column) {
        return switch (column) {
            case "full_name" -> "name";
            case "client_tier" -> "status";
            case "total_spent" -> "lifetime_value";
            default -> column;
        };
    }

    private static String mapInteractionSortColumn(String column) {
        if ("description".equals(column)) {
            return "notes";
        }
        return column;
    }

    private static String mapCampaignSortColumn(String column) {
        return switch (column) {
            case "type" -> "campaign_type";
            case "scheduled_date" -> "start_date";
            default -> column;
        };
    }

    private static Map<String, Object> toApiClient(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        if (api.containsKey("name")) {
            api.put("full_name", api.remove("name"));
        }
        if (api.containsKey("status")) {
            api.put("client_tier", api.remove("status"));
        }
        if (api.containsKey("lifetime_value")) {
            api.put("total_spent", api.remove("lifetime_value"));
        }
        mergeClientMeta(api);
        return api;
    }

    private static Map<String, Object> toDbClient(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (db.containsKey("full_name")) {
            db.put("name", db.remove("full_name"));
        }
        if (db.containsKey("client_tier")) {
            db.put("status", db.remove("client_tier"));
        }
        if (db.containsKey("total_spent")) {
            db.put("lifetime_value", db.remove("total_spent"));
        }
        packClientMeta(db);
        if (!db.containsKey("name") && db.get("email") != null) {
            db.put("name", String.valueOf(db.get("email")));
        }
        return db;
    }

    private static void mergeClientMeta(Map<String, Object> api) {
        Object notesValue = api.get("notes");
        if (notesValue == null) {
            return;
        }
        String notes = notesValue.toString();
        if (!notes.startsWith("{")) {
            return;
        }
        try {
            Map<String, Object> parsed = JSON.readValue(notes, new TypeReference<>() {});
            Object meta = parsed.get("_crm_meta");
            if (meta instanceof Map<?, ?> metaMap) {
                for (Map.Entry<?, ?> entry : metaMap.entrySet()) {
                    api.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                Object text = parsed.get("text");
                api.put("notes", text != null ? text : "");
            }
        } catch (Exception ignored) {
            // keep raw notes
        }
    }

    private static void packClientMeta(Map<String, Object> db) {
        Set<String> metaKeys = Set.of(
            "user_id", "total_bids", "total_wins", "last_bid_date", "last_win_date",
            "preferred_styles", "price_range_min", "price_range_max", "opt_in_marketing"
        );
        Map<String, Object> meta = new LinkedHashMap<>();
        for (String key : metaKeys) {
            if (db.containsKey(key)) {
                meta.put(key, db.remove(key));
            }
        }
        if (meta.isEmpty()) {
            return;
        }
        String textNotes = db.containsKey("notes") ? String.valueOf(db.get("notes")) : "";
        if (textNotes.startsWith("{")) {
            try {
                Map<String, Object> existing = JSON.readValue(textNotes, new TypeReference<>() {});
                Object existingText = existing.get("text");
                if (existingText != null) {
                    textNotes = String.valueOf(existingText);
                } else {
                    textNotes = "";
                }
            } catch (Exception ignored) {
                // keep textNotes as-is
            }
        }
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_crm_meta", meta);
        wrapper.put("text", textNotes);
        try {
            db.put("notes", JSON.writeValueAsString(wrapper));
        } catch (Exception ignored) {
            db.put("notes", textNotes);
        }
    }

    private static Map<String, Object> toApiInteraction(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        if (api.containsKey("notes")) {
            api.put("description", api.remove("notes"));
        }
        api.putIfAbsent("status", "completed");
        return api;
    }

    private static Map<String, Object> toDbInteraction(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (db.containsKey("description")) {
            db.put("notes", db.remove("description"));
        }
        db.remove("status");
        if (!db.containsKey("interaction_date")) {
            db.put("interaction_date", Instant.now().toString());
        }
        return db;
    }

    private static Map<String, Object> toApiCampaign(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        if (api.containsKey("campaign_type")) {
            api.put("type", api.remove("campaign_type"));
        }
        if (api.containsKey("start_date")) {
            api.put("scheduled_date", api.remove("start_date"));
        }
        Object description = api.get("description");
        if (description != null) {
            String text = description.toString();
            if (text.startsWith("{")) {
                try {
                    Map<String, Object> parsed = JSON.readValue(text, new TypeReference<>() {});
                    for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                        api.put(entry.getKey(), entry.getValue());
                    }
                    api.remove("description");
                } catch (Exception ignored) {
                    api.put("message_template", text);
                }
            } else if (!text.isBlank()) {
                api.put("message_template", text);
                api.remove("description");
            }
        }
        return api;
    }

    private static Map<String, Object> toDbCampaign(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (db.containsKey("type")) {
            db.put("campaign_type", db.remove("type"));
        }
        if (db.containsKey("scheduled_date")) {
            db.put("start_date", db.remove("scheduled_date"));
        }
        Map<String, Object> descriptionMeta = new LinkedHashMap<>();
        for (String key : List.of("subject", "message_template", "auction_id")) {
            if (db.containsKey(key)) {
                descriptionMeta.put(key, db.remove(key));
            }
        }
        if (!descriptionMeta.isEmpty()) {
            try {
                db.put("description", JSON.writeValueAsString(descriptionMeta));
            } catch (Exception ignored) {
                db.put("description", descriptionMeta.toString());
            }
        }
        if (!db.containsKey("status")) {
            db.put("status", "draft");
        }
        return db;
    }

    private static Map<String, Object> toApiSegment(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        Object isDynamic = api.get("is_dynamic");
        api.put("is_active", isDynamic == null || Boolean.TRUE.equals(isDynamic));
        return api;
    }

    private static Map<String, Object> toDbSegment(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (db.containsKey("is_active")) {
            db.put("is_dynamic", db.remove("is_active"));
        }
        db.remove("is_active");
        if (!db.containsKey("criteria")) {
            db.put("criteria", Collections.emptyMap());
        }
        return db;
    }

    private static Map<String, Object> toApiTask(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        api.putIfAbsent("task_type", "follow_up_call");
        return api;
    }

    private static Map<String, Object> toDbTask(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        db.remove("task_type");
        return db;
    }
}
