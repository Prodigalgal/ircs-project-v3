package com.prodigalgal.ircs.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BffRequestAuditWriterTest {

    @Test
    void writesAuditLogForProxyRequest() throws Exception {
        String url = "jdbc:h2:mem:audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTables(url);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portal/search/suggest");
        request.setQueryString("keyword=codex");
        request.addHeader("X-Authenticated-User", "member@example.com");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.addHeader("User-Agent", "bff-test");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");

        BffRequestAuditWriter writer = new BffRequestAuditWriter(true, url, null, null, "ircs-portal-bff");
        writer.record(request, 200, Duration.ofMillis(42), null);

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement().executeQuery(
                     """
                             select audit_class, request_source, username, method, path, query_string, status_code,
                                    success, duration_ms, client_ip, user_agent, trace_id
                             from request_audit_logs
                             """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("audit_class")).isEqualTo("BEHAVIOR");
            assertThat(result.getString("request_source")).isEqualTo("ircs-portal-bff");
            assertThat(result.getString("username")).isEqualTo("member@example.com");
            assertThat(result.getString("method")).isEqualTo("GET");
            assertThat(result.getString("path")).isEqualTo("/api/portal/search/suggest");
            assertThat(result.getString("query_string")).isEqualTo("keyword=codex");
            assertThat(result.getInt("status_code")).isEqualTo(200);
            assertThat(result.getBoolean("success")).isTrue();
            assertThat(result.getLong("duration_ms")).isEqualTo(42L);
            assertThat(result.getString("client_ip")).isEqualTo("203.0.113.10");
            assertThat(result.getString("user_agent")).isEqualTo("bff-test");
            assertThat(result.getString("trace_id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(result.next()).isFalse();
        }
        assertOutboxCount(url, "request_audit_logs", "BEHAVIOR", 1);
    }

    @Test
    void excludesAuditQueryNoise() throws Exception {
        String url = "jdbc:h2:mem:audit_excluded;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTables(url);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ops/request-audit");
        BffRequestAuditWriter writer = new BffRequestAuditWriter(true, url, null, null);

        writer.record(request, 200, Duration.ofMillis(1), null);

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement().executeQuery("select count(*) from request_audit_logs")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
    }

    @Test
    void redactsSensitiveQueryParametersForProxyRequest() throws Exception {
        String url = "jdbc:h2:mem:audit_redacted;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTables(url);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portal/profile");
        request.setQueryString("token=secret-token&keyword=codex&password=secret-password");
        BffRequestAuditWriter writer = new BffRequestAuditWriter(true, url, null, null, "ircs-portal-bff");

        writer.record(request, 200, Duration.ofMillis(1), null);

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement().executeQuery("select query_string from request_audit_logs")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("query_string"))
                    .isEqualTo("token=***&keyword=codex&password=***")
                    .doesNotContain("secret-token")
                    .doesNotContain("secret-password");
        }
        assertOutboxCount(url, "request_audit_logs", "SECURITY", 1);
    }

    private static void createAuditTables(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table request_audit_logs (
                        id uuid primary key,
                        created_at timestamp not null,
                        updated_at timestamp not null,
                        version bigint,
                        audit_class varchar(32) not null,
                        request_source varchar(128),
                        username varchar(128),
                        method varchar(16) not null,
                        path varchar(1024) not null,
                        query_string text,
                        status_code integer not null,
                        success boolean not null,
                        duration_ms bigint not null,
                        client_ip varchar(128),
                        user_agent text,
                        trace_id varchar(128),
                        error_message text
                    )
                    """);
            statement.execute("""
                    create table audit_es_replication_outbox (
                        id uuid primary key,
                        created_at timestamp not null,
                        updated_at timestamp not null,
                        version bigint not null,
                        audit_class varchar(32) not null,
                        source_table varchar(128) not null,
                        source_id uuid not null,
                        event_type varchar(32) not null,
                        payload text,
                        status varchar(32) not null,
                        attempt_count integer not null,
                        next_attempt_at timestamp not null,
                        last_error text
                    )
                    """);
        }
    }

    private static void assertOutboxCount(String url, String sourceTable, String auditClass, int expected) throws Exception {
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.prepareStatement("""
                     select count(*)
                     from audit_es_replication_outbox
                     where source_table = ? and audit_class = ?
                     """)) {
            statement.setString(1, sourceTable);
            statement.setString(2, auditClass);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(expected);
            }
        }
    }
}
