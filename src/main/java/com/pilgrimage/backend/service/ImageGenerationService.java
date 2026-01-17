package com.pilgrimage.backend.service;

import java.util.Map;

public interface ImageGenerationService {
    Map<String, Object> generateImage(Map<String, Object> payload);
}
