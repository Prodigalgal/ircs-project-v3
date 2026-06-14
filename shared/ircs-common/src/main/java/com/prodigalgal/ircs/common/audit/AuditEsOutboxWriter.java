package com.prodigalgal.ircs.common.audit;

import com.prodigalgal.ircs.common.id.IrcsUuidGenerators;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AuditEsOutboxWriter {

    private static final String SOURCE_TABLE_REQUEST = "request_audit_logs";
    private static final String SOURCE_TABLE_WORKER = "worker_job_audit_events";
    private static final String SOURCE_TABLE_MAIL = "notification_mail_send_history";
    private static final String EVENT_UPSERT = "UPSERT";
    private static final String INSERT_SQL = """
            insert into audit_es_replication_outbox (
                id, created_at, updated_at, version, audit_class, source_table, source_id,
                event_type, payload, status, attempt_count, next_attempt_at
            ) values (?, now(), now(), 0, ?, ?, ?, ?, ?, 'PENDING', 0, now())
            """;

    private AuditEsOutboxWriter() {
    }

    public static void enqueueRequest(JdbcTemplate jdbcTemplate, AuditClass auditClass, UUID sourceId, Logger log) {
        enqueue(jdbcTemplate, auditClass, SOURCE_TABLE_REQUEST, sourceId, EVENT_UPSERT, null, log);
    }

    public static void enqueueRequest(Connection connection, AuditClass auditClass, UUID sourceId, Logger log) {
        enqueue(connection, auditClass, SOURCE_TABLE_REQUEST, sourceId, EVENT_UPSERT, null, log);
    }

    public static void enqueueWorkerJob(Connection connection, AuditClass auditClass, UUID sourceId, Logger log) {
        enqueue(connection, auditClass, SOURCE_TABLE_WORKER, sourceId, EVENT_UPSERT, null, log);
    }

    public static void enqueueMailSendHistory(Connection connection, AuditClass auditClass, UUID sourceId, Logger log) {
        enqueue(connection, auditClass, SOURCE_TABLE_MAIL, sourceId, EVENT_UPSERT, null, log);
    }

    public static void enqueue(
            JdbcTemplate jdbcTemplate,
            AuditClass auditClass,
            String sourceTable,
            UUID sourceId,
            String eventType,
            String payload,
            Logger log) {
        if (jdbcTemplate == null || sourceId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    INSERT_SQL,
                    IrcsUuidGenerators.nextId(),
                    normalized(auditClass),
                    sourceTable,
                    sourceId,
                    eventType,
                    payload);
        } catch (RuntimeException ex) {
            logBestEffortFailure(log, sourceTable, sourceId, ex);
        }
    }

    public static void enqueue(
            Connection connection,
            AuditClass auditClass,
            String sourceTable,
            UUID sourceId,
            String eventType,
            String payload,
            Logger log) {
        if (connection == null || sourceId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(1, IrcsUuidGenerators.nextId());
            statement.setString(2, normalized(auditClass));
            statement.setString(3, sourceTable);
            statement.setObject(4, sourceId);
            statement.setString(5, eventType);
            statement.setString(6, payload);
            statement.executeUpdate();
        } catch (SQLException | RuntimeException ex) {
            logBestEffortFailure(log, sourceTable, sourceId, ex);
        }
    }

    private static String normalized(AuditClass auditClass) {
        return (auditClass == null ? AuditClass.SYSTEM : auditClass).name();
    }

    private static void logBestEffortFailure(Logger log, String sourceTable, UUID sourceId, Exception ex) {
        if (log != null && log.isDebugEnabled()) {
            log.debug(
                    "Audit ES outbox enqueue skipped for {}:{}: {}",
                    sourceTable,
                    sourceId,
                    ex.getMessage());
        }
    }
}
