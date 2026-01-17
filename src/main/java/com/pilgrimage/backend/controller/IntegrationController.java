package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.service.FileExtractionService;
import com.pilgrimage.backend.service.FileStorageService;
import com.pilgrimage.backend.service.ImageGenerationService;
import com.pilgrimage.backend.service.LlmService;
import com.pilgrimage.backend.service.NotificationDispatchService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/integrations")
public class IntegrationController {

    private final ImageGenerationService imageGenerationService;
    private final LlmService llmService;
    private final NotificationDispatchService notificationDispatchService;
    private final FileStorageService fileStorageService;
    private final FileExtractionService fileExtractionService;

    public IntegrationController(
        ImageGenerationService imageGenerationService,
        LlmService llmService,
        NotificationDispatchService notificationDispatchService,
        FileStorageService fileStorageService,
        FileExtractionService fileExtractionService
    ) {
        this.imageGenerationService = imageGenerationService;
        this.llmService = llmService;
        this.notificationDispatchService = notificationDispatchService;
        this.fileStorageService = fileStorageService;
        this.fileExtractionService = fileExtractionService;
    }

    @PostMapping("/llm")
    public Map<String, Object> invokeLlm(@RequestBody Map<String, Object> payload) {
        return llmService.invoke(payload);
    }

    @PostMapping("/send-email")
    public Map<String, Object> sendEmail(@RequestBody Map<String, Object> payload) {
        return notificationDispatchService.sendEmail(payload);
    }

    @PostMapping("/send-sms")
    public Map<String, Object> sendSms(@RequestBody Map<String, Object> payload) {
        return notificationDispatchService.sendSms(payload);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file) {
        String storedName = fileStorageService.store(file);
        String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/integrations/uploads/")
            .path(storedName)
            .toUriString();
        return Map.of(
            "file_url", fileUrl,
            "file_name", file.getOriginalFilename()
        );
    }

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> getUpload(@PathVariable("filename") String filename) throws IOException {
        Resource resource = fileStorageService.loadAsResource(filename);
        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(resource.getFile().toPath());
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .body(resource);
    }

    @PostMapping("/generate-image")
    public Map<String, Object> generateImage(@RequestBody Map<String, Object> payload) {
        return imageGenerationService.generateImage(payload);
    }

    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, Object> payload) {
        return fileExtractionService.extract(payload);
    }
}
