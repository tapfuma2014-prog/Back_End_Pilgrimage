package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    private final Path storageLocation;

    public FileStorageServiceImpl() {
        this.storageLocation = Path.of("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create upload directory", e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFileName(originalName != null ? originalName : "upload");
        String filename = UUID.randomUUID() + "_" + safeName;
        Path target = storageLocation.resolve(filename);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return filename;
    }

    @Override
    public Resource loadAsResource(String filename) {
        Path filePath = storageLocation.resolve(filename).normalize();
        return new FileSystemResource(filePath.toFile());
    }

    private String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "upload" : sanitized;
    }
}
