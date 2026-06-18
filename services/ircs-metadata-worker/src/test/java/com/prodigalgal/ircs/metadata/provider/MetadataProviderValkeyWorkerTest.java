package com.prodigalgal.ircs.metadata.provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTask;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.dispatch.MetadataProviderTaskPublisher.MetadataProviderTaskPayload;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MetadataProviderValkeyWorkerTest {

    private final PipelineValkeyQueue pipelineQueue = org.mockito.Mockito.mock(PipelineValkeyQueue.class);
    private final MetadataProviderWorker providerWorker = org.mockito.Mockito.mock(MetadataProviderWorker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void continuesBatchAfterOneTaskFails() throws Exception {
        PipelineValkeyTask first = task("task-1");
        PipelineValkeyTask second = task("task-2");
        MetadataProviderValkeyWorker worker = worker(1);

        when(pipelineQueue.claim(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                any(),
                eq(2),
                eq(Duration.ofMinutes(10))))
                .thenReturn(List.of(first, second));
        doThrow(new MetadataProviderRetryableException("RATE_LIMITED", "limited"))
                .doNothing()
                .when(providerWorker)
                .execute(any(MetadataSearchContext.class), eq(ProviderType.TMDB));

        org.assertj.core.api.Assertions.assertThat(worker.runOnce()).isEqualTo(2);

        verify(pipelineQueue).fail(
                eq(first),
                eq(true),
                eq(Duration.ofMinutes(2)),
                org.mockito.ArgumentMatchers.contains("limited"));
        verify(pipelineQueue).complete(second);
        worker.shutdown();
    }

    @Test
    void claimsConfiguredBatchWhenParallelismIsEnabled() throws Exception {
        PipelineValkeyTask first = task("task-1");
        PipelineValkeyTask second = task("task-2");
        MetadataProviderValkeyWorker worker = worker(4);

        when(pipelineQueue.claim(
                eq(PipelineTaskTypes.METADATA_PROVIDER),
                any(),
                eq(2),
                eq(Duration.ofMinutes(10))))
                .thenReturn(List.of(first, second));

        org.assertj.core.api.Assertions.assertThat(worker.runOnce()).isEqualTo(2);

        verify(pipelineQueue).complete(first);
        verify(pipelineQueue).complete(second);
        worker.shutdown();
    }

    private MetadataProviderValkeyWorker worker(int parallelism) {
        return new MetadataProviderValkeyWorker(
                pipelineQueue,
                providerWorker,
                objectMapper,
                "ircs-metadata-worker",
                "worker-test",
                2,
                parallelism,
                Duration.ofMinutes(10),
                Duration.ofMinutes(2));
    }

    private PipelineValkeyTask task(String taskId) throws Exception {
        UUID videoId = UUID.randomUUID();
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(videoId)
                .title("The Matrix")
                .pipelineVersion("v1")
                .build();
        String payload = objectMapper.writeValueAsString(new MetadataProviderTaskPayload(ProviderType.TMDB, context));
        return new PipelineValkeyTask(
                PipelineTaskTypes.METADATA_PROVIDER,
                taskId,
                videoId,
                "v1",
                payload,
                0,
                "worker-test");
    }
}
