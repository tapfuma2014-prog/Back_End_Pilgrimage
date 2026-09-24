package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.CreateNotificationRequest;
import com.pilgrimage.backend.dto.NotificationDto;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.service.NotificationService;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import com.pilgrimage.backend.util.SimpleRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final SimpleRateLimiter createRateLimiter =
        new SimpleRateLimiter(30, 60_000L, "Too many notification requests");

    public NotificationController(NotificationService notificationService, JdbcTemplate jdbcTemplate,
                                  UserRepository userRepository) {
        this.notificationService = notificationService;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<NotificationDto> listNotifications(@RequestParam(name = "userEmail", required = false) String userEmail) {
        String caller = requireAuthenticatedEmail();
        // Non-admin callers may only ever read their own notifications.
        String effectiveEmail = caller;
        if (userEmail != null && !userEmail.isBlank() && !userEmail.equalsIgnoreCase(caller)) {
            if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
            effectiveEmail = userEmail;
        }
        return notificationService.listByUserEmail(effectiveEmail);
    }

    @PostMapping
    public ResponseEntity<NotificationDto> createNotification(@RequestBody CreateNotificationRequest request) {
        String caller = requireAuthenticatedEmail();
        createRateLimiter.check(caller);
        if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
            // Non-admin callers cannot create notifications for other users or
            // spoof the created_by attribution.
            request.setUserEmail(caller);
            request.setCreatedBy(caller);
        }
        NotificationDto created = notificationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") String id) {
        String caller = requireAuthenticatedEmail();
        if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
            Integer owned = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE id = ? AND LOWER(user_email) = LOWER(?)",
                Integer.class,
                id,
                caller
            );
            if (owned == null || owned == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
        }
        jdbcTemplate.update(
            "UPDATE notifications SET is_read = true, updated_date = NOW() WHERE id = ?",
            id
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestParam(name = "userEmail", required = false) String userEmail) {
        String caller = requireAuthenticatedEmail();
        String effectiveEmail = caller;
        if (userEmail != null && !userEmail.isBlank() && !userEmail.equalsIgnoreCase(caller)) {
            if (!EntityAuthorizationHelper.isAdmin(userRepository)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
            effectiveEmail = userEmail;
        }
        jdbcTemplate.update(
            "UPDATE notifications SET is_read = true, updated_date = NOW() WHERE LOWER(user_email) = LOWER(?)",
            effectiveEmail
        );
        return ResponseEntity.ok().build();
    }

    private String requireAuthenticatedEmail() {
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return email;
    }
}
