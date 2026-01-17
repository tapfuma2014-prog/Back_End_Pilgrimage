package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.LlmService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LlmServiceImpl implements LlmService {
    private static final Pattern ID_PATTERN = Pattern.compile("ID:\\s*([^,\\s]+)");

    @Override
    public Map<String, Object> invoke(Map<String, Object> payload) {
        String prompt = asString(payload.get("prompt"));
        Map<String, Object> schema = asMap(payload.get("response_json_schema"));
        Map<String, Object> properties = schema != null ? asMap(schema.get("properties")) : null;

        if (properties != null) {
            Map<String, Object> response = new HashMap<>();
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> propertySchema = asMap(entry.getValue());
                response.put(key, buildValue(key, propertySchema, prompt));
            }
            return response;
        }

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("response", buildChatResponse(prompt));
        return fallback;
    }

    private Object buildValue(String key, Map<String, Object> schema, String prompt) {
        String type = schema != null ? asString(schema.get("type")) : "string";
        String normalizedKey = key.toLowerCase();

        if ("array".equalsIgnoreCase(type)) {
            if (normalizedKey.contains("id")) {
                return extractIds(prompt, 4);
            }
            return Collections.emptyList();
        }
        if ("boolean".equalsIgnoreCase(type)) {
            return false;
        }
        if ("number".equalsIgnoreCase(type) || "integer".equalsIgnoreCase(type)) {
            return 0;
        }
        if ("object".equalsIgnoreCase(type)) {
            return Collections.emptyMap();
        }
        if ("response".equalsIgnoreCase(key)) {
            return buildChatResponse(prompt);
        }
        if ("reasoning".equalsIgnoreCase(key)) {
            return "Selected based on the prompt and available data.";
        }
        return "Generated content for " + key.replace('_', ' ') + ".";
    }

    private List<String> extractIds(String prompt, int limit) {
        if (prompt == null || prompt.isBlank()) {
            return Collections.emptyList();
        }
        Matcher matcher = ID_PATTERN.matcher(prompt);
        List<String> ids = new ArrayList<>();
        while (matcher.find() && ids.size() < limit) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private String buildChatResponse(String prompt) {
        String question = extractLastCustomerQuestion(prompt).toLowerCase();
        if (question.contains("return") || question.contains("refund")) {
            return "We offer a 30-day return policy for merchandise in unused, original packaging. Artworks are final sale unless damaged in transit. Refunds are processed within 5-7 business days.";
        }
        if (question.contains("shipping") || question.contains("delivery")) {
            return "Standard shipping is a $12 AUD flat rate, and delivery within Australia takes 3-7 business days. Orders over $100 AUD ship free.";
        }
        if (question.contains("track")) {
            return "Tracking numbers are emailed once your order ships. You can also check Profile > Orders or contact support with your order number.";
        }
        if (question.contains("international")) {
            return "International shipping is available upon request. Please contact support with your location for options and pricing.";
        }
        return "I can help with orders, shipping, returns, events, and products. What would you like to know?";
    }

    private String extractLastCustomerQuestion(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        int lastIndex = prompt.lastIndexOf("Customer:");
        if (lastIndex >= 0) {
            return prompt.substring(lastIndex + "Customer:".length()).trim();
        }
        return prompt.trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? "" : Objects.toString(value);
    }
}
