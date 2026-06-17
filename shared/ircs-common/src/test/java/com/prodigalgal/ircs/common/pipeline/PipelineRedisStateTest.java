package com.prodigalgal.ircs.common.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PipelineRedisStateTest {

    private final StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
    private final PipelineRedisState state = new PipelineRedisState(redisTemplate);

    @Test
    void tryLeaseReturnsLeaseWhenRedisSetNxSucceeds() {
        UUID rawVideoId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                        eq("ircs:pipeline:lease:NORMALIZE_VIDEO:" + rawVideoId + ":v1"),
                        any(String.class),
                        eq(Duration.ofMinutes(10))))
                .thenReturn(true);

        Optional<PipelineLease> lease = state.tryLease(
                PipelineStep.NORMALIZE_VIDEO,
                rawVideoId,
                "v1",
                "worker-1",
                Duration.ofMinutes(10));

        assertThat(lease).isPresent();
        assertThat(lease.orElseThrow().ownerId()).isEqualTo("worker-1");
    }

    @Test
    void markCompleteStoresShortLivedHintOnly() {
        UUID rawVideoId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        state.markComplete(PipelineStep.ENRICH_METADATA, rawVideoId, null, Duration.ofHours(6));

        verify(valueOperations).set(
                eq("ircs:pipeline:complete:ENRICH_METADATA:" + rawVideoId + ":_"),
                any(String.class),
                eq(Duration.ofHours(6)));
    }
}
