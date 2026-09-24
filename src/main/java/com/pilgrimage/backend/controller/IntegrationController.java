package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.service.FileExtractionService;
import com.pilgrimage.backend.service.FileStorageService;
import com.pilgrimage.backend.service.ImageGenerationService;
import com.pilgrimage.backend.service.ImageProxyResponse;
import com.pilgrimage.backend.service.ImageRequestParams;
import com.pilgrimage.backend.service.LlmService;
import com.pilgrimage.backend.service.NotificationDispatchService;
import com.pilgrimage.backend.util.EnquiryValidationHelper;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RestController
@RequestMapping("/integrations")
public class IntegrationController {

    private final ImageGenerationService imageGenerationService;
    private final LlmService llmService;
    private final NotificationDispatchService notificationDispatchService;
    private final FileStorageService fileStorageService;
    private final FileExtractionService fileExtractionService;

    private final Map<String, List<Long>> llmRateTimestamps = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> contactRateTimestamps = new ConcurrentHashMap<>();

    @Value("${app.contact.email:enquiries@53coxroad.com.au}")
    private String contactEmail;

    @Value("${app.image-preview-secret:}")
    private String imagePreviewSecret;

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String rateLimitKey;
        
        if (authentication != null && authentication.isAuthenticated() 
            && !"anonymousUser".equalsIgnoreCase(authentication.getName())) {
            rateLimitKey = authentication.getName().toLowerCase().trim();
        } else {
            rateLimitKey = "anonymous";
        }
        
        checkLlmRateLimit(rateLimitKey);
        return llmService.invoke(payload);
    }

    @PostMapping("/send-email")
    public Map<String, Object> sendEmail(@RequestBody Map<String, Object> payload) {
        requireAuthenticatedUser();
        EnquiryValidationHelper.validateSendEmailPayload(payload);
        return notificationDispatchService.sendEmail(payload);
    }

    @PostMapping("/send-sms")
    public Map<String, Object> sendSms(@RequestBody Map<String, Object> payload) {
        requireAuthenticatedUser();
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
        requireAuthenticatedUser();
        Map<String, Object> response = imageGenerationService.generateImage(payload);
        ImageRequestParams requestParams = imageGenerationService.buildRequestParams(payload);
        if (!requestParams.prompt().isEmpty()) {
            String previewUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/integrations/generate-image/preview")
                .queryParam("prompt", requestParams.prompt())
                .queryParam("model", requestParams.model())
                .queryParamIfPresent("width", java.util.Optional.ofNullable(requestParams.width()))
                .queryParamIfPresent("height", java.util.Optional.ofNullable(requestParams.height()))
                .queryParam("sig", signPreviewParams(requestParams))
                .toUriString();
            response.put("url", previewUrl);
            response.put("image_url", previewUrl);
            response.put("preview_url", previewUrl);
        }
        return response;
    }

    @GetMapping("/generate-image/preview")
    public ResponseEntity<byte[]> previewGeneratedImage(@RequestParam Map<String, String> params) {
        // The endpoint is reachable without a session (it is used as an <img> src),
        // so access is controlled by an HMAC signature over the parameters that is
        // only issued by the authenticated POST /generate-image handler.
        ImageRequestParams requestParams = imageGenerationService.buildRequestParams(new HashMap<>(params));
        String providedSig = params.get("sig");
        if (requestParams.prompt().isEmpty() || requestParams.prompt().length() > 2000
            || !verifyPreviewSignature(requestParams, providedSig)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing preview signature");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", requestParams.prompt());
        payload.put("model", requestParams.model());
        payload.put("width", requestParams.width());
        payload.put("height", requestParams.height());

        ImageProxyResponse proxyResponse = imageGenerationService.fetchImage(payload);
        MediaType contentType = proxyResponse.getContentType() != null
            ? MediaType.parseMediaType(proxyResponse.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.status(proxyResponse.getStatusCode())
            .contentType(contentType)
            .body(proxyResponse.getBody());
    }

    @PostMapping("/extract")
    public Map<String, Object> extract(@RequestBody Map<String, Object> payload) {
        requireAuthenticatedUser();
        return fileExtractionService.extract(payload);
    }

    @PostMapping("/contact")
    public Map<String, Object> contact(@RequestBody Map<String, Object> payload) {
        // Strip CR/LF from fields that end up in email headers to prevent header injection.
        String name = stripHeaderChars(getString(payload, "name"));
        String fromEmail = stripHeaderChars(getString(payload, "from_email"));
        String subject = stripHeaderChars(getString(payload, "subject"));
        String message = getString(payload, "message");

        if (name.isBlank() || message.isBlank() || !isPlausibleEmail(fromEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, email and message are required");
        }

        checkContactRateLimit(fromEmail);

        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("to", contactEmail);
        emailPayload.put("from_name", "53 Cox Road Website");
        emailPayload.put("subject", "Website Enquiry: " + subject + " — from " + name);
        emailPayload.put("body", "Name: " + name + "\nEmail: " + fromEmail + "\nSubject: " + subject + "\n\nMessage:\n" + message);

        return notificationDispatchService.sendEmail(emailPayload);
    }

    private String requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName().toLowerCase().trim();
    }

    private void checkLlmRateLimit(String email) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        List<Long> timestamps = llmRateTimestamps.computeIfAbsent(email, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t < windowStart);
            if (timestamps.size() >= 20) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
            }
            timestamps.add(now);
        }
    }

    private void checkContactRateLimit(String fromEmail) {
        long now = System.currentTimeMillis();
        long windowStart = now - 3_600_000L;
        String key = fromEmail.toLowerCase().trim();
        List<Long> timestamps = contactRateTimestamps.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t < windowStart);
            if (timestamps.size() >= 5) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many contact attempts");
            }
            timestamps.add(now);
        }
    }

    private String signPreviewParams(ImageRequestParams params) {
        String data = params.prompt() + "|" + params.model() + "|"
            + (params.width() == null ? "" : params.width()) + "|"
            + (params.height() == null ? "" : params.height());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(imagePreviewSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign preview URL", e);
        }
    }

    private boolean verifyPreviewSignature(ImageRequestParams params, String providedSig) {
        if (providedSig == null || providedSig.isBlank() || imagePreviewSecret == null || imagePreviewSecret.isBlank()) {
            return false;
        }
        String expected = signPreviewParams(params);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), providedSig.getBytes(StandardCharsets.UTF_8));
    }

    private String stripHeaderChars(String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", " ").trim();
    }

    private boolean isPlausibleEmail(String value) {
        return value != null && value.matches("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");
    }

    private String getString(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return "";
        }
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
