package com.pilgrimage.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArtworkColumnMigration {

    private final JdbcTemplate jdbcTemplate;

    public ArtworkColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("ALTER TABLE artwork ADD COLUMN IF NOT EXISTS status text DEFAULT 'approved'");
        jdbcTemplate.execute("ALTER TABLE artwork ADD COLUMN IF NOT EXISTS rejection_reason text");
        jdbcTemplate.execute("ALTER TABLE artwork ADD COLUMN IF NOT EXISTS reviewed_date timestamptz");
        
        // Fix QA Issue #18: Update Bamboo Whispers image URL
        jdbcTemplate.update(
            "UPDATE artwork SET image_url = ? WHERE id = ? AND title = ?",
            "https://images.unsplash.com/photo-1518495973542-4542c06a5843?w=800",
            "692dd50e60eef538ade0794b",
            "Bamboo Whispers"
        );
    }
}
