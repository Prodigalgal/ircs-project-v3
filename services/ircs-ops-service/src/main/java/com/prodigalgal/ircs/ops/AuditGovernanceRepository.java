package com.prodigalgal.ircs.ops;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditGovernanceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditArchiveSummaryResponse archiveSummary() {
        return jdbcTemplate.queryForObject(
                """
                select count(*) as total,
                       sum(case when audit_class = 'SECURITY' then 1 else 0 end) as security_count,
                       sum(case when audit_class = 'BEHAVIOR' then 1 else 0 end) as behavior_count,
                       sum(case when audit_class = 'SYSTEM' then 1 else 0 end) as system_count,
                       min(created_at) as oldest_archived_at,
                       max(created_at) as newest_archived_at
                  from audit_archive_entries
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AuditArchiveSummaryResponse(
                        rs.getLong("total"),
                        rs.getLong("security_count"),
                        rs.getLong("behavior_count"),
                        rs.getLong("system_count"),
                        toInstant(rs.getTimestamp("oldest_archived_at")),
                        toInstant(rs.getTimestamp("newest_archived_at"))));
    }

    public AuditEsReplicationOutboxSummaryResponse outboxSummary() {
        return jdbcTemplate.queryForObject(
                """
                select count(*) as total,
                       sum(case when status = 'PENDING' then 1 else 0 end) as pending_count,
                       sum(case when status = 'PROCESSING' then 1 else 0 end) as processing_count,
                       sum(case when status = 'SUCCEEDED' then 1 else 0 end) as succeeded_count,
                       sum(case when status = 'FAILED' then 1 else 0 end) as failed_count,
                       max(attempt_count) as max_attempt_count,
                       min(case when status = 'PENDING' then created_at else null end) as oldest_pending_at,
                       min(case when status in ('PENDING', 'FAILED') then next_attempt_at else null end) as next_attempt_at
                  from audit_es_replication_outbox
                """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new AuditEsReplicationOutboxSummaryResponse(
                        rs.getLong("total"),
                        rs.getLong("pending_count"),
                        rs.getLong("processing_count"),
                        rs.getLong("succeeded_count"),
                        rs.getLong("failed_count"),
                        getLong(rs.getObject("max_attempt_count")),
                        toInstant(rs.getTimestamp("oldest_pending_at")),
                        toInstant(rs.getTimestamp("next_attempt_at"))));
    }

    private static long getLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
