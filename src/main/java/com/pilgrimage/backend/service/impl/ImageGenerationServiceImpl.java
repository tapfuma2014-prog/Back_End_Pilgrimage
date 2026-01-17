package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.ImageGenerationService;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImageGenerationServiceImpl implements ImageGenerationService {
    private static final String PROVIDER = "pollinations";
    private static final String BASE_URL = "https://image.pollinations.ai/prompt/";

    @Override
    public Map<String, Object> generateImage(Map<String, Object> payload) {
        String prompt = payload.get("prompt") instanceof String
            ? ((String) payload.get("prompt")).trim()
            : "";

        Map<String, Object> response = new HashMap<>();
        if (prompt.isEmpty()) {
            response.put("url", "");
            response.put("message", "Prompt is required");
            response.put("provider", PROVIDER);
            return response;
        }

        String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        StringBuilder url = new StringBuilder(BASE_URL).append(encodedPrompt);

        List<String> params = new ArrayList<>();
        Integer width = toInteger(payload.get("width"));
        Integer height = toInteger(payload.get("height"));
        if (width != null && width > 0) {
            params.add("width=" + width);
        }
        if (height != null && height > 0) {
            params.add("height=" + height);
        }
        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }

        response.put("url", url.toString());
        response.put("provider", PROVIDER);
        return response;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
