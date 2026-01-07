package com.pilgrimage.backend.service;

import com.pilgrimage.backend.dto.JwtResponse;
import com.pilgrimage.backend.dto.LoginRequest;
import com.pilgrimage.backend.dto.PasswordResetRequest;
import com.pilgrimage.backend.dto.ProfileUpdateRequest;
import com.pilgrimage.backend.dto.RegisterRequest;
import com.pilgrimage.backend.model.User;

public interface AuthService {
    JwtResponse authenticateUser(LoginRequest loginRequest);
    User registerUser(RegisterRequest registerRequest);
    User getCurrentUser(String email);
    User updateProfile(String email, ProfileUpdateRequest profileUpdateRequest);
    void requestPasswordReset(String email);
    void resetPassword(PasswordResetRequest passwordResetRequest);
}
