package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.dto.EntityFilterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class Base44CompatController {

    private final EntityController entityController;
    private final ObjectMapper objectMapper;

    public Base44CompatController(EntityController entityController, ObjectMapper objectMapper) {
        this.entityController = entityController;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/apps/public/prod/public-settings/by-id/{appId}")
    public ResponseEntity<?> publicSettings(@PathVariable String appId) {
        Map<String, Object> publicSettings = new HashMap<>();
        publicSettings.put("id", appId);
        publicSettings.put("public_settings", new HashMap<>());
        return ResponseEntity.ok(publicSettings);
    }

    @PostMapping("/apps/{appId}/analytics/track/batch")
    public ResponseEntity<?> analytics(@PathVariable String appId) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/apps/{appId}/entities/{entity}")
    public List<Map<String, Object>> listEntities(
            @PathVariable String appId,
            @PathVariable String entity,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "limit", required = false) Integer limit) {
        if (q != null && !q.isBlank()) {
            try {
                Map<String, Object> filters = objectMapper.readValue(q, new TypeReference<>() {});
                EntityFilterRequest request = new EntityFilterRequest();
                request.setFilters(filters != null ? filters : Collections.emptyMap());
                request.setSort(sort);
                request.setLimit(limit);
                return entityController.filter(entity, request);
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return entityController.list(entity, sort, limit);
    }

    @PostMapping("/apps/{appId}/entities/{entity}")
    public Map<String, Object> createEntity(
            @PathVariable String appId,
            @PathVariable String entity,
            @RequestBody Map<String, Object> payload) {
        return entityController.create(entity, payload);
    }

    @PutMapping("/apps/{appId}/entities/{entity}/{id}")
    public Map<String, Object> updateEntity(
            @PathVariable String appId,
            @PathVariable String entity,
            @PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        return entityController.update(entity, id, payload);
    }
}
