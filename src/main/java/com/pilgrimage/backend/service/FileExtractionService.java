package com.pilgrimage.backend.service;

import java.util.Map;

public interface FileExtractionService {
    Map<String, Object> extract(Map<String, Object> payload);
}
