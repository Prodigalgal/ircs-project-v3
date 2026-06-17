package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTaskCounts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class SystemMetricsServiceTest {

    private final RabbitAdmin rabbitAdmin = org.mockito.Mockito.mock(RabbitAdmin.class);
    private final RedisConnectionFactory redisConnectionFactory = org.mockito.Mockito.mock(RedisConnectionFactory.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<PipelineValkeyQueue> pipelineValkeyQueueProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final SystemMetricsService service = new SystemMetricsService(
            rabbitAdmin,
            redisConnectionFactory,
            new SimpleMeterRegistry(),
            pipelineValkeyQueueProvider);

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

    private Properties queueProps(int messages, int consumers) {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, messages);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumers);
        return props;
    }
}
