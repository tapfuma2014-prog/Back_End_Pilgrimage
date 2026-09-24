package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.ProfileUpdateRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.service.AuthService;
import com.pilgrimage.backend.util.SimpleRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Throttle credential-guessing and reset/registration spam per email address.
    private final SimpleRateLimiter loginRateLimiter =
        new SimpleRateLimiter(10, 60_000L, "Too many login attempts. Please try again later.");
    private final SimpleRateLimiter registerRateLimiter =
        new SimpleRateLimiter(5, 3_600_000L, "Too many registration attempts. Please try again later.");
    private final SimpleRateLimiter resetRateLimiter =
        new SimpleRateLimiter(5, 3_600_000L, "Too many password reset attempts. Please try again later.");

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        loginRateLimiter.check(loginRequest != null ? loginRequest.getEmail() : null);
        JwtResponse response = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        registerRateLimiter.check(registerRequest != null ? registerRequest.getEmail() : null);
        try {
            authService.registerUser(registerRequest);
            log.info("User registered successfully");
            return ResponseEntity.ok(Collections.singletonMap("message", "User registered successfully"));
        } catch (Exception e) {
            log.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Collections.singletonMap("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String email = authentication.getName();
        User user = authService.getCurrentUser(email);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileUpdateRequest profileUpdateRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String email = authentication.getName();
        User updatedUser = authService.updateProfile(email, profileUpdateRequest);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> requestPasswordReset(
            @RequestParam(name = "email", required = false) String emailParam,
            @RequestBody(required = false) Map<String, Object> body) {
        // Prefer the request body so the email is not leaked via URL query params
        // (which end up in access logs and browser history). The query param is
        // still accepted for backwards compatibility.
        String email = emailParam;
        if (body != null && body.get("email") != null) {
            email = body.get("email").toString();
        }
        resetRateLimiter.check(email);
        authService.requestPasswordReset(email);
        return ResponseEntity.ok("Password reset link has been sent to your email");
    }

    @PostMapping("/password/confirm")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest passwordResetRequest) {
        authService.resetPassword(passwordResetRequest);
        return ResponseEntity.ok("Password has been reset successfully");
    }
}
