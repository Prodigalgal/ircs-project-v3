package com.prodigalgal.ircs.search.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class SearchOutboxRelayTest {

    @Test
    void publishesLatestTaskPerEntityAndDeletesSuccessfulGroup() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createTable(jdbcTemplate);
        UUID id = UUID.randomUUID();
        insertTask(jdbcTemplate, id, "INDEX", 0, Instant.now().minusSeconds(10));
        insertTask(jdbcTemplate, id, "DELETE", 0, Instant.now().minusSeconds(5));
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SearchOutboxRelay relay = relay(jdbcTemplate, rabbitTemplate, meterRegistry);

        int published = relay.runOnce();

        assertThat(published).isEqualTo(1);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.Mockito.eq(QueueTopic.Names.SEARCH_X),
                org.mockito.Mockito.eq("search.sync.raw"),
                payloadCaptor.capture(),
                any(MessagePostProcessor.class));
        SearchSyncMessage message = new ObjectMapper().readValue(payloadCaptor.getValue(), SearchSyncMessage.class);
        assertThat(message.getOperation()).isEqualTo(SyncOperation.DELETE);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM search_sync_tasks", Integer.class)).isZero();
        assertThat(meterRegistry.find("ircs.search.outbox.relay.runs")
                .tag("worker_id", "test-worker")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.find("ircs.search.outbox.relay.groups")
                .tag("worker_id", "test-worker")
                .tag("result", "published")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.find("ircs.search.outbox.relay.task.delay")
                .tag("worker_id", "test-worker")
                .tag("result", "published")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(relay.state().workerId()).isEqualTo("test-worker");
        assertThat(relay.state().lastSuccessAt()).isNotNull();
    }

    @Test
    void movesFailedGroupToDeadLetterAfterMaxRetries() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createTable(jdbcTemplate);
        UUID id = UUID.randomUUID();
        insertTask(jdbcTemplate, id, "INDEX", 4, Instant.now().minusSeconds(10));
        RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        doThrow(new RuntimeException("rabbit down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString(), any(MessagePostProcessor.class));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SearchOutboxRelay relay = relay(jdbcTemplate, rabbitTemplate, meterRegistry);

        int published = relay.runOnce();

        assertThat(published).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM search_sync_tasks",
                String.class)).isEqualTo("DEAD_LETTER");
        assertThat(meterRegistry.find("ircs.search.outbox.relay.groups")
                .tag("worker_id", "test-worker")
                .tag("result", "failed")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    private SearchOutboxRelay relay(
            JdbcTemplate jdbcTemplate,
            RabbitTemplate rabbitTemplate,
            SimpleMeterRegistry meterRegistry) throws Exception {
        SearchOutboxRelay relay = new SearchOutboxRelay(
                new SearchSyncOutboxRepository(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate)),
                rabbitTemplate,
                new ObjectMapper(),
                org.mockito.Mockito.mock(WorkerJobAuditWriter.class),
                new SearchOutboxRelayMetrics(meterRegistry));
        set(relay, "batchSize", 10);
        set(relay, "maxRetries", 5);
        set(relay, "leaseSeconds", 60L);
        set(relay, "maxBackoffSeconds", 300L);
        set(relay, "workerId", "test-worker");
        set(relay, "applicationName", "ircs-search-service");
        return relay;
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build());
    }

    private void createTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE search_sync_tasks (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    entity_type VARCHAR(50) NOT NULL,
                    entity_id UUID NOT NULL,
                    operation VARCHAR(20) NOT NULL,
                    status VARCHAR(20) NOT NULL,
                    retry_count INT DEFAULT 0,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    next_retry_at TIMESTAMP NOT NULL,
                    source_service VARCHAR(128),
                    correlation_id VARCHAR(128),
                    locked_by VARCHAR(128),
                    locked_until TIMESTAMP,
                    last_attempt_at TIMESTAMP,
                    last_error VARCHAR(2000)
                )
                """);
    }

    private void insertTask(JdbcTemplate jdbcTemplate, UUID id, String operation, int retryCount, Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO search_sync_tasks
                    (entity_type, entity_id, operation, status, retry_count,
                     created_at, updated_at, next_retry_at, source_service, correlation_id)
                VALUES ('RAW_VIDEO', ?, ?, 'PENDING', ?, ?, ?, ?, 'test', ?)
                """,
                id,
                operation,
                retryCount,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                Timestamp.from(Instant.now().minusSeconds(1)),
                id.toString());
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
