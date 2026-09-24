package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.service.FileStorageService;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/portrait-commissions")
public class PortraitCommissionController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public PortraitCommissionController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                        UserRepository userRepository, FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public List<Map<String, Object>> listCommissions(@RequestParam(name = "status", required = false) String status,
                                                     @RequestParam(name = "artistId", required = false) String artistId) {
        String caller = EntityAuthorizationHelper.currentUserEmail();
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        String sql = """
            SELECT id,
                   artist_id,
                   artist_name,
                   contact_email,
                   contact_phone,
                   style,
                   size,
                   custom_size,
                   background_preference,
                   reference_image,
                   reference_images,
                   sample_portrait_image,
                   mood_keywords,
                   special_requests,
                   total_price,
                   deposit_paid,
                   balance_due,
                   deposit_paid_at,
                   balance_paid,
                   estimated_completion,
                   progress_stage,
                   progress_percentage,
                   revision_count,
                   max_revisions,
                   email_notifications,
                   sms_notifications,
                   milestones,
                   progress_history,
                   status,
                   created_date
            FROM portrait_commission
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (artistId != null && !artistId.isBlank() && isAdmin) {
            conditions.add("artist_id = ?");
            params.add(artistId);
        }
        if (!isAdmin) {
            // Non-admin callers may only see commissions they submitted themselves.
            conditions.add("(LOWER(contact_email) = LOWER(?) OR LOWER(created_by) = LOWER(?))");
            params.add(caller);
            params.add(caller);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toCommissionMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toCommissionMap(rs), params.toArray());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createCommission(
        @RequestPart("file") MultipartFile file,
        @RequestPart(name = "sampleFile", required = false) MultipartFile sampleFile,
        @RequestParam(name = "artistId", required = false) String artistId,
        @RequestParam(name = "artistName", required = false) String artistName,
        @RequestParam(name = "style", required = false) String style,
        @RequestParam(name = "size", required = false) String size,
        @RequestParam(name = "specialRequests", required = false) String specialRequests,
        @RequestParam(name = "contactEmail", required = false) String contactEmail,
        @RequestParam(name = "contactPhone", required = false) String contactPhone
    ) {
        String caller = EntityAuthorizationHelper.currentUserEmail();
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if ((artistId == null || artistId.isBlank()) && (artistName == null || artistName.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artistId or artistName is required");
        }

        Map<String, Object> artist = findArtist(artistId, artistName);
        if (artist == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found");
        }

        String storedName = fileStorageService.store(file);
        String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/integrations/uploads/")
            .path(storedName)
            .toUriString();

        // Optional second upload: a sample portrait the rider supplies as a
        // style reference for the artist (distinct from the subject photo above).
        String sampleUrl = null;
        if (sampleFile != null && !sampleFile.isEmpty()) {
            String sampleStoredName = fileStorageService.store(sampleFile);
            sampleUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/integrations/uploads/")
                .path(sampleStoredName)
                .toUriString();
        }

        String id = UUID.randomUUID().toString();
        String email = (contactEmail != null && !contactEmail.isBlank()) ? contactEmail.trim() : caller;
        String referenceImagesJson;
        try {
            referenceImagesJson = objectMapper.writeValueAsString(List.of(fileUrl));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize reference images");
        }

        jdbcTemplate.update("""
            INSERT INTO portrait_commission (
                id, artist_id, artist_name, contact_email, contact_phone,
                style, size, special_requests, reference_image, reference_images,
                sample_portrait_image,
                status, progress_stage, progress_percentage, revision_count, max_revisions,
                email_notifications, sms_notifications, created_by, created_date, updated_date
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?,
                      'submitted', 'Awaiting Start', 0, 0, 2, true, false, ?, now(), now())
            """,
            id,
            artist.get("id"),
            artist.get("name"),
            email,
            contactPhone,
            style,
            size,
            specialRequests,
            fileUrl,
            referenceImagesJson,
            sampleUrl,
            caller
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("artist_id", artist.get("id"));
        response.put("artist_name", artist.get("name"));
        response.put("contact_email", email);
        response.put("contact_phone", contactPhone);
        response.put("style", style);
        response.put("size", size);
        response.put("special_requests", specialRequests);
        response.put("reference_image", fileUrl);
        response.put("sample_portrait_image", sampleUrl);
        response.put("status", "submitted");
        response.put("progress_stage", "Awaiting Start");
        response.put("progress_percentage", 0);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/status")
    public List<Map<String, Object>> listCommissionStatuses(@RequestParam(name = "contactEmail", required = false) String contactEmail) {
        String caller = EntityAuthorizationHelper.currentUserEmail();
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);

        String sql = """
            SELECT id,
                   artist_id,
                   artist_name,
                   contact_email,
                   status,
                   progress_stage,
                   progress_percentage,
                   estimated_completion,
                   revision_count,
                   max_revisions,
                   milestones,
                   progress_history,
                   reference_image,
                   sample_portrait_image,
                   created_date,
                   updated_date
            FROM portrait_commission
        """;

        List<Object> params = new ArrayList<>();
        if (isAdmin) {
            if (contactEmail != null && !contactEmail.isBlank()) {
                sql += " WHERE LOWER(contact_email) = LOWER(?)";
                params.add(contactEmail.trim());
            }
        } else {
            sql += " WHERE (LOWER(contact_email) = LOWER(?) OR LOWER(created_by) = LOWER(?))";
            params.add(caller);
            params.add(caller);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toStatusMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toStatusMap(rs), params.toArray());
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> getCommissionStatus(@PathVariable("id") String id) {
        String caller = EntityAuthorizationHelper.currentUserEmail();
        if (caller == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT id,
                   artist_id,
                   artist_name,
                   contact_email,
                   status,
                   progress_stage,
                   progress_percentage,
                   estimated_completion,
                   revision_count,
                   max_revisions,
                   milestones,
                   progress_history,
                   reference_image,
                   sample_portrait_image,
                   created_by,
                   created_date,
                   updated_date
            FROM portrait_commission
            WHERE id = ?
        """, (rs, rowNum) -> {
            Map<String, Object> map = toStatusMap(rs);
            map.put("created_by", rs.getString("created_by"));
            return map;
        }, id);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portrait commission not found");
        }

        Map<String, Object> commission = rows.get(0);
        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        boolean isOwner = caller.equalsIgnoreCase(String.valueOf(commission.get("contact_email")))
            || caller.equalsIgnoreCase(String.valueOf(commission.get("created_by")));
        if (!isAdmin && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        commission.remove("created_by");
        return commission;
    }

    private Map<String, Object> findArtist(String artistId, String artistName) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (artistId != null && !artistId.isBlank()) {
            rows = queryArtist("SELECT id, name FROM artists WHERE id = ? LIMIT 1", artistId.trim());
            if (rows.isEmpty()) {
                rows = queryArtist("SELECT id, name FROM artist WHERE id = ? LIMIT 1", artistId.trim());
            }
        }
        if (rows.isEmpty() && artistName != null && !artistName.isBlank()) {
            rows = queryArtist("SELECT id, name FROM artists WHERE LOWER(name) = LOWER(?) LIMIT 1", artistName.trim());
            if (rows.isEmpty()) {
                rows = queryArtist("SELECT id, name FROM artist WHERE LOWER(name) = LOWER(?) LIMIT 1", artistName.trim());
            }
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> queryArtist(String sql, String param) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getString("id"));
            map.put("name", rs.getString("name"));
            return map;
        }, param);
    }

    private Map<String, Object> toStatusMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("artist_id", rs.getString("artist_id"));
        map.put("artist_name", rs.getString("artist_name"));
        map.put("contact_email", rs.getString("contact_email"));
        map.put("status", rs.getString("status"));
        map.put("progress_stage", rs.getString("progress_stage"));
        map.put("progress_percentage", rs.getObject("progress_percentage"));
        map.put("estimated_completion", rs.getString("estimated_completion"));
        map.put("revision_count", rs.getObject("revision_count"));
        map.put("max_revisions", rs.getObject("max_revisions"));
        map.put("milestones", parseJsonArray(rs.getString("milestones")));
        map.put("progress_history", parseJsonArray(rs.getString("progress_history")));
        map.put("reference_image", rs.getString("reference_image"));
        map.put("sample_portrait_image", rs.getString("sample_portrait_image"));
        map.put("created_date", rs.getTimestamp("created_date"));
        map.put("updated_date", rs.getTimestamp("updated_date"));
        return map;
    }

    private Map<String, Object> toCommissionMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("artist_id", rs.getString("artist_id"));
        map.put("artist_name", rs.getString("artist_name"));
        map.put("contact_email", rs.getString("contact_email"));
        map.put("contact_phone", rs.getString("contact_phone"));
        map.put("style", rs.getString("style"));
        map.put("size", rs.getString("size"));
        map.put("custom_size", rs.getString("custom_size"));
        map.put("background_preference", rs.getString("background_preference"));
        map.put("reference_image", rs.getString("reference_image"));
        map.put("reference_images", parseJsonArray(rs.getString("reference_images")));
        map.put("sample_portrait_image", rs.getString("sample_portrait_image"));
        map.put("mood_keywords", parseJsonArray(rs.getString("mood_keywords")));
        map.put("special_requests", rs.getString("special_requests"));
        map.put("total_price", rs.getObject("total_price"));
        map.put("deposit_paid", rs.getObject("deposit_paid"));
        map.put("balance_due", rs.getObject("balance_due"));
        map.put("deposit_paid_at", rs.getTimestamp("deposit_paid_at"));
        map.put("balance_paid", rs.getObject("balance_paid"));
        map.put("estimated_completion", rs.getString("estimated_completion"));
        map.put("progress_stage", rs.getString("progress_stage"));
        map.put("progress_percentage", rs.getObject("progress_percentage"));
        map.put("revision_count", rs.getObject("revision_count"));
        map.put("max_revisions", rs.getObject("max_revisions"));
        map.put("email_notifications", rs.getObject("email_notifications"));
        map.put("sms_notifications", rs.getObject("sms_notifications"));
        map.put("milestones", parseJsonArray(rs.getString("milestones")));
        map.put("progress_history", parseJsonArray(rs.getString("progress_history")));
        map.put("status", rs.getString("status"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
