package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public CartController(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @GetMapping("/count")
    public ResponseEntity<Integer> getCartCount(@RequestParam(name = "userEmail", required = false) String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.ok(0);
        }

        String currentUser = EntityAuthorizationHelper.currentUserEmail();
        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        if (currentUser == null) {
            return ResponseEntity.ok(0);
        }
        if (!isAdmin && !userEmail.equalsIgnoreCase(currentUser)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM cart WHERE LOWER(created_by) = LOWER(?)",
            Integer.class,
            userEmail.toLowerCase()
        );

        return ResponseEntity.ok(count != null ? count : 0);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCartItem(@PathVariable Long id) {
        requireCartOwnership(id);
        jdbcTemplate.update("DELETE FROM cart WHERE id = ?", id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCartItem(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        requireCartOwnership(id);
        if (updates != null && updates.containsKey("quantity")) {
            Object raw = updates.get("quantity");
            int quantity;
            if (raw instanceof Number number) {
                quantity = number.intValue();
            } else {
                try {
                    quantity = Integer.parseInt(String.valueOf(raw));
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be a number");
                }
            }
            if (quantity < 1 || quantity > 9999) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be between 1 and 9999");
            }
            jdbcTemplate.update("UPDATE cart SET quantity = ? WHERE id = ?", quantity, id);
        }
        return ResponseEntity.ok().build();
    }

    private void requireCartOwnership(Long id) {
        String currentUser = EntityAuthorizationHelper.currentUserEmail();
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (EntityAuthorizationHelper.isAdmin(userRepository)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cart WHERE id = ? AND LOWER(created_by) = LOWER(?)",
            Integer.class,
            id,
            currentUser
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
