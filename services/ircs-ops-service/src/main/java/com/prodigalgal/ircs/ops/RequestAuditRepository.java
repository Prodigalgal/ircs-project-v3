package com.prodigalgal.ircs.ops;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class RequestAuditRepository {

    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000L;
    private static final Map<String, String> SORT_COLUMNS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("createdAt", "created_at"),
            Map.entry("updatedAt", "updated_at"),
            Map.entry("auditClass", "audit_class"),
            Map.entry("requestSource", "request_source"),
            Map.entry("username", "username"),
            Map.entry("method", "method"),
            Map.entry("path", "path"),
            Map.entry("statusCode", "status_code"),
            Map.entry("success", "success"),
            Map.entry("durationMs", "duration_ms"),
            Map.entry("clientIp", "client_ip"),
            Map.entry("traceId", "trace_id"));
    private static final String DEFAULT_ORDER = " order by created_at desc, id desc";
    private static final RowMapper<RequestAuditLogResponse> ROW_MAPPER = RequestAuditRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<RequestAuditLogResponse> findAll(
            Pageable pageable,
            String username,
            String requestSource,
            String method,
            String path,
            Integer statusCode,
            String statusClass,
            String clientIp,
            Instant from,
            Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> predicates = new ArrayList<>();

        if (StringUtils.hasText(username)) {
            predicates.add("lower(username) like :username");
            params.addValue("username", "%" + username.toLowerCase() + "%");
        }
        if (StringUtils.hasText(requestSource)) {
            predicates.add("lower(request_source) like :requestSource");
            params.addValue("requestSource", "%" + requestSource.toLowerCase() + "%");
        }
        if (StringUtils.hasText(method)) {
            predicates.add("method = :method");
            params.addValue("method", method.toUpperCase());
        }
        if (StringUtils.hasText(path)) {
            predicates.add("lower(path) like :path");
            params.addValue("path", "%" + path.toLowerCase() + "%");
        }
        if (statusCode != null) {
            predicates.add("status_code = :statusCode");
            params.addValue("statusCode", statusCode);
        } else if (StringUtils.hasText(statusClass)) {
            int lower = parseStatusClass(statusClass);
            if (lower > 0) {
                predicates.add("status_code between :statusLower and :statusUpper");
                params.addValue("statusLower", lower);
                params.addValue("statusUpper", lower + 99);
            }
        }
        if (StringUtils.hasText(clientIp)) {
            predicates.add("client_ip like :clientIp");
            params.addValue("clientIp", "%" + clientIp + "%");
        }
        if (from != null) {
            predicates.add("created_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            predicates.add("created_at <= :to");
            params.addValue("to", Timestamp.from(to));
        }

        String where = predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates);
        long total = count("select count(*) from request_audit_logs" + where, params);
        String sql = """
                select id, created_at, updated_at, version, audit_class, request_source, username, method, path,
                       query_string, status_code, success, duration_ms, client_ip,
                       user_agent, trace_id, error_message
                  from request_audit_logs
                """ + where + JdbcPageSorts.orderBy(pageable, SORT_COLUMNS, DEFAULT_ORDER)
                + " limit :limit offset :offset";
        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());
        List<RequestAuditLogResponse> content = jdbcTemplate.query(sql, params, ROW_MAPPER);
        return new PageImpl<>(content, pageable, total);
    }

    public RequestAuditSummaryResponse summarize(Instant since) {
        MapSqlParameterSource params = new MapSqlParameterSource("since", Timestamp.from(since));
        return new RequestAuditSummaryResponse(
                count("select count(*) from request_audit_logs where created_at >= :since", params),
                count("select count(*) from request_audit_logs where created_at >= :since and status_code >= 400", params),
                count("select count(*) from request_audit_logs where created_at >= :since and duration_ms >= "
                        + SLOW_REQUEST_THRESHOLD_MS, params),
                maxDurationSince(params));
    }

    private static int parseStatusClass(String statusClass) {
        return switch (statusClass.trim().toLowerCase()) {
            case "2xx" -> 200;
            case "3xx" -> 300;
            case "4xx" -> 400;
            case "5xx" -> 500;
            default -> 0;
        };
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long value = jdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private Long maxDurationSince(MapSqlParameterSource params) {
        return jdbcTemplate.queryForObject(
                "select max(duration_ms) from request_audit_logs where created_at >= :since",
                params,
                Long.class);
    }

    private static RequestAuditLogResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RequestAuditLogResponse(
                rs.getObject("id", UUID.class),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                getLong(rs, "version"),
                rs.getString("audit_class"),
                rs.getString("request_source"),
                rs.getString("username"),
                rs.getString("method"),
                rs.getString("path"),
                rs.getString("query_string"),
                getInteger(rs, "status_code"),
                getBoolean(rs, "success"),
                getLong(rs, "duration_ms"),
                rs.getString("client_ip"),
                rs.getString("user_agent"),
                rs.getString("trace_id"),
                rs.getString("error_message"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Long getLong(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    private static Integer getInteger(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private static Boolean getBoolean(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : rs.getBoolean(column);
    }
}
