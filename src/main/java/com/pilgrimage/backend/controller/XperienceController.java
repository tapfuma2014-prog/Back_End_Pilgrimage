package com.pilgrimage.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/xperience")
public class XperienceController {

    private final JdbcTemplate jdbcTemplate;

    public XperienceController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/galleryCommission")
    public Map<String, Object> galleryCommissionGet(@RequestParam(name = "commission_id") String commissionId) {
        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT id,
                   commission_id,
                   gallery_commission_id,
                   status,
                   progress_stage,
                   progress_percentage,
                   deposit_status,
                   balance_status,
                   balance_amount,
                   approval_status,
                   final_image_url,
                   tracking_number
            FROM gallery_commission
            WHERE commission_id = ?
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("commission_id", rs.getString("commission_id"));
                map.put("gallery_commission_id", rs.getString("gallery_commission_id"));
                map.put("status", rs.getString("status"));
                map.put("progress_stage", rs.getString("progress_stage"));
                map.put("progress_percentage", rs.getObject("progress_percentage"));
                map.put("deposit_status", rs.getString("deposit_status"));
                map.put("balance_status", rs.getString("balance_status"));
                map.put("balance_amount", rs.getObject("balance_amount"));
                map.put("approval_status", rs.getString("approval_status"));
                map.put("final_image_url", rs.getString("final_image_url"));
                map.put("tracking_number", rs.getString("tracking_number"));
                return map;
            },
            commissionId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        return rows.get(0);
    }

    @PostMapping("/galleryCommission")
    public Map<String, Object> galleryCommissionPost(@RequestBody Map<String, Object> payload) {
        String commissionId = payload.get("commission_id") != null ? payload.get("commission_id").toString() : null;
        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT id,
                   commission_id,
                   gallery_commission_id,
                   status,
                   progress_stage,
                   progress_percentage,
                   deposit_status,
                   balance_status,
                   balance_amount,
                   approval_status,
                   final_image_url,
                   tracking_number
            FROM gallery_commission
            WHERE commission_id = ?
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("commission_id", rs.getString("commission_id"));
                map.put("gallery_commission_id", rs.getString("gallery_commission_id"));
                map.put("status", rs.getString("status"));
                map.put("progress_stage", rs.getString("progress_stage"));
                map.put("progress_percentage", rs.getObject("progress_percentage"));
                map.put("deposit_status", rs.getString("deposit_status"));
                map.put("balance_status", rs.getString("balance_status"));
                map.put("balance_amount", rs.getObject("balance_amount"));
                map.put("approval_status", rs.getString("approval_status"));
                map.put("final_image_url", rs.getString("final_image_url"));
                map.put("tracking_number", rs.getString("tracking_number"));
                return map;
            },
            commissionId
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        return rows.get(0);
    }

    @PostMapping("/galleryPortraitDeliver")
    public Map<String, Object> galleryPortraitDeliver(@RequestBody Map<String, Object> payload) {
        String commissionId = payload.get("commission_id") != null ? payload.get("commission_id").toString() : null;
        String imageUrl = payload.get("image_url") != null ? payload.get("image_url").toString() : null;
        String imageBase64 = payload.get("image_base64") != null ? payload.get("image_base64").toString() : null;
        String title = payload.get("title") != null ? payload.get("title").toString() : null;
        String notes = payload.get("notes") != null ? payload.get("notes").toString() : null;
        Boolean isFinal = payload.get("is_final") != null ? Boolean.valueOf(payload.get("is_final").toString()) : true;

        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }
        if (imageUrl == null && imageBase64 == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing image_url or image_base64");
        }

        // Check if commission exists
        List<Map<String, Object>> commissionRows = jdbcTemplate.query("""
            SELECT id FROM gallery_commission WHERE commission_id = ? LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getString("id"));
                return map;
            },
            commissionId
        );

        if (commissionRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        // Process image: if base64, convert to URL (mock S3 behavior)
        String finalImageUrl = imageUrl;
        if (imageBase64 != null && !imageBase64.isBlank()) {
            // Mock S3 upload - in real implementation, upload to S3 and get URL
            String storedName = UUID.randomUUID().toString() + ".png";
            finalImageUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/xperience/uploads/")
                .path(storedName)
                .toUriString();
        }

        // Insert delivery record
        String deliveryId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO gallery_portrait_delivery (id, commission_id, delivery_url, status, created_by, created_date, updated_date)
            VALUES (?, ?, ?, 'delivered', 'xperience', now(), now())
            """,
            deliveryId, commissionId, finalImageUrl
        );

        // Update commission status to awaiting_approval
        jdbcTemplate.update("""
            UPDATE gallery_commission
            SET status = 'awaiting_approval',
                final_image_url = ?,
                updated_date = now()
            WHERE commission_id = ?
            """,
            finalImageUrl, commissionId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("commission_id", commissionId);
        response.put("image_url", finalImageUrl);
        response.put("commission_status", "awaiting_approval");
        return response;
    }

    @GetMapping("/galleryApproval")
    public Map<String, Object> galleryApprovalGet(@RequestParam(name = "commission_id") String commissionId) {
        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }

        // Check if commission exists
        List<Map<String, Object>> commissionRows = jdbcTemplate.query("""
            SELECT id FROM gallery_commission WHERE commission_id = ? LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getString("id"));
                return map;
            },
            commissionId
        );

        if (commissionRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT commission_id,
                   approved,
                   approved_at,
                   revision_note,
                   revision_count,
                   max_revisions
            FROM gallery_approval
            WHERE commission_id = ?
            ORDER BY created_date DESC
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("commission_id", rs.getString("commission_id"));
                Boolean approved = rs.getBoolean("approved");
                String approvalStatus;
                if (approved) {
                    approvalStatus = "approved";
                } else if (rs.getString("revision_note") != null && !rs.getString("revision_note").isBlank()) {
                    approvalStatus = "revision_requested";
                } else {
                    approvalStatus = "pending";
                }
                map.put("approval_status", approvalStatus);
                map.put("approved_at", rs.getTimestamp("approved_at"));
                map.put("revision_note", rs.getString("revision_note"));
                map.put("revision_count", rs.getObject("revision_count"));
                map.put("max_revisions", rs.getObject("max_revisions"));
                return map;
            },
            commissionId
        );

        if (rows.isEmpty()) {
            // No approval record yet, return pending
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("commission_id", commissionId);
            pending.put("approval_status", "pending");
            pending.put("approved_at", null);
            pending.put("revision_note", null);
            pending.put("revision_count", 0);
            pending.put("max_revisions", 3);
            return pending;
        }

        return rows.get(0);
    }

    @PostMapping("/galleryApproval")
    public Map<String, Object> galleryApprovalPost(@RequestBody Map<String, Object> payload) {
        String commissionId = payload.get("commission_id") != null ? payload.get("commission_id").toString() : null;
        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }

        // Check if commission exists
        List<Map<String, Object>> commissionRows = jdbcTemplate.query("""
            SELECT id FROM gallery_commission WHERE commission_id = ? LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getString("id"));
                return map;
            },
            commissionId
        );

        if (commissionRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT commission_id,
                   approved,
                   approved_at,
                   revision_note,
                   revision_count,
                   max_revisions
            FROM gallery_approval
            WHERE commission_id = ?
            ORDER BY created_date DESC
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("commission_id", rs.getString("commission_id"));
                Boolean approved = rs.getBoolean("approved");
                String approvalStatus;
                if (approved) {
                    approvalStatus = "approved";
                } else if (rs.getString("revision_note") != null && !rs.getString("revision_note").isBlank()) {
                    approvalStatus = "revision_requested";
                } else {
                    approvalStatus = "pending";
                }
                map.put("approval_status", approvalStatus);
                map.put("approved_at", rs.getTimestamp("approved_at"));
                map.put("revision_note", rs.getString("revision_note"));
                map.put("revision_count", rs.getObject("revision_count"));
                map.put("max_revisions", rs.getObject("max_revisions"));
                return map;
            },
            commissionId
        );

        if (rows.isEmpty()) {
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("commission_id", commissionId);
            pending.put("approval_status", "pending");
            pending.put("approved_at", null);
            pending.put("revision_note", null);
            pending.put("revision_count", 0);
            pending.put("max_revisions", 3);
            return pending;
        }

        return rows.get(0);
    }

    @PostMapping("/galleryRefundRequest")
    public Map<String, Object> galleryRefundRequest(@RequestBody Map<String, Object> payload) {
        String commissionId = payload.get("commission_id") != null ? payload.get("commission_id").toString() : null;
        String reason = payload.get("reason") != null ? payload.get("reason").toString() : null;
        String requestedBy = payload.get("requested_by") != null ? payload.get("requested_by").toString() : null;
        String note = payload.get("note") != null ? payload.get("note").toString() : null;

        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reason");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing requested_by");
        }

        // Check if commission exists
        List<Map<String, Object>> commissionRows = jdbcTemplate.query("""
            SELECT id, amount FROM gallery_commission WHERE commission_id = ? LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getString("id"));
                map.put("amount", rs.getObject("amount"));
                return map;
            },
            commissionId
        );

        if (commissionRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commission not found");
        }

        Double amount = (Double) commissionRows.get(0).get("amount");

        // Check if refund already requested
        List<Map<String, Object>> existingRefunds = jdbcTemplate.query("""
            SELECT id FROM gallery_refund_request WHERE commission_id = ? LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", rs.getString("id"));
                return map;
            },
            commissionId
        );

        if (!existingRefunds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund already requested");
        }

        // Create refund request
        String refundId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
            INSERT INTO gallery_refund_request (id, commission_id, amount, reason, status, requested_by, note, created_by, created_date, updated_date)
            VALUES (?, ?, ?, ?, 'requested', ?, ?, 'xperience', now(), now())
            """,
            refundId, commissionId, amount, reason, requestedBy, note
        );

        // Update commission status to refund_requested
        jdbcTemplate.update("""
            UPDATE gallery_commission
            SET status = 'refund_requested',
                updated_date = now()
            WHERE commission_id = ?
            """,
            commissionId
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("refund_id", refundId);
        response.put("commission_id", commissionId);
        response.put("refund_status", "requested");
        response.put("amount", amount);
        return response;
    }

    @GetMapping("/galleryRefundStatus")
    public Map<String, Object> galleryRefundStatus(@RequestParam(name = "commission_id") String commissionId) {
        if (commissionId == null || commissionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing commission_id");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT status,
                   transfer_status,
                   amount,
                   reason
            FROM gallery_refund_request
            WHERE commission_id = ?
            ORDER BY created_date DESC
            LIMIT 1
            """,
            (rs, rowNum) -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("status", rs.getString("status"));
                map.put("transfer_status", rs.getString("transfer_status"));
                map.put("amount", rs.getObject("amount"));
                map.put("reason", rs.getString("reason"));
                return map;
            },
            commissionId
        );

        if (rows.isEmpty()) {
            Map<String, Object> none = new LinkedHashMap<>();
            none.put("status", "none");
            none.put("transfer_status", null);
            none.put("amount", null);
            none.put("reason", null);
            return none;
        }

        return rows.get(0);
    }
}
