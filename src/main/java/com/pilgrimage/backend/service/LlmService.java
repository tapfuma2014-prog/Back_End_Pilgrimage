package com.pilgrimage.backend.service;

import java.util.Map;

public interface LlmService {
    Map<String, Object> invoke(Map<String, Object> payload);
}
