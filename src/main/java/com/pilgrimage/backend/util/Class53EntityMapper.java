package com.pilgrimage.backend.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps Class 53 affiliate entity API fields to class53_affiliates / affiliate_referrals columns.
 */
public final class Class53EntityMapper {

    private static final Set<String> CLASS53_ENTITIES = Set.of("Class53Affiliate", "AffiliateReferral");

    private Class53EntityMapper() {
    }

    public static boolean isClass53Entity(String entity) {
        return entity != null && CLASS53_ENTITIES.contains(entity);
    }

    public static String mapFilterColumn(String entity, String column) {
        if (!"Class53Affiliate".equals(entity)) {
            return column;
        }
        return switch (column) {
            case "business_name" -> "name";
            case "contact_name" -> "name";
            default -> column;
        };
    }

    public static String mapSortColumn(String entity, String column) {
        return mapFilterColumn(entity, column);
    }

    public static Map<String, Object> toApiRow(String entity, Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        return switch (entity) {
            case "Class53Affiliate" -> toApiAffiliate(row);
            case "AffiliateReferral" -> toApiReferral(row);
            default -> row;
        };
    }

    public static Map<String, Object> toDbPayload(String entity, Map<String, Object> payload) {
        if (payload == null) {
            return Collections.emptyMap();
        }
        return switch (entity) {
            case "Class53Affiliate" -> toDbAffiliate(payload);
            case "AffiliateReferral" -> toDbReferral(payload);
            default -> payload;
        };
    }

    private static Map<String, Object> toApiAffiliate(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        Object name = row.get("name");
        Object organization = row.get("organization");
        api.put("business_name", organization != null && !organization.toString().isBlank()
            ? organization
            : name);
        api.put("contact_name", name);
        if (row.containsKey("total_earned")) {
            api.put("total_earnings", row.get("total_earned"));
            api.put("referral_revenue", row.get("total_earned"));
        }
        if (row.containsKey("total_referrals")) {
            api.put("referral_conversions", row.get("total_referrals"));
        }
        return api;
    }

    private static Map<String, Object> toDbAffiliate(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>(payload);
        if (payload.containsKey("business_name") && !payload.containsKey("name")) {
            db.put("name", payload.get("business_name"));
        } else if (payload.containsKey("contact_name") && !payload.containsKey("name")) {
            db.put("name", payload.get("contact_name"));
        }
        if (payload.containsKey("business_name") && !payload.containsKey("organization")) {
            db.put("organization", payload.get("business_name"));
        }
        if (payload.containsKey("referral_conversions") && !payload.containsKey("total_referrals")) {
            db.put("total_referrals", payload.get("referral_conversions"));
        }
        if (payload.containsKey("referral_revenue") && !payload.containsKey("total_earned")) {
            db.put("total_earned", payload.get("referral_revenue"));
        }
        if (payload.containsKey("total_earnings") && !payload.containsKey("total_earned")) {
            db.put("total_earned", payload.get("total_earnings"));
        }
        db.remove("business_name");
        db.remove("contact_name");
        db.remove("website");
        db.remove("instagram");
        db.remove("description");
        db.remove("event_types");
        db.remove("referral_clicks");
        db.remove("referral_conversions");
        db.remove("referral_revenue");
        db.remove("total_earnings");
        return db;
    }

    private static Map<String, Object> toApiReferral(Map<String, Object> row) {
        Map<String, Object> api = new LinkedHashMap<>(row);
        if (row.containsKey("sale_amount")) {
            api.put("booking_amount", row.get("sale_amount"));
        }
        if (row.containsKey("booking_type")) {
            api.put("landing_page", row.get("booking_type"));
        }
        String status = row.get("status") == null ? "" : row.get("status").toString();
        api.put("converted", "paid".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status));
        return api;
    }

    private static Map<String, Object> toDbReferral(Map<String, Object> payload) {
        Map<String, Object> db = new LinkedHashMap<>();
        copyIfPresent(payload, db, "affiliate_id");
        copyIfPresent(payload, db, "referral_code");
        copyIfPresent(payload, db, "booking_id");
        copyIfPresent(payload, db, "booking_type");
        copyIfPresent(payload, db, "sale_amount");
        copyIfPresent(payload, db, "commission_amount");
        copyIfPresent(payload, db, "status");
        copyIfPresent(payload, db, "paid_date");
        copyIfPresent(payload, db, "created_by");

        if (!db.containsKey("booking_id") && payload.containsKey("id")) {
            db.put("booking_id", payload.get("id"));
        }
        if (!db.containsKey("booking_type")) {
            db.put("booking_type", payload.getOrDefault("landing_page", "class53"));
        }
        if (!db.containsKey("sale_amount") && payload.containsKey("booking_amount")) {
            db.put("sale_amount", payload.get("booking_amount"));
        }
        if (!db.containsKey("status")) {
            Object converted = payload.get("converted");
            if (Boolean.TRUE.equals(converted)) {
                db.put("status", "pending");
            }
        }
        return db;
    }

    private static void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key) && source.get(key) != null) {
            target.put(key, source.get(key));
        }
    }
}
