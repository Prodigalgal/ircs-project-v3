package com.prodigalgal.ircs.common.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.common.work.WorkSubmissionGate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class PipelineValkeyQueueRateMetricsTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-18T00:00:05Z"), ZoneOffset.UTC);
    private final PipelineValkeyQueue queue = new PipelineValkeyQueue(
            redisTemplate,
            null,
            null,
            provider(clock),
            "ircs:pipeline:v2",
            10_000,
            "ircs:metrics:rate",
            Duration.ofSeconds(10),
            Duration.ofMinutes(30),
            Duration.ofMinutes(31));

    @Test
    void submitPassesRateBucketAndMetricArgs() {
        queue.submit(
                PipelineTaskTypes.NORMALIZE_VIDEO,
                "task-1",
                UUID.randomUUID(),
                "v1",
                "",
                Duration.ZERO);

        CapturedExecution execution = captureExecution();

        assertRateArgs(
                execution,
                6,
                13,
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.NORMALIZE_VIDEO, RateMetricKeys.ACTION_SUBMITTED));
    }

    @Test
    void submitSkipsRedisWhenSubmissionGateIsDisabled() {
        WorkSubmissionGate gate = org.mockito.Mockito.mock(WorkSubmissionGate.class);
        when(gate.canSubmitPipeline(PipelineTaskTypes.ENRICH_METADATA)).thenReturn(false);
        PipelineValkeyQueue gatedQueue = new PipelineValkeyQueue(
                redisTemplate,
                gateProvider(gate),
                null,
                provider(clock),
                "ircs:pipeline:v2",
                10_000,
                "ircs:metrics:rate",
                Duration.ofSeconds(10),
                Duration.ofMinutes(30),
                Duration.ofMinutes(31));

        gatedQueue.submit(
                PipelineTaskTypes.ENRICH_METADATA,
                "task-1",
                UUID.randomUUID(),
                "v1",
                "",
                Duration.ZERO);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void claimPassesRateBucketAndMetricArgs() {
        queue.claim(PipelineTaskTypes.METADATA_PROVIDER, "worker-a", 5, Duration.ofMinutes(5));

        CapturedExecution execution = captureExecution();

        assertRateArgs(
                execution,
                5,
                11,
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.METADATA_PROVIDER, RateMetricKeys.ACTION_CLAIMED));
    }

    @Test
    void hasOpenTaskChecksPendingAndInflightIndexes() {
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = org.mockito.Mockito.mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.score("ircs:pipeline:v2:NORMALIZE_VIDEO:pending", "task-1")).thenReturn(null);
        when(zSetOperations.score("ircs:pipeline:v2:NORMALIZE_VIDEO:inflight", "task-1")).thenReturn(1.0);

        assertTrue(queue.hasOpenTask(PipelineTaskTypes.NORMALIZE_VIDEO, "task-1"));
    }

    @Test
    void completePassesRateBucketAndMetricArgs() {
        PipelineValkeyTask task = new PipelineValkeyTask(
                PipelineTaskTypes.ENRICH_METADATA,
                "task-2",
                UUID.randomUUID(),
                "v2",
                "",
                1,
                "worker-a");

        queue.complete(task);

        CapturedExecution execution = captureExecution();

        assertRateArgs(
                execution,
                6,
                10,
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.ENRICH_METADATA, RateMetricKeys.ACTION_COMPLETED));
    }

    @Test
    void failPassesRateBucketAndMetricArgs() {
        PipelineValkeyTask task = new PipelineValkeyTask(
                PipelineTaskTypes.METADATA_PROVIDER,
                "task-3",
                UUID.randomUUID(),
                "v3",
                "",
                1,
                "worker-a");

        queue.fail(task, false, Duration.ZERO, "boom");

        CapturedExecution execution = captureExecution();

        assertRateArgs(
                execution,
                6,
                12,
                RateMetricKeys.pipelineMetric(PipelineTaskTypes.METADATA_PROVIDER, RateMetricKeys.ACTION_FAILED));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CapturedExecution captureExecution() {
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture(), argsCaptor.capture());
        return new CapturedExecution(keysCaptor.getValue(), argsCaptor.getValue());
    }

    private void assertRateArgs(
            CapturedExecution execution,
            int expectedKeyCount,
            int expectedArgCount,
            String expectedMetricKey) {
        long bucketStart = RateMetricKeys.bucketStartMillis(clock.millis(), Duration.ofSeconds(10));
        assertEquals(expectedKeyCount, execution.keys().size());
        assertEquals(expectedArgCount, execution.args().length);
        assertTrue(execution.keys().contains(RateMetricKeys.bucketKey("ircs:metrics:rate", bucketStart)));
        assertTrue(execution.keys().contains(RateMetricKeys.bucketIndexKey("ircs:metrics:rate")));
        assertEquals(expectedMetricKey, execution.args()[expectedArgCount - 1]);
        assertTrue(List.of(execution.args()).contains(Long.toString(bucketStart)));
        assertTrue(List.of(execution.args()).contains(Long.toString(Duration.ofMinutes(30).toSeconds())));
        assertTrue(List.of(execution.args()).contains(Long.toString(Duration.ofMinutes(31).toMillis())));
    }

    private record CapturedExecution(List<?> keys, Object[] args) {
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<WorkSubmissionGate> gateProvider(WorkSubmissionGate gate) {
        ObjectProvider<WorkSubmissionGate> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
