package com.prodigalgal.ircs.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.common.normalization.LlmCleaningWorkTypes;
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
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
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
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RabbitManagementRateProbe> rabbitRateProbeProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AggregationOpsStatsClient> aggregationOpsStatsClientProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final RuntimeConfigService runtimeConfig = org.mockito.Mockito.mock(RuntimeConfigService.class);
    private final SystemMetricsService service = new SystemMetricsService(
            rabbitAdmin,
            redisConnectionFactory,
            redisTemplate,
            new SimpleMeterRegistry(),
            pipelineValkeyQueueProvider,
            runtimeWorkQueueProvider,
            rabbitRateProbeProvider,
            aggregationOpsStatsClientProvider,
            runtimeConfig);

    @BeforeEach
    void setUp() {
        stubRuntimeConfigDefaults();
    }

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
    void runtimeWorkQueueExplainsDisabledConsumerBacklog() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(runtimeWorkQueue.counts(LlmCleaningWorkTypes.RAW_TERM))
                .thenReturn(new RuntimeWorkQueueCounts(11, 0, 0));
        when(runtimeWorkQueue.consumerCount(LlmCleaningWorkTypes.RAW_TERM)).thenReturn(0L);
        when(runtimeConfig.booleanValue("app.normalization.llm-cleaning.work-queue.worker.enabled", false))
                .thenReturn(false);
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_LLM_CLEANING_PENDING".equals(queue.key())
                        && queue.messageCount() == 11
                        && queue.consumerCount() == 0
                        && "DISABLED_BY_CONFIG".equals(queue.blockedReason())));
    }

    @Test
    void runtimeWorkQueueDoesNotReportStaleConsumerWhenRecentRateExists() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(runtimeWorkQueue.counts(SearchSyncWorkTypes.RAW))
                .thenReturn(new RuntimeWorkQueueCounts(11, 0, 0));
        when(runtimeWorkQueue.consumerCount(SearchSyncWorkTypes.RAW)).thenReturn(0L);
        stubRateBucket(Map.of(
                RateMetricKeys.runtimeMetric(SearchSyncWorkTypes.RAW, RateMetricKeys.ACTION_CLAIMED), "3"));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_PENDING".equals(queue.key())
                        && queue.messageCount() == 11
                        && queue.consumerCount() == 0
                        && queue.blockedReason() == null));
    }

    @Test
    void runtimeWorkQueueReportsNoProgressWhenConsumerExistsButRateIsStale() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(runtimeWorkQueue.counts(SearchSyncWorkTypes.RAW))
                .thenReturn(new RuntimeWorkQueueCounts(11, 0, 0));
        when(runtimeWorkQueue.consumerCount(SearchSyncWorkTypes.RAW)).thenReturn(1L);
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_SEARCH_RAW_PENDING".equals(queue.key())
                        && queue.messageCount() == 11
                        && queue.consumerCount() == 1
                        && "CONSUMER_NO_PROGRESS".equals(queue.blockedReason())));
    }

    @Test
    void runtimeWorkQueueReportsNoActiveConsumerInsteadOfHeartbeatTimeout() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(runtimeWorkQueue.counts(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(new RuntimeWorkQueueCounts(9, 0, 0));
        when(runtimeWorkQueue.consumerCount(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(0L);
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_AGGREGATION_PENDING".equals(queue.key())
                        && queue.messageCount() == 9
                        && queue.consumerCount() == 0
                        && "NO_ACTIVE_CONSUMER".equals(queue.blockedReason())));
    }

    @Test
    void metricsUseShortLocalCacheForRepeatedReads() throws Exception {
        when(runtimeConfig.durationValue("app.ops.metrics.cache-ttl", Duration.ofSeconds(15)))
                .thenReturn(Duration.ofSeconds(5));
        when(rabbitAdmin.getQueueProperties(QueueTopic.INGEST_VIDEO.queueName()))
                .thenReturn(queueProps(3, 1));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse first = service.currentMetrics();
        SystemMetricsResponse second = waitForCachedMetrics();

        assertEquals("METRICS_REFRESHING", first.history().get("dashboardMetrics"));
        assertEquals(3, second.ingestQueueDepth());
        verify(rabbitAdmin, times(1)).getQueueProperties(QueueTopic.INGEST_VIDEO.queueName());
    }

    @Test
    void aggregationInflightReportsProcessingWhileWorkerStageIsFresh() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        AggregationOpsStatsClient aggregationOpsStatsClient = org.mockito.Mockito.mock(AggregationOpsStatsClient.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(aggregationOpsStatsClientProvider.getIfAvailable()).thenReturn(aggregationOpsStatsClient);
        when(runtimeWorkQueue.counts(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(new RuntimeWorkQueueCounts(0, 25, 0));
        when(runtimeWorkQueue.consumerCount(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(1L);
        when(aggregationOpsStatsClient.currentStats()).thenReturn(Map.of(
                "available", true,
                "worker", Map.of(
                        "running", true,
                        "currentStage", "AGGREGATE_BATCH",
                        "runningForSeconds", 30)));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_AGGREGATION_INFLIGHT".equals(queue.key())
                        && queue.messageCount() == 25
                        && queue.consumerCount() == 1
                        && "PROCESSING".equals(queue.blockedReason())));
    }

    @Test
    void aggregationInflightReportsProcessingStuckAfterThreshold() {
        RuntimeWorkQueue runtimeWorkQueue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
        AggregationOpsStatsClient aggregationOpsStatsClient = org.mockito.Mockito.mock(AggregationOpsStatsClient.class);
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(runtimeWorkQueue);
        when(aggregationOpsStatsClientProvider.getIfAvailable()).thenReturn(aggregationOpsStatsClient);
        when(runtimeWorkQueue.counts(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(new RuntimeWorkQueueCounts(0, 25, 0));
        when(runtimeWorkQueue.consumerCount(com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes.RAW_VIDEO))
                .thenReturn(1L);
        when(aggregationOpsStatsClient.currentStats()).thenReturn(Map.of(
                "available", true,
                "worker", Map.of(
                        "running", true,
                        "currentStage", "AGGREGATE_BATCH",
                        "runningForSeconds", 180)));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .filter(group -> "Valkey Runtime Work".equals(group.title()))
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> "RUNTIME_AGGREGATION_INFLIGHT".equals(queue.key())
                        && "CONSUMER_PROCESSING_STUCK".equals(queue.blockedReason())));
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

    @Test
    void rabbitQueuesExposeAckRateWhenManagementBucketExists() {
        RabbitManagementRateProbe probe = org.mockito.Mockito.mock(RabbitManagementRateProbe.class);
        when(rabbitRateProbeProvider.getIfAvailable()).thenReturn(probe);
        when(probe.rateAvailable()).thenReturn(true);
        stubRateBucket(Map.of(
                RabbitManagementRateProbe.metricKey(
                        QueueTopic.INGEST_VIDEO,
                        RabbitManagementRateProbe.RabbitQueueRole.MAIN,
                        RabbitManagementRateProbe.RabbitRateAction.ACKED),
                "4"));
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("redis unavailable"));

        SystemMetricsResponse metrics = service.currentMetrics();

        assertTrue(metrics.queueGroups().stream()
                .flatMap(group -> group.queues().stream())
                .anyMatch(queue -> QueueTopic.INGEST_VIDEO.name().equals(queue.key())
                        && queue.rateKind() == RateKind.ACK
                        && queue.rate().instantTpm() == 4));
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

    private void stubRuntimeConfigDefaults() {
        when(runtimeConfig.stringValue("app.rate-metrics.key-prefix", RateMetricKeys.DEFAULT_KEY_PREFIX))
                .thenReturn(RateMetricKeys.DEFAULT_KEY_PREFIX);
        when(runtimeConfig.positiveDurationValue("app.rate-metrics.bucket-size", RateMetricKeys.DEFAULT_BUCKET_SIZE))
                .thenReturn(RateMetricKeys.DEFAULT_BUCKET_SIZE);
        when(runtimeConfig.positiveDurationValue("app.ops.metrics.instant-window", Duration.ofSeconds(60)))
                .thenReturn(Duration.ofSeconds(60));
        when(runtimeConfig.positiveDurationValue("app.ops.metrics.stable-window", Duration.ofMinutes(5)))
                .thenReturn(Duration.ofMinutes(5));
        when(runtimeConfig.doubleValue("app.ops.metrics.ewma-alpha", 0.25d)).thenReturn(0.25d);
        when(runtimeConfig.positiveDurationValue(
                "app.aggregation.work-queue.worker.processing-stall-threshold",
                Duration.ofSeconds(120))).thenReturn(Duration.ofSeconds(120));
        when(runtimeConfig.durationValue("app.ops.metrics.cache-ttl", Duration.ofSeconds(15)))
                .thenReturn(Duration.ZERO);
    }

    private SystemMetricsResponse waitForCachedMetrics() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        SystemMetricsResponse latest = null;
        while (System.nanoTime() < deadline) {
            latest = service.currentMetrics();
            if (!"METRICS_REFRESHING".equals(latest.history().get("dashboardMetrics"))) {
                return latest;
            }
            Thread.sleep(25);
        }
        return latest;
    }
}
