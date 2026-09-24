package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.security.JwtTokenUtil;
import com.pilgrimage.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/apps/{appId}/functions")
public class FunctionsController {

    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;
    private final String expectedAppId;

    public FunctionsController(AuthService authService,
                               JwtTokenUtil jwtTokenUtil,
                               @Value("${app.base44-app-id:}") String expectedAppId) {
        this.authService = authService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.expectedAppId = expectedAppId == null ? "" : expectedAppId.trim();
    }

    @PostMapping("/{functionName}")
    public ResponseEntity<?> invoke(@PathVariable String appId,
                                    @PathVariable String functionName,
                                    @RequestBody Map<String, Object> payload) {
        // When an app id is configured, reject calls addressed to any other app.
        if (!expectedAppId.isEmpty() && !expectedAppId.equals(appId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        switch (functionName) {
            case "customLogin":
                return handleLogin(payload);
            case "customSignup":
                return handleSignup(payload);
            case "customLogout":
                return ResponseEntity.ok(Map.of("success", true));
            case "customRefreshToken":
                return handleRefreshToken(payload);
            case "customForgotPassword":
                return handleForgotPassword(payload);
            case "applyAccountRecovery":
                return handleApplyAccountRecovery(payload);
            case "customVerifyEmail":
                return handleVerifyEmail(payload);
            case "customChangePassword":
                return handleChangePassword(payload);
            default:
                return ResponseEntity.ok(Map.of("success", false, "error", "Function not found"));
        }
    }

    private ResponseEntity<?> handleLogin(Map<String, Object> payload) {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(getString(payload, "login"));
        loginRequest.setPassword(getString(payload, "password"));
        try {
            JwtResponse jwt = authService.authenticateUser(loginRequest);
            Map<String, Object> user = new HashMap<>();
            user.put("id", jwt.getId());
            user.put("fullName", jwt.getFullName());
            user.put("email", jwt.getEmail());
            user.put("role", jwt.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("access_token", jwt.getToken());
            response.put("refresh_token", jwt.getRefreshToken());
            response.put("user", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleSignup(Map<String, Object> payload) {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setFullName(getString(payload, "full_name"));
        registerRequest.setEmail(getString(payload, "email"));
        registerRequest.setPassword(getString(payload, "password"));
        try {
            authService.registerUser(registerRequest);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account created. Please sign in."
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleRefreshToken(Map<String, Object> payload) {
        String refreshToken = getString(payload, "refresh_token");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Refresh token required"));
        }
        try {
            // Only genuine refresh tokens may be exchanged for new token pairs.
            if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Invalid or expired refresh token"));
            }
            String username = jwtTokenUtil.extractUsername(refreshToken);
            String newAccessToken = jwtTokenUtil.generateToken(username);
            String newRefreshToken = jwtTokenUtil.generateRefreshToken(username);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "access_token", newAccessToken,
                "refresh_token", newRefreshToken
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Invalid or expired refresh token"));
        }
    }

    private ResponseEntity<?> handleForgotPassword(Map<String, Object> payload) {
        String email = getString(payload, "email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Email is required"));
        }
        try {
            authService.requestPasswordReset(email);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password reset link has been sent to your email"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleApplyAccountRecovery(Map<String, Object> payload) {
        String token = getString(payload, "rc");
        String newPassword = getString(payload, "new_password");
        String confirmPassword = getString(payload, "confirm_password");

        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Invalid or expired reset link"));
        }
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "New password is required"));
        }
        if (!newPassword.equals(confirmPassword)) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Passwords do not match"));
        }

        try {
            PasswordResetRequest request = new PasswordResetRequest();
            request.setToken(token);
            request.setNewPassword(newPassword);
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password has been reset successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleChangePassword(Map<String, Object> payload) {
        try {
            authService.changePassword(
                getString(payload, "access_token"),
                getString(payload, "current_password"),
                getString(payload, "new_password"),
                getString(payload, "confirm_password")
            );
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Password changed. Please sign in again."
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleVerifyEmail(Map<String, Object> payload) {
        String token = getString(payload, "token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Verification token is required"));
        }

        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email verified. You can now sign in."
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
