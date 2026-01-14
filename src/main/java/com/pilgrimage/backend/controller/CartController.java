package com.pilgrimage.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final JdbcTemplate jdbcTemplate;

    public CartController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/count")
    public ResponseEntity<Integer> getCartCount(@RequestParam(name = "userEmail", required = false) String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.ok(0);
        }

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM cart WHERE created_by = ?",
            Integer.class,
            userEmail
        );

        return ResponseEntity.ok(count != null ? count : 0);
    }
}
