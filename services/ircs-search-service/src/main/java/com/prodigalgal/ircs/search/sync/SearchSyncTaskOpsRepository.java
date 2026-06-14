package com.prodigalgal.ircs.search.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchSyncTaskOpsRepository {

    private static final String STATS_SQL = """
            SELECT COUNT(*) AS total_count,
                   SUM(CASE WHEN UPPER(COALESCE(status, '')) = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
                   SUM(CASE WHEN UPPER(COALESCE(status, '')) = 'PROCESSING' THEN 1 ELSE 0 END) AS processing_count,
                   SUM(CASE WHEN UPPER(COALESCE(status, '')) IN ('COMPLETED', 'DONE', 'SYNCED') THEN 1 ELSE 0 END) AS completed_count,
                   SUM(CASE WHEN UPPER(COALESCE(status, '')) IN ('FAILED', 'ERROR', 'DEAD_LETTER') THEN 1 ELSE 0 END) AS failed_count,
                   SUM(CASE
                         WHEN UPPER(COALESCE(status, '')) = 'PENDING'
                          AND next_retry_at <= CURRENT_TIMESTAMP
                         THEN 1 ELSE 0 END) AS due_pending_count,
                   SUM(CASE WHEN UPPER(COALESCE(entity_type, '')) = 'RAW_VIDEO' THEN 1 ELSE 0 END) AS raw_video_count,
                   SUM(CASE WHEN UPPER(COALESCE(entity_type, '')) = 'UNIFIED_VIDEO' THEN 1 ELSE 0 END) AS unified_video_count,
                   SUM(CASE WHEN UPPER(COALESCE(operation, '')) = 'INDEX' THEN 1 ELSE 0 END) AS index_count,
                   SUM(CASE WHEN UPPER(COALESCE(operation, '')) = 'DELETE' THEN 1 ELSE 0 END) AS delete_count,
                   COALESCE(MAX(retry_count), 0) AS max_retry_count,
                   MIN(CASE WHEN UPPER(COALESCE(status, '')) = 'PENDING' THEN created_at ELSE NULL END) AS oldest_pending_at,
                   MIN(CASE WHEN UPPER(COALESCE(status, '')) = 'PENDING' THEN next_retry_at ELSE NULL END) AS next_due_at
              FROM search_sync_tasks
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchSyncTaskStatsResponse stats() {
        try {
            return jdbcTemplate.query(STATS_SQL, rs -> {
                if (!rs.next()) {
                    return new SearchSyncTaskStatsResponse(
                            true, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null);
                }
                return mapStats(rs);
            });
        } catch (DataAccessException ex) {
            return SearchSyncTaskStatsResponse.unavailable(classifyFailure(ex));
        }
    }

    private SearchSyncTaskStatsResponse mapStats(ResultSet rs) throws SQLException {
        return new SearchSyncTaskStatsResponse(
                true,
                null,
                rs.getLong("total_count"),
                rs.getLong("pending_count"),
                rs.getLong("processing_count"),
                rs.getLong("completed_count"),
                rs.getLong("failed_count"),
                rs.getLong("due_pending_count"),
                rs.getLong("raw_video_count"),
                rs.getLong("unified_video_count"),
                rs.getLong("index_count"),
                rs.getLong("delete_count"),
                rs.getInt("max_retry_count"),
                instant(rs, "oldest_pending_at"),
                instant(rs, "next_due_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String classifyFailure(DataAccessException ex) {
        String message = String.valueOf(ex.getMostSpecificCause() == null
                ? ex.getMessage()
                : ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("search_sync_tasks")
                || message.contains("relation") && message.contains("does not exist")
                || message.contains("table") && message.contains("not found")) {
            return "search_sync_tasks_unavailable";
        }
        return "search_sync_tasks_stats_query_failed";
    }
}
