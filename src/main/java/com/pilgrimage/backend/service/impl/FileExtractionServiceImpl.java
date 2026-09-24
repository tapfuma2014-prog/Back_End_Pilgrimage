package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.FileExtractionService;
import com.pilgrimage.backend.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileExtractionServiceImpl implements FileExtractionService {
    private final FileStorageService fileStorageService;

    public FileExtractionServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Map<String, Object> extract(Map<String, Object> payload) {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> extractedData = new HashMap<>();

        String filename = (String) payload.get("filename");
        if (filename != null && !filename.isBlank()) {
            try {
                Resource resource = fileStorageService.loadAsResource(filename);
                if (resource.exists() && resource.isReadable()) {
                    String content = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
                    extractedData.put("text", content);
                    extractedData.put("filename", filename);
                    extractedData.put("size", resource.contentLength());
                    response.put("message", "Extraction successful");
                } else {
                    response.put("message", "File not found or not readable");
                }
            } catch (IOException e) {
                // Do not leak filesystem paths or OS error details to the caller.
                response.put("message", "Failed to read file");
            }
        } else {
            response.put("message", "Filename not provided in payload");
        }

        response.put("extracted_data", extractedData);
        response.put("input", payload);
        return response;
    }
}
