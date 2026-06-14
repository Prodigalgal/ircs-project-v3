package com.prodigalgal.ircs.search.sync;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

@Repository
@RequiredArgsConstructor
class SearchSyncOutboxRepository {

    private static final int ERROR_LIMIT = 2000;
    private static final String SELECT_COLUMNS = """
            id, entity_type, entity_id, operation, status, retry_count,
            created_at, next_retry_at, source_service, correlation_id
            """;
    private static final RowMapper<SearchSyncTaskRecord> ROW_MAPPER = SearchSyncOutboxRepository::mapTask;
    private static final String POSTGRES_CLAIM_SQL = """
            WITH due AS (
                SELECT id
                  FROM search_sync_tasks
                 WHERE status IN ('PENDING', 'FAILED')
                   AND next_retry_at <= CURRENT_TIMESTAMP
                 ORDER BY created_at ASC, id ASC
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
            )
            UPDATE search_sync_tasks t
               SET status = 'PROCESSING',
                   locked_by = ?,
                   locked_until = ?,
                   updated_at = CURRENT_TIMESTAMP,
                   last_attempt_at = CURRENT_TIMESTAMP
              FROM due
             WHERE t.id = due.id
             RETURNING t.id, t.entity_type, t.entity_id, t.operation, t.status, t.retry_count,
                       t.created_at, t.next_retry_at, t.source_service, t.correlation_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    List<SearchSyncTaskRecord> claimDue(int limit, String workerId, Duration leaseDuration) {
        if (limit <= 0) {
            return List.of();
        }
        Instant lockedUntil = Instant.now().plus(leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration);
        try {
            return jdbcTemplate.query(
                    POSTGRES_CLAIM_SQL,
                    ROW_MAPPER,
                    limit,
                    workerId,
                    Timestamp.from(lockedUntil));
        } catch (DataAccessException ex) {
            if (!looksLikeDialectFailure(ex)) {
                throw ex;
            }
            return claimDueFallback(limit, workerId, lockedUntil);
        }
    }

    int restoreExpiredProcessingLeases() {
        return jdbcTemplate.update("""
                UPDATE search_sync_tasks
                   SET status = 'PENDING',
                       locked_by = NULL,
                       locked_until = NULL,
                       updated_at = CURRENT_TIMESTAMP,
                       last_error = COALESCE(last_error, 'processing lease expired')
                 WHERE status = 'PROCESSING'
                   AND locked_until IS NOT NULL
                   AND locked_until <= CURRENT_TIMESTAMP
                """);
    }

    void deleteTasks(Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        namedJdbcTemplate.update(
                "DELETE FROM search_sync_tasks WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids));
    }

    int deleteByEntityType(SearchEntityType entityType) {
        SearchEntityType safeEntityType = entityType == null ? SearchEntityType.UNIFIED_VIDEO : entityType;
        return jdbcTemplate.update(
                "DELETE FROM search_sync_tasks WHERE entity_type = ?",
                safeEntityType.name());
    }

    void markFailed(Collection<SearchSyncTaskRecord> tasks, int maxRetries, Duration maxBackoff, Throwable error) {
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        Instant now = Instant.now();
        String message = errorMessage(error);
        for (SearchSyncTaskRecord task : tasks) {
            int retryCount = task.retryCount() + 1;
            boolean deadLetter = retryCount >= maxRetries;
            Instant nextRetryAt = deadLetter ? now : now.plus(backoff(retryCount, maxBackoff));
            namedJdbcTemplate.update("""
                    UPDATE search_sync_tasks
                       SET status = :status,
                           retry_count = :retryCount,
                           next_retry_at = :nextRetryAt,
                           updated_at = CURRENT_TIMESTAMP,
                           locked_by = NULL,
                           locked_until = NULL,
                           last_error = :lastError
                     WHERE id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("status", deadLetter ? "DEAD_LETTER" : "PENDING")
                            .addValue("retryCount", retryCount)
                            .addValue("nextRetryAt", Timestamp.from(nextRetryAt))
                            .addValue("lastError", message)
                            .addValue("id", task.id()));
        }
    }

    private List<SearchSyncTaskRecord> claimDueFallback(int limit, String workerId, Instant lockedUntil) {
        List<SearchSyncTaskRecord> due = namedJdbcTemplate.query("""
                SELECT %s
                  FROM search_sync_tasks
                 WHERE status IN ('PENDING', 'FAILED')
                   AND next_retry_at <= CURRENT_TIMESTAMP
                 ORDER BY created_at ASC, id ASC
                 LIMIT :limit
                """.formatted(SELECT_COLUMNS), new MapSqlParameterSource("limit", limit), ROW_MAPPER);
        if (due.isEmpty()) {
            return due;
        }
        List<Long> ids = due.stream().map(SearchSyncTaskRecord::id).toList();
        namedJdbcTemplate.update("""
                UPDATE search_sync_tasks
                   SET status = 'PROCESSING',
                       locked_by = :workerId,
                       locked_until = :lockedUntil,
                       updated_at = CURRENT_TIMESTAMP,
                       last_attempt_at = CURRENT_TIMESTAMP
                 WHERE id IN (:ids)
                   AND status IN ('PENDING', 'FAILED')
                """,
                new MapSqlParameterSource()
                        .addValue("workerId", workerId)
                        .addValue("lockedUntil", Timestamp.from(lockedUntil))
                        .addValue("ids", ids));
        return namedJdbcTemplate.query("""
                SELECT %s
                  FROM search_sync_tasks
                 WHERE id IN (:ids)
                 ORDER BY created_at ASC, id ASC
                """.formatted(SELECT_COLUMNS), new MapSqlParameterSource("ids", ids), ROW_MAPPER);
    }

    private static SearchSyncTaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new SearchSyncTaskRecord(
                rs.getLong("id"),
                SearchEntityType.valueOf(rs.getString("entity_type")),
                rs.getObject("entity_id", java.util.UUID.class),
                SyncOperation.valueOf(rs.getString("operation")),
                rs.getString("status"),
                rs.getInt("retry_count"),
                instant(rs, "created_at"),
                instant(rs, "next_retry_at"),
                rs.getString("source_service"),
                rs.getString("correlation_id"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static boolean looksLikeDialectFailure(DataAccessException ex) {
        String message = String.valueOf(ex.getMostSpecificCause() == null
                ? ex.getMessage()
                : ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("syntax")
                || message.contains("returning")
                || message.contains("skip locked")
                || message.contains("for update");
    }

    private static Duration backoff(int retryCount, Duration maxBackoff) {
        long delaySeconds = 1L << Math.min(Math.max(0, retryCount), 10);
        long maxSeconds = maxBackoff == null ? 300 : Math.max(1, maxBackoff.toSeconds());
        return Duration.ofSeconds(Math.min(delaySeconds, maxSeconds));
    }

    private static String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return null;
        }
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        return message.length() <= ERROR_LIMIT ? message : message.substring(0, ERROR_LIMIT);
    }
}
