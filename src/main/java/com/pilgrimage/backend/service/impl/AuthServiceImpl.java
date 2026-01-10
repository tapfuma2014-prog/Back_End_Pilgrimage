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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                          JwtTokenUtil jwtTokenUtil,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        try {
            // Authenticate the user with email and password
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail().toLowerCase().trim(),
                    loginRequest.getPassword()
                )
            );

            // Set the authentication in the security context
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Generate JWT token
            String jwt = jwtTokenUtil.generateToken(authentication.getName());
            
            // Get user details
            User user = userRepository.findByEmail(loginRequest.getEmail().toLowerCase().trim())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Return JWT response with user details
            return new JwtResponse(
                jwt, 
                user.getId().toString(), 
                user.getFullName(), 
                user.getEmail(),
                user.getRole()
            );
            
        } catch (Exception e) {
            // Log the error for debugging
            // In a production environment, you might want to use a proper logging framework
            System.err.println("Authentication error: " + e.getMessage());
            
            // Throw a more specific exception with a user-friendly message
            throw new RuntimeException("Invalid email or password");
        }
    }

    @Override
    public User registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        User user = new User();
        user.setFullName(registerRequest.getFullName());
        user.setEmail(registerRequest.getEmail().toLowerCase().trim());
        
        // Encode the password before saving
        String encodedPassword = passwordEncoder.encode(registerRequest.getPassword());
        user.setPassword(encodedPassword);
        
        // Set default role with ROLE_ prefix to match database constraint
        user.setRole("user");

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
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        
        // Generate reset token (in a real app, you would send this via email)
        String resetToken = UUID.randomUUID().toString();
        // In a real app, you would save this token with an expiry date in the database
    }

    @Override
    public void resetPassword(PasswordResetRequest passwordResetRequest) {
        // In a real app, you would validate the reset token here
        User user = userRepository.findByEmail(passwordResetRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        user.setPassword(passwordEncoder.encode(passwordResetRequest.getNewPassword()));
        userRepository.save(user);
    }
}
