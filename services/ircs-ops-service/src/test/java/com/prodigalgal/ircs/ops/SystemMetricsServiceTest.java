package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTaskCounts;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

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
    void metricsExposeValkeyBucketRatesForTopCardsAndRows() {
        PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        stubRateBucket(Map.of(
                RateMetricKeys.COLLECTION_PAGE_DISCOVERED, "1",
                RateMetricKeys.COLLECTION_DETAIL_COMPLETED, "3",
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.NORMALIZE_VIDEO, RateMetricKeys.ACTION_COMPLETED), "2",
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.METADATA_PROVIDER, RateMetricKeys.ACTION_CLAIMED), "1",
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.METADATA_PROVIDER, RateMetricKeys.ACTION_COMPLETED), "1",
                RateMetricKeys.runtimeMetric(SearchSyncWorkTypes.RAW, RateMetricKeys.ACTION_COMPLETED), "1"));
        when(pipelineValkeyQueueProvider.getIfAvailable()).thenReturn(pipelineQueue);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(pipelineQueue.counts(anyString())).thenReturn(new PipelineValkeyTaskCounts(0, 0, 0));
        when(runtimeWorkQueue.counts(anyString())).thenReturn(new RuntimeWorkQueueCounts(0, 0, 0));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertEquals(1, metrics.pageDiscoveryRate().instantTpm());
        assertEquals(3, metrics.detailCollectionRate().instantTpm());
        assertEquals(2, metrics.normalizationRate().instantTpm());
        assertEquals(1, metrics.metadataRate().instantTpm());
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Pipeline".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "VALKEY_PROVIDER_PENDING".equals(queue.key())
                        && queue.rateKind() == RateKind.CLAIM
                        && queue.rate().instantTpm() == 1));
        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_INFLIGHT".equals(queue.key())
                        && queue.rateKind() == RateKind.COMPLETE
                        && queue.rate().instantTpm() == 1));
    }

    @Test
    void rabbitQueuesExposeNoRateWhenNoBucketMetricExists() {
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> QueueTopic.INGEST_VIDEO.name().equals(queue.key())
                        && queue.rateKind() == RateKind.NONE
                        && queue.rate().stale()));
    }

    private Properties queueProps(int messages, int consumers) {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, messages);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumers);
        return props;
    }

    @SuppressWarnings("unchecked")
    private void stubRateBucket(Map<Object, Object> counts) {
        HashOperations<String, Object, Object> hashOps = org.mockito.Mockito.mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = org.mockito.Mockito.mock(ZSetOperations.class);
        long bucketMillis = RateMetricKeys.DEFAULT_BUCKET_SIZE.toMillis();
        long currentBucket = RateMetricKeys.bucketStartMillis(System.currentTimeMillis(), RateMetricKeys.DEFAULT_BUCKET_SIZE);
        Set<String> indexedBuckets = Set.of(
                Long.toString(currentBucket - bucketMillis),
                Long.toString(currentBucket),
                Long.toString(currentBucket + bucketMillis));
        AtomicBoolean emitted = new AtomicBoolean(false);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(zSetOps.rangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(indexedBuckets);
        when(hashOps.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean indexed = indexedBuckets.stream().anyMatch(key::endsWith);
            return indexed && emitted.compareAndSet(false, true) ? counts : Map.of();
        });
    }
}
