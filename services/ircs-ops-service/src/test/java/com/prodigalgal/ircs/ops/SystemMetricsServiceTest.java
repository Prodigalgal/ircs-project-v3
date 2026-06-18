package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTaskCounts;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class SystemMetricsServiceTest {

    private final RabbitAdmin rabbitAdmin = org.mockito.Mockito.mock(RabbitAdmin.class);
    private final RedisConnectionFactory redisConnectionFactory = org.mockito.Mockito.mock(RedisConnectionFactory.class);
    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PipelineValkeyQueue> pipelineValkeyQueueProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RuntimeWorkQueue> runtimeWorkQueueProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final SystemMetricsService service = new SystemMetricsService(
            rabbitAdmin,
            redisConnectionFactory,
            redisTemplate,
            new SimpleMeterRegistry(),
            pipelineValkeyQueueProvider,
            runtimeWorkQueueProvider);

    @Test
    void metricsReturnQueueGroupsAndGracefullyHandleRedisFailure() {
        when(rabbitAdmin.getQueueProperties(anyString())).thenReturn(null);
        when(rabbitAdmin.getQueueProperties(QueueTopic.INGEST_VIDEO.queueName())).thenReturn(queueProps(3, 1));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertEquals(3, metrics.ingestQueueDepth());
        assertEquals(3, metrics.totalQueueBacklog());
        assertNull(metrics.redisMetric());
        assertFalse(metrics.queueGroups().isEmpty());
        assertTrue(metrics.queueGroups().stream().anyMatch(QueueGroupResponse::dlq));
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Task".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> QueueTopic.TASK_PAGE_FAILED.name().equals(queue.key())));
    }

    @Test
    void metricsExposeValkeyPipelineBacklog() {
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        when(pipelineValkeyQueueProvider.getIfAvailable()).thenReturn(pipelineQueue);
        when(pipelineQueue.counts(PipelineTaskTypes.NORMALIZE_VIDEO))
                .thenReturn(new PipelineValkeyTaskCounts(2, 1, 0));
        when(pipelineQueue.counts(PipelineTaskTypes.ENRICH_METADATA))
                .thenReturn(new PipelineValkeyTaskCounts(4, 2, 1));
        when(pipelineQueue.counts(PipelineTaskTypes.METADATA_PROVIDER))
                .thenReturn(new PipelineValkeyTaskCounts(5, 3, 2));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertEquals(3, metrics.normalizeQueueDepth());
        assertEquals(6, metrics.enrichQueueDepth());
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Pipeline".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "VALKEY_PROVIDER_INFLIGHT".equals(queue.key()) && queue.messageCount() == 3));
        assertTrue(metrics.queueGroups().stream()
                .filter(QueueGroupResponse::dlq)
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "VALKEY_PROVIDER_DLQ".equals(queue.key()) && queue.messageCount() == 2));
    }

    @Test
    void metricsExposeValkeyRuntimeWorkBacklog() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(runtimeWorkQueue.counts(SearchSyncWorkTypes.RAW))
                .thenReturn(new RuntimeWorkQueueCounts(7, 2, 1));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_PENDING".equals(queue.key()) && queue.messageCount() == 7));
        assertTrue(metrics.queueGroups().stream()
                .filter(QueueGroupResponse::dlq)
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_DLQ".equals(queue.key()) && queue.messageCount() == 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void metricsExposeValkeyStreamThroughputForTopCardsAndRows() {
        StreamOperations<String, Object, Object> streamOps = org.mockito.Mockito.mock(StreamOperations.class);
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(pipelineValkeyQueueProvider.getIfAvailable()).thenReturn(pipelineQueue);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(pipelineQueue.counts(anyString())).thenReturn(new PipelineValkeyTaskCounts(0, 0, 0));
        when(runtimeWorkQueue.counts(anyString())).thenReturn(new RuntimeWorkQueueCounts(0, 0, 0));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));
        when(streamOps.range(eq("ircs:pipeline:v2:events"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(
                        event("ircs:pipeline:v2:events", "1781749800000-0", "completed", PipelineTaskTypes.NORMALIZE_VIDEO),
                        event("ircs:pipeline:v2:events", "1781749800001-0", "completed", PipelineTaskTypes.NORMALIZE_VIDEO),
                        event("ircs:pipeline:v2:events", "1781749800002-0", "claimed", PipelineTaskTypes.METADATA_PROVIDER),
                        event("ircs:pipeline:v2:events", "1781749800003-0", "completed", PipelineTaskTypes.METADATA_PROVIDER)));
        when(streamOps.range(eq("ircs:work:v1:events"), any(Range.class), any(Limit.class)))
                .thenReturn(List.of(
                        event("ircs:work:v1:events", "1781749800004-0", "completed", SearchSyncWorkTypes.RAW)));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertEquals(2, metrics.normalizeTpm());
        assertEquals(1, metrics.enrichTpm());
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Pipeline".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "VALKEY_PROVIDER_PENDING".equals(queue.key()) && queue.throughput() == 1));
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_INFLIGHT".equals(queue.key()) && queue.throughput() == 1));
    }

    private Properties queueProps(int messages, int consumers) {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, messages);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumers);
        return props;
    }

    private MapRecord<String, Object, Object> event(
            String stream,
            String id,
            String event,
            String taskType) {
        return StreamRecords.<String, Object, Object>mapBacked(Map.of(
                        "event", event,
                        "taskType", taskType))
                .withStreamKey(stream)
                .withId(RecordId.of(id));
    }
}
