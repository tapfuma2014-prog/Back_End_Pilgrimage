package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.ProfileUpdateRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collections;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        JwtResponse response = authService.authenticateUser(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        try {
            System.out.println("Registering user with email: " + registerRequest.getEmail());
            User user = authService.registerUser(registerRequest);
            System.out.println("User registered successfully: " + user.getEmail());
            return ResponseEntity.ok(Collections.singletonMap("message", "User registered successfully"));
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
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
        String email = authentication.getName();
        User updatedUser = authService.updateProfile(email, profileUpdateRequest);
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> requestPasswordReset(@RequestParam String email) {
        authService.requestPasswordReset(email);
        return ResponseEntity.ok("Password reset link has been sent to your email");
    }

    @PostMapping("/password/confirm")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordResetRequest passwordResetRequest) {
        authService.resetPassword(passwordResetRequest);
        return ResponseEntity.ok("Password has been reset successfully");
    }
}
