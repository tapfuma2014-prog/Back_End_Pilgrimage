package com.pilgrimage.backend.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auctions")
public class AuctionController {

    private final JdbcTemplate jdbcTemplate;

    public AuctionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Map<String, Object>> listAuctions() {
        String sql = """
            SELECT id,
                   title,
                   description,
                   start_time AS start_date,
                   end_time AS end_date,
                   status,
                   total_bids,
                   created_date
            FROM auctions
            ORDER BY start_time DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getString("id"));
            map.put("title", rs.getString("title"));
            map.put("description", rs.getString("description"));
            map.put("start_date", rs.getTimestamp("start_date"));
            map.put("end_date", rs.getTimestamp("end_date"));
            map.put("status", rs.getString("status"));
            map.put("total_bids", rs.getInt("total_bids"));
            map.put("created_date", rs.getTimestamp("created_date"));
            return map;
        });
    }
}
