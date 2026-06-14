package com.prodigalgal.ircs.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkerJobAuditWriterTest {

    @Test
    void writesWorkerJobAuditEvent() throws Exception {
        String url = "jdbc:h2:mem:worker_job_audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTable(url);

        WorkerJobAuditWriter writer = new WorkerJobAuditWriter(
                true,
                url,
                null,
                null,
                "ircs-notification-worker",
                "notification-pod-1");

        writer.record(WorkerJobAuditEvent.succeeded(
                "queue_consumer",
                "notification.mail",
                "mail-task-1",
                Duration.ofMillis(42)));

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement().executeQuery("""
                     select audit_class, job_source, job_type, job_name, correlation_id, status, duration_ms,
                            error_class, error_message
                     from worker_job_audit_events
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("audit_class")).isEqualTo("SYSTEM");
            assertThat(result.getString("job_source")).isEqualTo("notification-pod-1");
            assertThat(result.getString("job_type")).isEqualTo("queue_consumer");
            assertThat(result.getString("job_name")).isEqualTo("notification.mail");
            assertThat(result.getString("correlation_id")).isEqualTo("mail-task-1");
            assertThat(result.getString("status")).isEqualTo("succeeded");
            assertThat(result.getLong("duration_ms")).isEqualTo(42L);
            assertThat(result.getString("error_class")).isNull();
            assertThat(result.getString("error_message")).isNull();
            assertThat(result.next()).isFalse();
        }
        assertOutboxCount(url, "worker_job_audit_events", "SYSTEM", 1);
    }

    @Test
    void defaultJobSourceContainsServiceNameAndInstanceIdentity() throws Exception {
        String url = "jdbc:h2:mem:worker_job_audit_instance_source;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTable(url);
        WorkerJobAuditWriter writer = new WorkerJobAuditWriter(
                true,
                url,
                null,
                null,
                "ircs-task-service");

        writer.record(WorkerJobAuditEvent.succeeded(
                "scheduler",
                "task.watchdog",
                null,
                Duration.ofMillis(1)));

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement()
                     .executeQuery("select job_source from worker_job_audit_events")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("job_source"))
                    .startsWith("ircs-task-service@")
                    .contains("#")
                    .hasSizeLessThanOrEqualTo(128);
        }
    }

    @Test
    void redactsSensitiveErrorMessage() throws Exception {
        String url = "jdbc:h2:mem:worker_job_audit_redacted;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTable(url);
        WorkerJobAuditWriter writer = new WorkerJobAuditWriter(true, url, null, null, "worker");

        writer.record(WorkerJobAuditEvent.failed(
                "queue_consumer",
                "notification.mail",
                null,
                Duration.ofMillis(7),
                new RuntimeException("access_token=abc123 for codex@example.invalid")));

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement()
                     .executeQuery("select audit_class, error_class, error_message from worker_job_audit_events")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("audit_class")).isEqualTo("SYSTEM");
            assertThat(result.getString("error_class")).isEqualTo("java.lang.RuntimeException");
            assertThat(result.getString("error_message"))
                    .isEqualTo("access_token=[redacted-token] for [redacted-email]");
        }
        assertOutboxCount(url, "worker_job_audit_events", "SYSTEM", 1);
    }

    @Test
    void keepsNonSensitiveParserTokenDiagnostic() throws Exception {
        String url = "jdbc:h2:mem:worker_job_audit_parser_token;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        createAuditTable(url);
        WorkerJobAuditWriter writer = new WorkerJobAuditWriter(true, url, null, null, "worker");

        writer.record(WorkerJobAuditEvent.failed(
                "queue_consumer",
                "scraper.task-page",
                "task-1",
                Duration.ofMillis(7),
                new RuntimeException("Unexpected character ('<' expected a valid value token 'null')")));

        try (var connection = DriverManager.getConnection(url);
             var result = connection.createStatement()
                     .executeQuery("select error_message from worker_job_audit_events")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("error_message"))
                    .contains("Unexpected character")
                    .contains("token 'null'")
                    .doesNotContain("[redacted-sensitive-error]");
        }
    }

    @Test
    void skipsWhenDatasourceIsMissing() {
        WorkerJobAuditWriter writer = WorkerJobAuditWriter.noop();

        writer.record(WorkerJobAuditEvent.succeeded(
                "queue_consumer",
                "notification.mail",
                null,
                Duration.ofMillis(1)));
    }

    @Test
    void recordsMicrometerMetricsEvenWhenDbAuditIsDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerJobAuditWriter writer = new WorkerJobAuditWriter(
                false,
                null,
                null,
                null,
                "ircs-notification-worker",
                "notification-pod-1",
                registry);

        writer.record(WorkerJobAuditEvent.failed(
                "queue_consumer",
                "notification.mail",
                "mail-task-1",
                Duration.ofMillis(17),
                new RuntimeException("delivery failed")));

        assertThat(registry.get("ircs.worker.job.runs")
                .tag("worker_id", "notification-pod-1")
                .tag("job_type", "queue_consumer")
                .tag("job_name", "notification.mail")
                .tag("outcome", "failed")
                .counter()
                .count())
                .isEqualTo(1.0d);
        assertThat(registry.get("ircs.worker.job.duration")
                .tag("worker_id", "notification-pod-1")
                .tag("job_type", "queue_consumer")
                .tag("job_name", "notification.mail")
                .tag("outcome", "failed")
                .timer()
                .totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(17.0d);
    }

    private static void createAuditTable(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table worker_job_audit_events (
                        id uuid primary key,
                        created_at timestamp not null,
                        updated_at timestamp not null,
                        version bigint not null,
                        audit_class varchar(32) not null,
                        job_source varchar(128) not null,
                        job_type varchar(64) not null,
                        job_name varchar(128) not null,
                        correlation_id varchar(128),
                        status varchar(32) not null,
                        duration_ms bigint not null,
                        error_class varchar(256),
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
