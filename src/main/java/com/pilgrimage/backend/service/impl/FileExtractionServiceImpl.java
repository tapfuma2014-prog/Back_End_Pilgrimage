package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.FileExtractionService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FileExtractionServiceImpl implements FileExtractionService {
    @Override
    public Map<String, Object> extract(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        response.put("extracted_data", new HashMap<String, Object>());
        response.put("message", "Extraction service not configured");
        response.put("input", payload);
        return response;
    }
}
