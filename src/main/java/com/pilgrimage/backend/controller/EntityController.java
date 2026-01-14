package com.pilgrimage.backend.controller;

import com.pilgrimage.backend.dto.EntityFilterRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/entities")
public class EntityController {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();

    public EntityController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @GetMapping("/{entity}")
    public List<Map<String, Object>> list(
        @PathVariable("entity") String entity,
        @RequestParam(name = "sort", required = false) String sort,
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM " + table;
        sql += buildOrderBy(sort, table);
        if (limit != null && limit > 0) {
            sql += " LIMIT " + limit;
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRow(rs));
    }

    @PostMapping("/{entity}/filter")
    public List<Map<String, Object>> filter(
        @PathVariable("entity") String entity,
        @RequestBody EntityFilterRequest request
    ) {
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyList();
        }

        Map<String, Object> filters = request.getFilters() != null ? request.getFilters() : Collections.emptyMap();
        Set<String> allowedColumns = getTableColumns(table);

        StringBuilder sql = new StringBuilder("SELECT * FROM " + table);
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String column = entry.getKey();
            if (!allowedColumns.contains(column)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                if (mapValue.containsKey("id")) {
                    value = mapValue.get("id");
                } else if (mapValue.containsKey("value")) {
                    value = mapValue.get("value");
                } else {
                    value = mapValue.toString();
                }
            }
            String paramName = column.replaceAll("[^a-zA-Z0-9_]", "");
            if (value instanceof Collection<?> collection) {
                if (!collection.isEmpty()) {
                    clauses.add(column + " IN (:" + paramName + ")");
                    params.addValue(paramName, collection);
                }
            } else {
                clauses.add(column + " = :" + paramName);
                params.addValue(paramName, value);
            }
        }

        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }

        sql.append(buildOrderBy(request.getSort(), table));
        if (request.getLimit() != null && request.getLimit() > 0) {
            sql.append(" LIMIT ").append(request.getLimit());
        }

        return namedJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> mapRow(rs));
    }

    @PostMapping("/{entity}")
    public Map<String, Object> create(
        @PathVariable("entity") String entity,
        @RequestBody Map<String, Object> payload
    ) {
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> data = new LinkedHashMap<>(payload != null ? payload : Collections.emptyMap());
        String username = resolveAuthenticatedUser();
        if (table.endsWith(".cart") || table.equals("cart")) {
            Object createdBy = data.get("created_by");
            String createdByValue = createdBy != null ? createdBy.toString().trim() : "";
            if (!createdByValue.contains("@") && username != null) {
                data.put("created_by", username.toLowerCase());
            } else if (!createdByValue.isEmpty()) {
                data.put("created_by", createdByValue.toLowerCase());
            }
        }
        if (!data.containsKey("id")) {
            data.put("id", UUID.randomUUID().toString());
        }

        Set<String> allowedColumns = getTableColumns(table);
        Map<String, Object> filtered = filterAllowedColumns(data, allowedColumns);

        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }

        String columns = String.join(", ", filtered.keySet());
        String values = String.join(", ", filtered.keySet().stream().map(k -> ":" + k).toList());
        String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ")";

        MapSqlParameterSource params = new MapSqlParameterSource(filtered);
        namedJdbcTemplate.update(sql, params);

        return jdbcTemplate.queryForMap("SELECT * FROM " + table + " WHERE id = ?", filtered.get("id"));
    }

    @PutMapping("/{entity}/{id}")
    public Map<String, Object> update(
        @PathVariable("entity") String entity,
        @PathVariable("id") String id,
        @RequestBody Map<String, Object> payload
    ) {
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyMap();
        }

        Set<String> allowedColumns = getTableColumns(table);
        Map<String, Object> filtered = filterAllowedColumns(payload, allowedColumns);
        filtered.remove("id");

        if (filtered.isEmpty()) {
            return jdbcTemplate.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
        }

        StringBuilder setClause = new StringBuilder();
        List<String> assignments = new ArrayList<>();
        for (String key : filtered.keySet()) {
            assignments.add(key + " = :" + key);
        }
        setClause.append(String.join(", ", assignments));

        String sql = "UPDATE " + table + " SET " + setClause + " WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource(filtered);
        params.addValue("id", id);
        namedJdbcTemplate.update(sql, params);

        return jdbcTemplate.queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
    }

    @DeleteMapping("/{entity}/{id}")
    public void delete(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        String table = resolveTable(entity);
        if (table == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", id);
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String column = metaData.getColumnLabel(i);
            Object value = rs.getObject(i);
            if (value instanceof java.sql.Array sqlArray) {
                Object arrayValue = sqlArray.getArray();
                if (arrayValue instanceof Object[] objectArray) {
                    value = Arrays.asList(objectArray);
                } else {
                    value = arrayValue;
                }
            }
            row.put(column, value);
        }
        return row;
    }

    private Map<String, Object> filterAllowedColumns(Map<String, Object> payload, Set<String> allowedColumns) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (payload == null) {
            return filtered;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (allowedColumns.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private String buildOrderBy(String sort, String table) {
        if (sort == null || sort.isBlank()) {
            return "";
        }
        String trimmed = sort.trim();
        boolean desc = trimmed.startsWith("-");
        String column = desc ? trimmed.substring(1) : trimmed;
        if (!getTableColumns(table).contains(column)) {
            return "";
        }
        return " ORDER BY " + column + (desc ? " DESC" : " ASC");
    }

    private String resolveTable(String entity) {
        if (entity == null || !entity.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        String snake = toSnakeCase(entity);
        if ("cart".equals(snake)) {
            return findTable("cart");
        }
        String exact = findTable(snake);
        if (exact != null) {
            return exact;
        }
        String plural = findTable(snake + "s");
        if (plural != null) {
            return plural;
        }
        if (snake.endsWith("y")) {
            String ies = findTable(snake.substring(0, snake.length() - 1) + "ies");
            if (ies != null) {
                return ies;
            }
        }
        return null;
    }

    private String resolveAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if ("anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        return name;
    }

    private String findTable(String candidate) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
            Integer.class,
            candidate
        );
        if (count != null && count > 0) {
            return candidate;
        }
        return null;
    }

    private Set<String> getTableColumns(String table) {
        return tableColumnsCache.computeIfAbsent(table, key -> {
            List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String.class,
                key
            );
            return new HashSet<>(columns);
        });
    }

    private String toSnakeCase(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }
}
