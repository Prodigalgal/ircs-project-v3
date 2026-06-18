package com.prodigalgal.ircs.common.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class PipelineValkeyQueueRateMetricsTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-18T00:00:05Z"), ZoneOffset.UTC);
    private final PipelineValkeyQueue queue = new PipelineValkeyQueue(
            redisTemplate,
            clock,
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
}
