package com.pilgrimage.backend.service.impl;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.ProfileUpdateRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.security.JwtTokenUtil;
import com.pilgrimage.backend.service.AuthService;
import com.pilgrimage.backend.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int SECURE_TOKEN_BYTES = 32;
    private static final int RESET_TOKEN_EXPIRY_HOURS = 1;
    private static final int VERIFICATION_TOKEN_EXPIRY_HOURS = 24;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendUrl;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService,
                          @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        try {
            if (loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()
                || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
                throw new RuntimeException("Email and password are required");
            }
            // Authenticate the user with email and password
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail().toLowerCase().trim(),
                    loginRequest.getPassword()
                )
            );

            // Set the authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate JWT token and refresh token
            String jwt = jwtTokenUtil.generateToken(authentication.getName());
            String refreshToken = jwtTokenUtil.generateRefreshToken(authentication.getName());
            
            // Get user details
            User user = userRepository.findByEmail(loginRequest.getEmail().toLowerCase().trim())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Return JWT response with user details and refresh token
            return new JwtResponse(
                jwt,
                refreshToken,
                user.getId().toString(), 
                user.getFullName(), 
                user.getEmail(),
                user.getRole()
            );
            
        } catch (Exception e) {
            // Do not log credentials or PII; a generic message is returned to the caller.
            log.warn("Authentication failed: {}", e.getClass().getSimpleName());
            throw new RuntimeException("Invalid email or password");
        }
    }

    @Override
    public User registerUser(RegisterRequest registerRequest) {
        if (registerRequest.getFullName() == null || registerRequest.getFullName().isBlank()
            || registerRequest.getEmail() == null || registerRequest.getEmail().isBlank()
            || registerRequest.getPassword() == null || registerRequest.getPassword().isBlank()) {
            throw new RuntimeException("Full name, email, and password are required");
        }
        enforcePasswordPolicy(registerRequest.getPassword());
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        User user = new User();
        user.setFullName(registerRequest.getFullName());
        user.setEmail(registerRequest.getEmail().toLowerCase().trim());
        
        // Encode the password before saving
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());
        user.setPassword(encodedPassword);
        
        // Role is always 'user' at registration - never trust a client-supplied role.
        user.setRole("user");
        user.setEmailVerified(false);
        // Only the SHA-256 hash of the token is stored; the raw token lives only in the email link.
        user.setVerificationToken(hashToken(generateSecureToken()));
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_EXPIRY_HOURS));

        return userRepository.save(user);
    }

    @Override
    public User getCurrentUser(String email) {
        String normalizedEmail = email == null ? null : email.toLowerCase().trim();
        return userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public User updateProfile(String email, ProfileUpdateRequest profileUpdateRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (profileUpdateRequest.getFullName() != null) {
            user.setFullName(profileUpdateRequest.getFullName());
        }
        if (profileUpdateRequest.getEmail() != null && !profileUpdateRequest.getEmail().equals(email)) {
            if (userRepository.existsByEmail(profileUpdateRequest.getEmail())) {
                throw new RuntimeException("Email is already in use");
            }
            user.setEmail(profileUpdateRequest.getEmail());
        }

        return userRepository.save(user);
    }

    @Override
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email is required");
        }

        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return;
        }

        String resetToken = generateSecureToken();
        // Store only the SHA-256 hash so a database leak does not expose usable tokens.
        user.setResetToken(hashToken(resetToken));
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(RESET_TOKEN_EXPIRY_HOURS));
        userRepository.save(user);

        String resetLink = buildPasswordResetLink(resetToken);
        emailService.sendPasswordResetEmail(normalizedEmail, resetLink);
    }

    @Override
    public void resetPassword(PasswordResetRequest passwordResetRequest) {
        if (passwordResetRequest == null) {
            throw new RuntimeException("Invalid or expired reset link");
        }

        String token = passwordResetRequest.getToken();
        String newPassword = passwordResetRequest.getNewPassword();

        if (token == null || token.isBlank()) {
            throw new RuntimeException("Invalid or expired reset link");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("New password is required");
        }
        enforcePasswordPolicy(newPassword);

        User user = userRepository.findByResetToken(hashToken(token))
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset link"));

        if (user.getResetTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getResetTokenExpiry())) {
            throw new RuntimeException("Invalid or expired reset link");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Verification token is required");
        }

        User user = userRepository.findByVerificationToken(hashToken(token))
                .orElseThrow(() -> new RuntimeException("Invalid or expired verification link"));

        if (user.getVerificationTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
            throw new RuntimeException("Verification link has expired. Please sign up again.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    @Override
    public void changePassword(
        String accessToken,
        String currentPassword,
        String newPassword,
        String confirmPassword
    ) {
        if (accessToken == null || accessToken.isBlank()
                || currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            throw new RuntimeException("Current password, new password, and access token are required");
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("Password must be at least 8 characters");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match");
        }

        String email = resolveEmailFromAccessToken(accessToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String resolveEmailFromAccessToken(String accessToken) {
        try {
            if (jwtTokenUtil.isRefreshToken(accessToken)) {
                throw new RuntimeException("Invalid or expired session. Please sign in again.");
            }
            return jwtTokenUtil.extractUsername(accessToken);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invalid or expired session. Please sign in again.");
        }
    }

    private void enforcePasswordPolicy(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > 128) {
            throw new RuntimeException("Password must be at most 128 characters");
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash token", e);
        }
    }

    private String generateSecureToken() {
        byte[] tokenBytes = new byte[SECURE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String buildPasswordResetLink(String resetToken) {
        String baseUrl = frontendUrl == null ? "" : frontendUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/reset-password?rkey=" + resetToken;
    }
}
