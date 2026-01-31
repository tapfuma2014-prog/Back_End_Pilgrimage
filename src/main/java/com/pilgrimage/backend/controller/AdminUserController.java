package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.UserAdminDto;
import com.pilgrimage.backend.dto.UserRoleUpdateRequest;
import com.pilgrimage.backend.model.User;
import com.pilgrimage.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserAdminDto> listUsers() {
        ensureAdmin();
        return userRepository.findAll()
                .stream()
                .map(UserAdminDto::fromUser)
                .toList();
    }

    @PutMapping("/{id}/role")
    public UserAdminDto updateUserRole(@PathVariable String id, @RequestBody UserRoleUpdateRequest request) {
        ensureAdmin();
        String normalizedRole = normalizeRole(request == null ? null : request.getRole());
        if (normalizedRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be 'admin' or 'user'");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setRole(normalizedRole);
        User saved = userRepository.save(user);
        return UserAdminDto.fromUser(saved);
    }

    private void ensureAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String normalized = role.toLowerCase().trim();
        if ("admin".equals(normalized) || "user".equals(normalized)) {
            return normalized;
        }
        return null;
    }
}
