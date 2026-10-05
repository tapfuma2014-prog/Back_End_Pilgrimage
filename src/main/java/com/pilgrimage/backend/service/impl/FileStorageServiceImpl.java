package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.service.FileStorageService;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Minimal in-memory object store used in place of an S3 bucket for local/runtime use.
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

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

        String key = UUID.randomUUID() + "_" + safeName;
        try {
            objects.put(key, new StoredObject(safeName, file.getBytes()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file", e);
        }
        return key;
    }

    @Override
    public Resource loadAsResource(String filename) {
        if (filename == null || filename.isBlank()
            || filename.contains("..") || filename.contains("/") || filename.contains("\\")
            || filename.startsWith(".")) {
            return MissingResource.INSTANCE;
        }

        StoredObject storedObject = objects.get(filename);
        return storedObject == null ? MissingResource.INSTANCE : new StoredResource(filename, storedObject);
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

    private record StoredObject(String filename, byte[] content) {
    }

    private static final class StoredResource extends AbstractResource {
        private final String key;
        private final StoredObject storedObject;

        private StoredResource(String key, StoredObject storedObject) {
            this.key = key;
            this.storedObject = storedObject;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(storedObject.content());
        }

        @Override
        public long contentLength() {
            return storedObject.content().length;
        }

        @Override
        public String getFilename() {
            return storedObject.filename();
        }

        @Override
        public String getDescription() {
            return "Mock S3 object " + key;
        }
    }

    private static final class MissingResource extends AbstractResource {
        private static final MissingResource INSTANCE = new MissingResource();

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean isReadable() {
            return false;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new FileNotFoundException("Mock S3 object not found");
        }

        @Override
        public String getDescription() {
            return "Missing Mock S3 object";
        }
    }
}
