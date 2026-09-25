package com.pilgrimage.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pilgrimage.backend.dto.EntityFilterRequest;
import com.pilgrimage.backend.repository.UserRepository;
import com.pilgrimage.backend.service.XperiencesService;
import com.pilgrimage.backend.util.Class53EntityMapper;
import com.pilgrimage.backend.util.CrmEntityMapper;
import com.pilgrimage.backend.util.EntityAuthorizationHelper;
import com.pilgrimage.backend.util.EnquiryValidationHelper;
import com.pilgrimage.backend.util.NotificationEntityMapper;
import com.pilgrimage.backend.util.VoteEntityMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/entities")
public class EntityController {

    private static final ObjectMapper JSON = new ObjectMapper();
    // Server-side ceiling for client-supplied LIMIT to prevent unbounded scans.
    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final UserRepository userRepository;
    private final XperiencesService xperiencesService;
    private final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ColumnType>> tableColumnTypesCache = new ConcurrentHashMap<>();

    public EntityController(
        JdbcTemplate jdbcTemplate,
        UserRepository userRepository,
        XperiencesService xperiencesService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.userRepository = userRepository;
        this.xperiencesService = xperiencesService;
    }

    @GetMapping("/{entity}")
    public List<Map<String, Object>> list(
        @PathVariable("entity") String entity,
        @RequestParam(name = "sort", required = false) String sort,
        @RequestParam(name = "limit", required = false) Integer limit
    ) {
        EntityAuthorizationHelper.ensureReadAccess(entity, userRepository);
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyList();
        }

        EntityAuthorizationHelper.ensureArtworkQueryAccess(entity, Collections.emptyMap(), userRepository);

        StringBuilder sql = new StringBuilder("SELECT * FROM " + table);
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();
        appendArtworkVisibilityFilter(entity, table, params, clauses);
        appendOwnershipFilter(entity, table, params, clauses);
        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }
        sql.append(buildOrderBy(entity, sort, table));
        if (limit != null && limit > 0) {
            sql.append(" LIMIT ").append(Math.min(limit, MAX_LIMIT));
        }

        List<Map<String, Object>> rows = namedJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> mapEntityRow(entity, mapRow(rs)));
        
        if ("Auction".equals(entity)) {
            for (Map<String, Object> row : rows) {
                Object featuredArtworks = row.get("featured_artworks");
                if (featuredArtworks instanceof List<?> list && list.isEmpty()) {
                    String auctionId = row.get("id") != null ? row.get("id").toString() : null;
                    if (auctionId != null) {
                        String artworkIdsSql = """
                            SELECT DISTINCT 
                                CASE 
                                    WHEN ab.artwork_id LIKE 'sample-%' THEN SUBSTRING(ab.artwork_id FROM 8)
                                    ELSE ab.artwork_id
                                END as artwork_id
                            FROM auction_bid ab
                            WHERE ab.auction_id = ?
                            AND ab.artwork_id IS NOT NULL
                        """;
                        List<String> artworkIds = jdbcTemplate.queryForList(artworkIdsSql, String.class, auctionId);
                        row.put("featured_artworks", artworkIds);
                    }
                }
            }
        }
        
        if (!EntityAuthorizationHelper.isPublicRead(entity)) {
            return rows;
        }
        return rows.stream().map(EntityAuthorizationHelper::sanitizePublicCatalogRow).toList();
    }

    @PostMapping("/{entity}/filter")
    public List<Map<String, Object>> filter(
        @PathVariable("entity") String entity,
        @RequestBody EntityFilterRequest request
    ) {
        EntityAuthorizationHelper.ensureReadAccess(entity, userRepository);
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyList();
        }

        Map<String, Object> filters = request.getFilters() != null ? request.getFilters() : Collections.emptyMap();
        EntityAuthorizationHelper.ensureArtworkQueryAccess(entity, filters, userRepository);
        Set<String> allowedColumns = getTableColumns(table);

        StringBuilder sql = new StringBuilder("SELECT * FROM " + table);
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> clauses = new ArrayList<>();

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String originalColumn = entry.getKey();
            String column = mapEntityFilterColumn(entity, originalColumn);
            if (!allowedColumns.contains(column)) {
                continue;
            }
            Object value = mapEntityFilterValue(entity, originalColumn, entry.getValue());
            if (value instanceof Map<?, ?> mapValue) {
                if (mapValue.containsKey("id")) {
                    value = mapValue.get("id");
                } else if (mapValue.containsKey("value")) {
                    value = mapValue.get("value");
                } else {
                    // Unmappable object filter - skip rather than comparing
                    // against a toString() blob.
                    continue;
                }
            }
            String paramName = column.replaceAll("[^a-zA-Z0-9_]", "");
            if (value instanceof Collection<?> collection) {
                if (!collection.isEmpty()) {
                    clauses.add(column + " IN (:" + paramName + ")");
                    params.addValue(paramName, collection);
                }
            } else {
                if (value instanceof String text && isCaseInsensitiveColumn(column)) {
                    clauses.add("LOWER(" + column + ") = :" + paramName);
                    params.addValue(paramName, text.toLowerCase());
                } else {
                    clauses.add(column + " = :" + paramName);
                    params.addValue(paramName, value);
                }
            }
        }

        appendArtworkVisibilityFilter(entity, table, params, clauses);
        appendOwnershipFilter(entity, table, params, clauses);

        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }

        sql.append(buildOrderBy(entity, request.getSort(), table));
        if (request.getLimit() != null && request.getLimit() > 0) {
            sql.append(" LIMIT ").append(Math.min(request.getLimit(), MAX_LIMIT));
        }

        List<Map<String, Object>> rows = namedJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> mapEntityRow(entity, mapRow(rs)));
        if (!EntityAuthorizationHelper.isPublicRead(entity)) {
            return rows;
        }
        return rows.stream().map(EntityAuthorizationHelper::sanitizePublicCatalogRow).toList();
    }

    @PostMapping("/{entity}")
    public Map<String, Object> create(
        @PathVariable("entity") String entity,
        @RequestBody Map<String, Object> payload
    ) {
        EntityAuthorizationHelper.ensureWriteAccess(entity, userRepository);
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> data = new LinkedHashMap<>(payload != null ? payload : Collections.emptyMap());
        if ("GardenBooking".equals(entity)) {
            EnquiryValidationHelper.validateGardenBookingPayload(data);
        }
        if ("User".equals(entity)) {
            data.remove("password");
            data.remove("reset_token");
            data.remove("reset_token_expiry");
            data.remove("verification_token");
            data.remove("verification_token_expiry");
        }
        if (Class53EntityMapper.isClass53Entity(entity)) {
            data = Class53EntityMapper.toDbPayload(entity, data);
        }
        if (CrmEntityMapper.isCrmEntity(entity)) {
            data = CrmEntityMapper.toDbPayload(entity, data);
        }
        if (VoteEntityMapper.isVoteEntity(entity)) {
            data = VoteEntityMapper.toDbPayload(data, userRepository);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            data = NotificationEntityMapper.toDbPayload(entity, data, userRepository);
        }

        String username = resolveAuthenticatedUser();
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        Set<String> allowedColumns = getTableColumns(table);

        applyAuthenticatedDefaults(entity, table, data, username);
        if (allowedColumns.contains("created_by")) {
            if (!isAdmin) {
                if (EntityAuthorizationHelper.isUserOwned(entity)
                    || "Artwork".equals(entity)
                    || "Artist".equals(entity)) {
                    data.put("created_by", username.toLowerCase());
                } else if (EntityAuthorizationHelper.isPublicRead(entity)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
                }
            } else if (!data.containsKey("created_by") || data.get("created_by") == null) {
                data.put("created_by", username.toLowerCase());
            }
        }

        if (!data.containsKey("id")) {
            data.put("id", UUID.randomUUID().toString());
        }

        Map<String, Object> filtered = filterAllowedColumns(data, allowedColumns);

        if (filtered.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> columns = new ArrayList<>(filtered.keySet());
        String values = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" + values + ")";

        Map<String, ColumnType> columnTypes = getTableColumnTypes(table);
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql);
            bindValues(statement, connection, columns, filtered, columnTypes);
            return statement;
        });

        return fetchEntityById(entity, table, filtered.get("id"));
    }

    @PutMapping("/{entity}/{id}")
    public Map<String, Object> update(
        @PathVariable("entity") String entity,
        @PathVariable("id") String id,
        @RequestBody Map<String, Object> payload
    ) {
        EntityAuthorizationHelper.ensureWriteAccess(entity, userRepository);
        String table = resolveTable(entity);
        if (table == null) {
            return Collections.emptyMap();
        }

        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        if (!isAdmin) {
            requireOwnershipOrAdmin(entity, table, id);
        }

        String previousStatus = null;
        if ("PortraitCommission".equals(entity)
            && payload != null
            && "completed".equalsIgnoreCase(String.valueOf(payload.get("status")))) {
            previousStatus = jdbcTemplate.query(
                "SELECT status FROM " + table + " WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"),
                id
            ).stream().findFirst().orElse(null);
        }

        Map<String, Object> mappedPayload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
        if ("User".equals(entity)) {
            mappedPayload.remove("password");
            mappedPayload.remove("reset_token");
            mappedPayload.remove("reset_token_expiry");
            mappedPayload.remove("verification_token");
            mappedPayload.remove("verification_token_expiry");
        }
        if (Class53EntityMapper.isClass53Entity(entity)) {
            mappedPayload = Class53EntityMapper.toDbPayload(entity, mappedPayload);
        }
        if (CrmEntityMapper.isCrmEntity(entity)) {
            mappedPayload = CrmEntityMapper.toDbPayload(entity, mappedPayload);
        }
        if (VoteEntityMapper.isVoteEntity(entity)) {
            mappedPayload = VoteEntityMapper.toDbPayload(mappedPayload, userRepository);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            mappedPayload = NotificationEntityMapper.toDbPayload(entity, mappedPayload, userRepository);
        }

        mappedPayload.remove("created_by");

        Set<String> allowedColumns = getTableColumns(table);
        Map<String, Object> filtered = filterAllowedColumns(mappedPayload, allowedColumns);
        filtered.remove("id");

        if (filtered.isEmpty()) {
            return fetchEntityById(entity, table, id);
        }

        List<String> columns = new ArrayList<>(filtered.keySet());
        String assignments = String.join(", ", columns.stream().map(key -> key + " = ?").toList());
        String sql = "UPDATE " + table + " SET " + assignments + " WHERE id = ?";

        Map<String, ColumnType> columnTypes = getTableColumnTypes(table);
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql);
            bindValues(statement, connection, columns, filtered, columnTypes);
            statement.setObject(columns.size() + 1, id);
            return statement;
        });

        if ("PortraitCommission".equals(entity)
            && "completed".equalsIgnoreCase(String.valueOf(filtered.get("status")))
            && !"completed".equalsIgnoreCase(previousStatus)) {
            xperiencesService.notifyPaintingCompleted(id);
        }

        return fetchEntityById(entity, table, id);
    }

    @DeleteMapping("/{entity}/{id}")
    public ResponseEntity<Void> delete(@PathVariable("entity") String entity, @PathVariable("id") String id) {
        EntityAuthorizationHelper.ensureWriteAccess(entity, userRepository);
        String table = resolveTable(entity);
        if (table == null) {
            return ResponseEntity.noContent().build();
        }

        boolean isAdmin = EntityAuthorizationHelper.isAdmin(userRepository);
        if (!isAdmin) {
            requireOwnershipOrAdmin(entity, table, id);
        }

        jdbcTemplate.update("DELETE FROM " + table + " WHERE id = ?", id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String column = metaData.getColumnLabel(i);
            String columnType = metaData.getColumnTypeName(i);
            Object value = normalizeColumnValue(rs, i, columnType);
            row.put(column, value);
        }
        return row;
    }

    private Object normalizeColumnValue(java.sql.ResultSet rs, int index, String columnType)
        throws java.sql.SQLException {
        if ("json".equalsIgnoreCase(columnType) || "jsonb".equalsIgnoreCase(columnType)) {
            String json = rs.getString(index);
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return JSON.readValue(json, Object.class);
            } catch (Exception ignored) {
                return json;
            }
        }
        Object value = rs.getObject(index);
        if (value instanceof java.sql.Array sqlArray) {
            Object arrayValue = sqlArray.getArray();
            if (arrayValue instanceof Object[] objectArray) {
                return Arrays.asList(objectArray);
            }
            return arrayValue;
        }
        return value;
    }

    private Map<String, Object> fetchEntityById(String entity, String table, Object id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM " + table + " WHERE id = ?",
            (rs, rowNum) -> mapEntityRow(entity, mapRow(rs)),
            id
        );
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.get(0);
    }

    private Map<String, Object> mapEntityRow(String entity, Map<String, Object> row) {
        Map<String, Object> sanitized = EntityAuthorizationHelper.sanitizeUserRow(entity, row);
        if (EntityAuthorizationHelper.isPublicRead(entity)) {
            sanitized = EntityAuthorizationHelper.sanitizePublicCatalogRow(sanitized);
        }
        if (Class53EntityMapper.isClass53Entity(entity)) {
            return Class53EntityMapper.toApiRow(entity, sanitized);
        }
        if (CrmEntityMapper.isCrmEntity(entity)) {
            return CrmEntityMapper.toApiRow(entity, sanitized);
        }
        if (VoteEntityMapper.isVoteEntity(entity)) {
            return VoteEntityMapper.toApiRow(sanitized, userRepository);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            return NotificationEntityMapper.toApiRow(entity, sanitized, userRepository);
        }
        return sanitized;
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

    private String buildOrderBy(String entity, String sort, String table) {
        if (sort == null || sort.isBlank()) {
            return "";
        }
        String trimmed = sort.trim();
        boolean desc = trimmed.startsWith("-");
        String column = desc ? trimmed.substring(1) : trimmed;
        column = mapEntitySortColumn(entity, column);
        if (!getTableColumns(table).contains(column)) {
            return "";
        }
        return " ORDER BY " + column + (desc ? " DESC" : " ASC");
    }

    private String mapEntityFilterColumn(String entity, String column) {
        if (VoteEntityMapper.isVoteEntity(entity)) {
            return VoteEntityMapper.mapFilterColumn(column);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            return NotificationEntityMapper.mapFilterColumn(entity, column);
        }
        if ("Artwork".equals(entity) && "artist_user_email".equals(column)) {
            return "created_by";
        }
        if (Class53EntityMapper.isClass53Entity(entity)) {
            return Class53EntityMapper.mapFilterColumn(entity, column);
        }
        return CrmEntityMapper.mapFilterColumn(entity, column);
    }

    private Object mapEntityFilterValue(String entity, String originalColumn, Object value) {
        if (VoteEntityMapper.isVoteEntity(entity)) {
            return VoteEntityMapper.mapFilterValue(originalColumn, value, userRepository);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            return NotificationEntityMapper.mapFilterValue(entity, originalColumn, value, userRepository);
        }
        return value;
    }

    private String mapEntitySortColumn(String entity, String column) {
        if (VoteEntityMapper.isVoteEntity(entity)) {
            return VoteEntityMapper.mapSortColumn(column);
        }
        if (NotificationEntityMapper.isNotificationEntity(entity)
            || NotificationEntityMapper.isNotificationPreferenceEntity(entity)) {
            return NotificationEntityMapper.mapSortColumn(entity, column);
        }
        if (Class53EntityMapper.isClass53Entity(entity)) {
            return Class53EntityMapper.mapSortColumn(entity, column);
        }
        return CrmEntityMapper.mapSortColumn(entity, column);
    }

    private void applyAuthenticatedDefaults(
        String entity,
        String table,
        Map<String, Object> data,
        String username
    ) {
        if (username == null || username.isBlank()) {
            return;
        }
        String email = username.toLowerCase();
        if ("wishlist".equals(table)) {
            if (!data.containsKey("created_by") || data.get("created_by") == null
                || data.get("created_by").toString().isBlank()) {
                data.put("created_by", email);
            }
        }
        if ("CollectionLike".equals(entity) && "collection_likes".equals(table)) {
            if (!data.containsKey("liker_email") || data.get("liker_email") == null
                || data.get("liker_email").toString().isBlank()) {
                data.put("liker_email", email);
            }
        }
        if ("ArtworkVote".equals(entity) && "artwork_votes".equals(table)) {
            if (!data.containsKey("voter_email") || data.get("voter_email") == null
                || data.get("voter_email").toString().isBlank()) {
                data.put("voter_email", email);
            }
            if (!data.containsKey("vote_type") || data.get("vote_type") == null
                || data.get("vote_type").toString().isBlank()) {
                data.put("vote_type", "up");
            }
        }
        if ("Artwork".equals(entity) && "artwork".equals(table)) {
            if (!data.containsKey("created_by") || data.get("created_by") == null
                || data.get("created_by").toString().isBlank()) {
                data.put("created_by", email);
            }
        }
    }

    private static final Set<String> COMMUNITY_PLURAL_PREFERRED = Set.of(
        "discussion_comment",
        "artist_message",
        "artist_follow",
        "project_invitation",
        "project_comment"
    );

    private static final Set<String> STUB_PLURAL_PREFERRED = Set.of(
        "notification",
        "notification_preference",
        "artist_request",
        "artwork_vote",
        "vote_category",
        "vote",
        "collection",
        "collection_like",
        "user_gallery",
        "ai_art_listing",
        "premium_subscription",
        "art_rover_route",
        "art_rover_tour",
        "art_rover_booking",
        "workshop_waitlist",
        "garden",
        "class53_affiliate",
        "affiliate_referral"
    );

    private boolean isCommunityPluralPreferred(String snake) {
        return COMMUNITY_PLURAL_PREFERRED.contains(snake);
    }

    private boolean isStubPluralPreferred(String snake) {
        return STUB_PLURAL_PREFERRED.contains(snake);
    }

    private String resolveTable(String entity) {
        if (entity == null || !entity.matches("[A-Za-z0-9_]+")) {
            return null;
        }
        String snake = toSnakeCase(entity);
        if ("cart".equals(snake)) {
            return findTable("cart");
        }
        if ("order".equals(snake)) {
            String ordersTable = findTable("orders");
            if (ordersTable != null) {
                return ordersTable;
            }
        }
        if ((snake.startsWith("crm_") || snake.endsWith("_booking")) && !snake.endsWith("s")) {
            String pluralTable = findTable(snake + "s");
            if (pluralTable != null) {
                return pluralTable;
            }
        }
        if (isCommunityPluralPreferred(snake)) {
            String pluralTable = findTable(snake + "s");
            if (pluralTable != null) {
                return pluralTable;
            }
        }
        if ("artist".equals(snake)) {
            String artistsTable = findTable("artists");
            if (artistsTable != null) {
                Set<String> singularColumns = getTableColumns(snake);
                if (singularColumns != null && !singularColumns.contains("user_email")) {
                    return artistsTable;
                }
            }
        }
        if ("discussion".equals(snake)) {
            String discussionsTable = findTable("discussions");
            if (discussionsTable != null) {
                Set<String> singularColumns = getTableColumns(snake);
                if (singularColumns != null && !singularColumns.contains("author_email")) {
                    return discussionsTable;
                }
            }
        }
        if (isStubPluralPreferred(snake)) {
            String pluralTable = findTable(snake + "s");
            if (pluralTable != null) {
                return pluralTable;
            }
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

    private boolean isCaseInsensitiveColumn(String column) {
        if (column == null) {
            return false;
        }
        String normalized = column.toLowerCase();
        return normalized.equals("created_by")
            || normalized.equals("user_email")
            || normalized.endsWith("_email");
    }

    private Map<String, ColumnType> getTableColumnTypes(String table) {
        return tableColumnTypesCache.computeIfAbsent(table, key -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, udt_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                key
            );
            Map<String, ColumnType> types = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String column = Objects.toString(row.get("column_name"), "");
                String dataType = Objects.toString(row.get("data_type"), "");
                String udtName = Objects.toString(row.get("udt_name"), "");
                types.put(column, new ColumnType(dataType, udtName));
            }
            return types;
        });
    }

    private void bindValues(
        java.sql.PreparedStatement statement,
        Connection connection,
        List<String> columns,
        Map<String, Object> values,
        Map<String, ColumnType> columnTypes
    ) throws java.sql.SQLException {
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            Object value = values.get(column);
            ColumnType columnType = columnTypes.get(column);
            if (value instanceof Collection<?> collection) {
                Object boundValue = coerceCollectionValue(connection, columnType, collection);
                statement.setObject(i + 1, boundValue);
            } else {
                statement.setObject(i + 1, value);
            }
        }
    }

    private Object coerceCollectionValue(
        Connection connection,
        ColumnType columnType,
        Collection<?> collection
    ) throws java.sql.SQLException {
        if (collection == null) {
            return null;
        }
        if (columnType != null && "ARRAY".equalsIgnoreCase(columnType.dataType)) {
            String elementType = columnType.elementType();
            Object[] elements = collection.toArray();
            return connection.createArrayOf(elementType, elements);
        }
        if (collection.isEmpty()) {
            return null;
        }
        return String.join(", ", collection.stream().map(String::valueOf).toList());
    }

    private static final class ColumnType {
        private final String dataType;
        private final String udtName;

        private ColumnType(String dataType, String udtName) {
            this.dataType = dataType;
            this.udtName = udtName;
        }

        private String elementType() {
            if (udtName == null || udtName.isBlank()) {
                return "text";
            }
            if (udtName.startsWith("_") && udtName.length() > 1) {
                return udtName.substring(1);
            }
            return udtName;
        }
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

    private String findOwnerColumn(String table) {
        Set<String> columns = getTableColumns(table);
        List<String> candidates = List.of(
            "created_by", "user_email", "voter_email", "bidder_email",
            "buyer_email", "artist_email", "organiser_email", "email"
        );
        for (String candidate : candidates) {
            if (columns.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void appendArtworkVisibilityFilter(
        String entity,
        String table,
        MapSqlParameterSource params,
        List<String> clauses
    ) {
        if (!"Artwork".equals(entity) || EntityAuthorizationHelper.isAdmin(userRepository)) {
            return;
        }
        Set<String> columns = getTableColumns(table);
        if (!columns.contains("status")) {
            return;
        }
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            clauses.add("status = 'approved'");
            return;
        }
        String ownerColumn = findOwnerColumn(table);
        if (ownerColumn != null) {
            clauses.add("(status = 'approved' OR LOWER(" + ownerColumn + ") = :artwork_owner_email)");
            params.addValue("artwork_owner_email", email.toLowerCase());
            return;
        }
        clauses.add("status = 'approved'");
    }

    private void appendOwnershipFilter(String entity, String table, MapSqlParameterSource params, List<String> clauses) {
        if (EntityAuthorizationHelper.isAdmin(userRepository) || !EntityAuthorizationHelper.isUserOwned(entity)) {
            return;
        }
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String ownerColumn = findOwnerColumn(table);
        if (ownerColumn == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ownership cannot be verified");
        }
        clauses.add("LOWER(" + ownerColumn + ") = :owner_email");
        params.addValue("owner_email", email.toLowerCase());
    }

    private void appendOwnershipFilter(String entity, String table, MapSqlParameterSource params, StringBuilder sql, String keyword) {
        if (EntityAuthorizationHelper.isAdmin(userRepository) || !EntityAuthorizationHelper.isUserOwned(entity)) {
            return;
        }
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String ownerColumn = findOwnerColumn(table);
        if (ownerColumn == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ownership cannot be verified");
        }
        sql.append(" ").append(keyword).append(" LOWER(").append(ownerColumn).append(") = :owner_email");
        params.addValue("owner_email", email.toLowerCase());
    }

    private void requireOwnershipOrAdmin(String entity, String table, String id) {
        if (EntityAuthorizationHelper.isAdmin(userRepository)) {
            return;
        }
        String email = EntityAuthorizationHelper.currentUserEmail();
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!EntityAuthorizationHelper.isUserOwned(entity)
            && !"Artwork".equals(entity)
            && !"Artist".equals(entity)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        String ownerColumn = findOwnerColumn(table);
        if (ownerColumn == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ownership cannot be verified");
        }
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id = ? AND LOWER(" + ownerColumn + ") = LOWER(?)",
            Integer.class,
            id,
            email
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
