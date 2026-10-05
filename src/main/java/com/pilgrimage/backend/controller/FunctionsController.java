package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.security.JwtTokenUtil;
import com.pilgrimage.backend.service.AuthService;
import com.pilgrimage.backend.service.SmsService;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import com.pilgrimage.backend.util.SimpleRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/apps/{appId}/functions")
public class FunctionsController {

    private final AuthService authService;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final SmsService smsService;

    // Throttle verification-email resends so the endpoint can't be used to spam mailboxes.
    private final SimpleRateLimiter resendRateLimiter =
        new SimpleRateLimiter(3, 3_600_000L, "Too many verification email requests. Please try again later.");
    // Every SMS costs money — tighter limits than email.
    private final SimpleRateLimiter smsOtpRateLimiter =
        new SimpleRateLimiter(5, 3_600_000L, "Too many SMS requests. Please try again later.");
    private final SimpleRateLimiter bookingSmsRateLimiter =
        new SimpleRateLimiter(10, 3_600_000L, "Too many requests. Please try again later.");
    private final String expectedAppId;

    public FunctionsController(AuthService authService,
                               JwtTokenUtil jwtTokenUtil,
                               UserRepository userRepository,
                               SmsService smsService,
                               @Value("${app.base44-app-id:}") String expectedAppId) {
        this.authService = authService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.smsService = smsService;
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
            case "customIssueSession":
                return handleIssueSession();
            case "customRefreshToken":
                return handleRefreshToken(payload);
            case "customForgotPassword":
                return handleForgotPassword(payload);
            case "applyAccountRecovery":
                return handleApplyAccountRecovery(payload);
            case "customVerifyEmail":
                return handleVerifyEmail(payload);
            case "customResendVerification":
                return handleResendVerification(payload);
            case "customSendSmsOtp":
                return handleSendSmsOtp(payload);
            case "customVerifySmsOtp":
                return handleVerifySmsOtp(payload);
            case "customSendBookingSms":
                return handleSendBookingSms(payload);
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

    // Mints a fresh access/refresh pair for an already-authenticated caller.
    // Sessions created without credentials (access_token URL param,
    // base44.auth.setToken, pre-refresh-token sessions) have no way to recover
    // from access-token expiry, so the frontend calls this once to arm them.
    private ResponseEntity<?> handleIssueSession() {
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Authentication required"));
        }
        return ResponseEntity.ok(Map.of(
            "success", true,
            "access_token", jwtTokenUtil.generateToken(email),
            "refresh_token", jwtTokenUtil.generateRefreshToken(email)
        ));
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
            // The subject must still resolve to a real account — otherwise we
            // would keep minting access tokens that JwtRequestFilter can never
            // authenticate, leaving the client in a zombie session where every
            // request 401s but refresh keeps "succeeding".
            Optional<User> subjectUser = username == null
                ? Optional.empty()
                : userRepository.findByEmailIgnoreCase(username);
            if (subjectUser.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Invalid or expired refresh token"));
            }
            // Mint under the stored email's canonical lowercase form so the new
            // subject always round-trips through loadUserByUsername.
            String canonicalSubject = subjectUser.get().getEmail().toLowerCase();
            String newAccessToken = jwtTokenUtil.generateToken(canonicalSubject);
            String newRefreshToken = jwtTokenUtil.generateRefreshToken(canonicalSubject);
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

    private ResponseEntity<?> handleResendVerification(Map<String, Object> payload) {
        String email = getString(payload, "email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Email is required"));
        }
        try {
            resendRateLimiter.check(email);
            authService.resendVerificationEmail(email);
            // Generic message — never reveal whether the address is registered.
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "If an unverified account exists for this email, a new verification link has been sent."
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleSendSmsOtp(Map<String, Object> payload) {
        String email = getString(payload, "email");
        String phone = getString(payload, "phone");
        try {
            smsOtpRateLimiter.check(email == null ? "anonymous" : email);
            authService.sendSmsOtp(email, phone);
            // Generic message — never reveal whether the account exists.
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "If this account exists, a verification code has been sent."
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private ResponseEntity<?> handleVerifySmsOtp(Map<String, Object> payload) {
        try {
            authService.verifySmsOtp(getString(payload, "email"), getString(payload, "code"));
            return ResponseEntity.ok(Map.of("success", true, "message", "Phone verified"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // Sends a booking-confirmation SMS to the caller's own verified phone number.
    // Client-supplied numbers are deliberately ignored so this can't be used to spam strangers.
    private ResponseEntity<?> handleSendBookingSms(Map<String, Object> payload) {
        String callerEmail = EntityAuthorizationHelper.currentUserEmail();
        if (callerEmail == null) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Authentication required"));
        }
        try {
            bookingSmsRateLimiter.check(callerEmail);
            User user = userRepository.findByEmail(callerEmail.toLowerCase()).orElse(null);
            if (user == null || user.getPhone() == null || !Boolean.TRUE.equals(user.getPhoneVerified())) {
                // Not an error worth failing the booking over — the email confirmation still went out.
                return ResponseEntity.ok(Map.of("success", true, "skipped", true));
            }
            String message = getString(payload, "message");
            if (message == null || message.isBlank()) {
                return ResponseEntity.ok(Map.of("success", false, "error", "Message is required"));
            }
            smsService.sendSms(user.getPhone(), message);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String getString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}
