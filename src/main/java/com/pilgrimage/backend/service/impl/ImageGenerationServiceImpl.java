package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.ImageGenerationService;
import com.pilgrimage.backend.service.ImageProxyResponse;
import com.pilgrimage.backend.service.ImageRequestParams;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ImageGenerationServiceImpl implements ImageGenerationService {
    private static final String PROVIDER = "pollinations";
    private static final String BASE_URL = "https://gen.pollinations.ai/image/";
    private static final String API_KEY = "sk_a7bIyIDcEBW95MyiMLObx2hPMFYt7Si6";
    private static final String DEFAULT_MODEL = "flux";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Set<String> SUPPORTED_MODELS = Set.of(
        "kontext",
        "turbo",
        "seedream",
        "seedream-pro",
        "nanobanana",
        "nanobanana-pro",
        "gptimage",
        "gptimage-large",
        "veo",
        "seedance",
        "seedance-pro",
        "wan",
        "zimage",
        "flux",
        "klein",
        "klein-large"
    );

    @Override
    public Map<String, Object> generateImage(Map<String, Object> payload) {
        ImageRequestParams requestParams = buildRequestParams(payload);

        Map<String, Object> response = new HashMap<>();
        if (requestParams.prompt().isEmpty()) {
            response.put("url", "");
            response.put("image_url", "");
            response.put("preview_url", "");
            response.put("message", "Prompt is required");
            response.put("provider", PROVIDER);
            return response;
        }

        String imageUrl = buildSourceUrl(requestParams);
        response.put("url", imageUrl);
        response.put("image_url", imageUrl);
        response.put("preview_url", imageUrl);
        response.put("source_url", imageUrl);
        response.put("provider", PROVIDER);
        return response;
    }

    @Override
    public ImageRequestParams buildRequestParams(Map<String, Object> payload) {
        String prompt = payload.get("prompt") instanceof String
            ? ((String) payload.get("prompt")).trim()
            : "";
        String encodedPrompt = prompt.isEmpty()
            ? ""
            : URLEncoder.encode(prompt, StandardCharsets.UTF_8);
        Integer width = toInteger(payload.get("width"));
        Integer height = toInteger(payload.get("height"));
        String model = payload.get("model") instanceof String
            ? ((String) payload.get("model")).trim()
            : "";
        String selectedModel = SUPPORTED_MODELS.contains(model) ? model : DEFAULT_MODEL;
        return new ImageRequestParams(prompt, encodedPrompt, width, height, selectedModel);
    }

    @Override
    public ImageProxyResponse fetchImage(Map<String, Object> payload) {
        ImageRequestParams requestParams = buildRequestParams(payload);
        if (requestParams.prompt().isEmpty()) {
            byte[] body = "{\"message\":\"Prompt is required\"}".getBytes(StandardCharsets.UTF_8);
            return new ImageProxyResponse(400, "application/json", body);
        }

        String imageUrl = buildSourceUrl(requestParams);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(imageUrl))
            .header("Accept", "image/*")
            .GET();
        if (API_KEY != null && !API_KEY.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + API_KEY);
        }

        try {
            HttpResponse<byte[]> response = HTTP_CLIENT.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );
            String contentType = response.headers()
                .firstValue("content-type")
                .orElse("application/octet-stream");
            return new ImageProxyResponse(response.statusCode(), contentType, response.body());
        } catch (Exception ex) {
            byte[] body = ("{\"message\":\"" + ex.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            return new ImageProxyResponse(500, "application/json", body);
        }
    }

    private String buildSourceUrl(ImageRequestParams requestParams) {
        StringBuilder url = new StringBuilder(BASE_URL).append(requestParams.encodedPrompt());
        List<String> params = new ArrayList<>();
        params.add("model=" + requestParams.model());
        Integer width = requestParams.width();
        Integer height = requestParams.height();
        if (width != null && width > 0) {
            params.add("width=" + width);
        }
        if (height != null && height > 0) {
            params.add("height=" + height);
        }
        if (!params.isEmpty()) {
            url.append("?").append(String.join("&", params));
        }
        return url.toString();
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
