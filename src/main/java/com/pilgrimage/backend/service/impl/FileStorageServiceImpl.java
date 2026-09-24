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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    // Allowed upload extensions: common images, documents, audio/video and archive formats.
    // Anything else (executables, scripts, web pages) is rejected.
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp", "avif", "tiff", "tif", "bmp",
        "heic", "heif", "svg", "ico",
        "raw", "cr2", "nef", "arw", "dng", "orf", "raf", "rw2", "pef", "sr2",
        "pdf", "txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp3", "mp4", "mov", "avi", "webm", "ogg", "wav", "flac", "m4a", "aac",
        "zip", "rar", "7z", "gz", "tar"
    );

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
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds the maximum allowed size of 20 MB");
        }
        String originalName = file.getOriginalFilename();
        String safeName = sanitizeFileName(originalName != null ? originalName : "upload");
        String extension = fileExtension(safeName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type '" + extension + "' is not allowed");
        }
        String filename = UUID.randomUUID() + "_" + safeName;
        Path target = storageLocation.resolve(filename).normalize();
        if (!target.startsWith(storageLocation)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return filename;
    }

    @Override
    public Resource loadAsResource(String filename) {
        if (filename == null || filename.isBlank()
            || filename.contains("..") || filename.contains("/") || filename.contains("\\")
            || filename.startsWith(".")) {
            // Reject traversal attempts outright - return a non-existent resource.
            return new FileSystemResource(storageLocation.resolve("__invalid__").toFile());
        }
        Path filePath = storageLocation.resolve(filename).normalize();
        if (!filePath.startsWith(storageLocation)) {
            return new FileSystemResource(storageLocation.resolve("__invalid__").toFile());
        }
        return new FileSystemResource(filePath.toFile());
    }

    private String fileExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sanitizeFileName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "upload" : sanitized;
    }
}
