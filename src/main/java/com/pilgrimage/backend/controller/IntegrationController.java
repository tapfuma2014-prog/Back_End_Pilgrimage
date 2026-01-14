package com.pilgrimage.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/integrations")
public class IntegrationController {

    @PostMapping("/llm")
    public Map<String, Object> invokeLlm(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("content", "AI service not configured");
        response.put("recommended_ids", Collections.emptyList());
        response.put("reasoning", "AI service not configured");
        response.put("input", payload);
        return response;
    }

    @PostMapping("/send-email")
    public Map<String, Object> sendEmail(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "skipped");
        response.put("message", "Email service not configured");
        response.put("input", payload);
        return response;
    }

    @PostMapping("/send-sms")
    public Map<String, Object> sendSms(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "skipped");
        response.put("message", "SMS service not configured");
        response.put("input", payload);
        return response;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        response.put("file_url", "");
        response.put("file_name", file.getOriginalFilename());
        response.put("message", "Upload service not configured");
        return response;
    }

    @PostMapping("/generate-image")
    public Map<String, Object> generateImage(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("image_url", "");
        response.put("message", "Image generation service not configured");
        response.put("input", payload);
        return response;
    }

    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("extracted_data", Collections.emptyMap());
        response.put("message", "Extraction service not configured");
        response.put("input", payload);
        return response;
    }
}
