package com.prodigalgal.ircs.common.search;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@ConditionalOnBean(type = "org.springframework.jdbc.core.JdbcTemplate")
@Slf4j
public class SearchSyncOutboxWriter {

    private static final int SOURCE_LIMIT = 128;
    private static final int CORRELATION_LIMIT = 128;
    private static final String INSERT_SQL = """
            INSERT INTO search_sync_tasks (
                entity_type, entity_id, operation, status, retry_count,
                created_at, updated_at, next_retry_at, source_service, correlation_id
            ) VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final String defaultSourceService;

    public SearchSyncOutboxWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.application.name:ircs-service}") String defaultSourceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.defaultSourceService = normalize(defaultSourceService, SOURCE_LIMIT);
    }

    public int enqueue(UUID entityId, SearchEntityType entityType, SyncOperation operation) {
        return enqueue(entityId, entityType, operation, defaultSourceService, null);
    }

    public int enqueue(
            UUID entityId,
            SearchEntityType entityType,
            SyncOperation operation,
            String sourceService,
            String correlationId) {
        return enqueueBatch(
                entityId == null ? java.util.List.of() : java.util.List.of(entityId),
                entityType,
                operation,
                sourceService,
                correlationId);
    }

    public int enqueueBatch(Collection<UUID> entityIds, SearchEntityType entityType, SyncOperation operation) {
        return enqueueBatch(entityIds, entityType, operation, defaultSourceService, null);
    }

    public int enqueueBatch(
            Collection<UUID> entityIds,
            SearchEntityType entityType,
            SyncOperation operation,
            String sourceService,
            String correlationId) {
        Objects.requireNonNull(entityType, "entityType is required");
        Objects.requireNonNull(operation, "operation is required");
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (entityIds != null) {
            entityIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return 0;
        }

        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        String source = normalize(
                StringUtils.hasText(sourceService) ? sourceService : defaultSourceService,
                SOURCE_LIMIT);
        String correlation = normalize(correlationId, CORRELATION_LIMIT);
        UUID[] orderedIds = ids.toArray(UUID[]::new);
        int[] results = jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws java.sql.SQLException {
                ps.setString(1, entityType.name());
                ps.setObject(2, orderedIds[index]);
                ps.setString(3, operation.name());
                ps.setTimestamp(4, timestamp);
                ps.setTimestamp(5, timestamp);
                ps.setTimestamp(6, timestamp);
                ps.setString(7, source);
                ps.setString(8, correlation);
            }

            @Override
            public int getBatchSize() {
                return orderedIds.length;
            }
        });

        int accepted = 0;
        for (int result : results) {
            if (result >= 0) {
                accepted += result;
            } else {
                accepted++;
            }
        }
        log.debug("Enqueued {} search sync tasks for {} {}", accepted, entityType, operation);
        return accepted;
    }

    private static String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
