package com.prodigalgal.ircs.common.audit;

import jakarta.servlet.http.HttpServletRequest;
import com.prodigalgal.ircs.common.id.IrcsUuidGenerator;
import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BffRequestAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(BffRequestAuditWriter.class);
    private static final IrcsUuidGenerator ID_GENERATOR = IrcsUuidGenerators.defaultGenerator();
    private static final int SOURCE_LIMIT = 128;
    private static final int USERNAME_LIMIT = 128;
    private static final int METHOD_LIMIT = 16;
    private static final int PATH_LIMIT = 1024;
    private static final int CLIENT_IP_LIMIT = 128;
    private static final int TRACE_ID_LIMIT = 128;
    private static final int ERROR_LIMIT = 2000;
    private static final String INSERT_SQL = """
            insert into request_audit_logs (
                id, created_at, updated_at, version, audit_class, request_source, username, method, path, query_string,
                status_code, success, duration_ms, client_ip, user_agent, trace_id, error_message
            ) values (?, now(), now(), 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final boolean enabled;
    private final String datasourceUrl;
    private final String username;
    private final String password;
    private final String requestSource;

    public BffRequestAuditWriter(boolean enabled, String datasourceUrl, String username, String password) {
        this(enabled, datasourceUrl, username, password, "bff");
    }

    public BffRequestAuditWriter(
            boolean enabled,
            String datasourceUrl,
            String username,
            String password,
            String requestSource) {
        this.enabled = enabled;
        this.datasourceUrl = normalize(datasourceUrl);
        this.username = normalize(username);
        this.password = password == null ? "" : password;
        this.requestSource = normalize(requestSource);
    }

    public void record(HttpServletRequest request, int statusCode, Duration duration, String errorMessage) {
        if (!enabled || datasourceUrl == null || request == null || excluded(request.getRequestURI())) {
            return;
        }
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            UUID id = ID_GENERATOR.nextId();
            AuditClass auditClass = AuditClassifiers.request(request.getRequestURI());
            statement.setObject(1, id);
            statement.setString(2, auditClass.name());
            statement.setString(3, truncate(requestSource, SOURCE_LIMIT));
            statement.setString(4, truncate(resolveUsername(request), USERNAME_LIMIT));
            statement.setString(5, truncate(request.getMethod(), METHOD_LIMIT));
            statement.setString(6, truncate(request.getRequestURI(), PATH_LIMIT));
            statement.setString(7, RequestAuditSanitizer.sanitizeQueryString(request.getQueryString()));
            statement.setInt(8, statusCode);
            statement.setBoolean(9, statusCode >= 200 && statusCode < 400);
            statement.setLong(10, Math.max(0L, duration == null ? 0L : duration.toMillis()));
            statement.setString(11, truncate(resolveClientIp(request), CLIENT_IP_LIMIT));
            statement.setString(12, request.getHeader("User-Agent"));
            statement.setString(13, truncate(resolveTraceId(request), TRACE_ID_LIMIT));
            statement.setString(14, truncate(errorMessage, ERROR_LIMIT));
            statement.executeUpdate();
            AuditEsOutboxWriter.enqueueRequest(connection, auditClass, id, log);
        } catch (SQLException ex) {
            log.warn("BFF request audit write failed: {}", ex.getMessage());
        }
    }

    private Connection connection() throws SQLException {
        if (username == null) {
            return DriverManager.getConnection(datasourceUrl);
        }
        return DriverManager.getConnection(datasourceUrl, username, password);
    }

    private static boolean excluded(String path) {
        if (path == null || path.isBlank()) {
            return true;
        }
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("/actuator/")
                || normalized.equals("/favicon.ico")
                || normalized.startsWith("/assets/")
                || normalized.startsWith("/static/")
                || normalized.startsWith("/api/v1/ops/request-audit");
    }

    private static String resolveUsername(HttpServletRequest request) {
        String value = firstHeader(request, "X-Authenticated-User", "X-User", "X-Username", "X-User-Email");
        return normalize(value);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = normalize(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded;
        }
        String realIp = normalize(request.getHeader("X-Real-IP"));
        return realIp == null ? normalize(request.getRemoteAddr()) : realIp;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String direct = firstHeader(request, "X-Trace-Id", "X-Request-Id", "X-B3-TraceId");
        if (direct != null) {
            return direct;
        }
        String traceparent = normalize(request.getHeader("traceparent"));
        if (traceparent == null) {
            return null;
        }
        String[] parts = traceparent.split("-");
        return parts.length >= 2 ? parts[1] : traceparent;
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = normalize(request.getHeader(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
