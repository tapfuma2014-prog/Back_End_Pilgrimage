package com.pilgrimage.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class XperienceAuthFilter extends OncePerRequestFilter {

    @Value("${app.secret:}")
    private String appSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply this filter to /xperience endpoints
        if (!path.startsWith("/xperience")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Fail closed: a blank configured secret must never authenticate anything.
        if (appSecret == null || appSecret.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Xperience secret is not configured");
            return;
        }

        // Check X-App-Secret header
        String appSecretHeader = request.getHeader("X-App-Secret");
        boolean isValid = false;

        if (appSecretHeader != null && !appSecretHeader.isBlank()) {
            isValid = secretEquals(appSecret, appSecretHeader);
        }

        // Check Authorization: Bearer header
        if (!isValid) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String bearerToken = authHeader.substring(7);
                isValid = secretEquals(appSecret, bearerToken);
            }
        }

        // Optional: Verify X-Xperience-Signature if present. Wrap the request so
        // reading the body here does not consume it for downstream @RequestBody binding.
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        if (isValid) {
            String signature = wrappedRequest.getHeader("X-Xperience-Signature");
            if (signature != null && !signature.isBlank()) {
                if (!verifySignature(wrappedRequest, signature)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("Invalid signature");
                    return;
                }
            }
        }

        if (!isValid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Invalid secret");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean secretEquals(String expected, String provided) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
    }

    private boolean verifySignature(HttpServletRequest request, String providedSignature) {
        if (appSecret == null || appSecret.isBlank()) {
            return false;
        }

        try {
            // Read the raw request body
            String body = request.getReader().lines().reduce("", (accumulator, actual) -> accumulator + actual);
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : expectedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String expectedSignature = hexString.toString();
            return MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), 
                                        providedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
