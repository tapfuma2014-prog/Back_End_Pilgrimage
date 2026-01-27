package com.pilgrimage.backend.service;

import java.util.Map;

public interface ImageGenerationService {
    Map<String, Object> generateImage(Map<String, Object> payload);

    ImageRequestParams buildRequestParams(Map<String, Object> payload);

    ImageProxyResponse fetchImage(Map<String, Object> payload);
}
