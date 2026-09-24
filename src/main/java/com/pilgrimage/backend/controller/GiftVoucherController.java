package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gift-vouchers")
public class GiftVoucherController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public GiftVoucherController(JdbcTemplate jdbcTemplate, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listVouchers(@RequestParam(name = "status", required = false) String status,
                                                  @RequestParam(name = "recipientEmail", required = false) String recipientEmail) {
        EntityAuthorizationHelper.ensureAdmin(userRepository);
        String sql = """
            SELECT id,
                   voucher_code,
                   voucher_type,
                   experience_type,
                   purchaser_name,
                   purchaser_email,
                   recipient_name,
                   recipient_email,
                   purchase_price,
                   expiry_date,
                   status,
                   created_date
            FROM gift_voucher
        """;

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (recipientEmail != null && !recipientEmail.isBlank()) {
            conditions.add("LOWER(recipient_email) = LOWER(?)");
            params.add(recipientEmail);
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        sql += " ORDER BY created_date DESC";

        return params.isEmpty()
            ? jdbcTemplate.query(sql, (rs, rowNum) -> toVoucherMap(rs))
            : jdbcTemplate.query(sql, (rs, rowNum) -> toVoucherMap(rs), params.toArray());
    }

    @GetMapping("/validate")
    public Map<String, Object> validateVoucher(@RequestParam(name = "code") String code) {
        EntityAuthorizationHelper.ensureAdmin(userRepository);
        String sql = """
            SELECT id,
                   voucher_code,
                   voucher_type,
                   experience_type,
                   purchase_price,
                   expiry_date,
                   status
            FROM gift_voucher
            WHERE voucher_code = ?
        """;

        List<Map<String, Object>> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getString("id"));
            map.put("voucher_code", rs.getString("voucher_code"));
            map.put("voucher_type", rs.getString("voucher_type"));
            map.put("experience_type", rs.getString("experience_type"));
            map.put("purchase_price", rs.getObject("purchase_price"));
            map.put("expiry_date", rs.getString("expiry_date"));
            map.put("status", rs.getString("status"));
            return map;
        }, code);

        if (results.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("valid", false);
            error.put("message", "Voucher not found");
            return error;
        }

        Map<String, Object> voucher = results.get(0);
        String voucherStatus = (String) voucher.get("status");
        
        if (!"active".equalsIgnoreCase(voucherStatus)) {
            voucher.put("valid", false);
            voucher.put("message", "Voucher is not active");
        } else {
            voucher.put("valid", true);
            voucher.put("message", "Voucher is valid");
        }
        
        return voucher;
    }

    private Map<String, Object> toVoucherMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getString("id"));
        map.put("voucher_code", rs.getString("voucher_code"));
        map.put("voucher_type", rs.getString("voucher_type"));
        map.put("experience_type", rs.getString("experience_type"));
        map.put("purchaser_name", rs.getString("purchaser_name"));
        map.put("purchaser_email", rs.getString("purchaser_email"));
        map.put("recipient_name", rs.getString("recipient_name"));
        map.put("recipient_email", rs.getString("recipient_email"));
        map.put("purchase_price", rs.getObject("purchase_price"));
        map.put("expiry_date", rs.getString("expiry_date"));
        map.put("status", rs.getString("status"));
        map.put("created_date", rs.getTimestamp("created_date"));
        return map;
    }
}
