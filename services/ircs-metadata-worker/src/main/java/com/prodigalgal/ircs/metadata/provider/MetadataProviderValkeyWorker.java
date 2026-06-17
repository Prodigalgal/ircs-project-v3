package com.prodigalgal.ircs.metadata.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTask;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.metadata.dispatch.MetadataProviderTaskPublisher.MetadataProviderTaskPayload;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.metadata.valkey-provider-worker", name = "enabled", havingValue = "true")
public class MetadataProviderValkeyWorker {

    private final PipelineValkeyQueue pipelineQueue;
    private final MetadataProviderWorker providerWorker;
    private final ObjectMapper objectMapper;
    private final String workerId;
    private final int batchSize;
    private final Duration visibilityTimeout;
    private final Duration retryDelay;

    MetadataProviderValkeyWorker(
            PipelineValkeyQueue pipelineQueue,
            MetadataProviderWorker providerWorker,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:ircs-metadata-worker}") String applicationName,
            @Value("${app.metadata.pipeline.worker-id:${APP_METADATA_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId,
            @Value("${app.metadata.valkey-provider-worker.batch-size:${APP_METADATA_VALKEY_PROVIDER_BATCH_SIZE:10}}")
                    int batchSize,
            @Value("${app.metadata.valkey-provider-worker.visibility-timeout:PT10M}") Duration visibilityTimeout,
            @Value("${app.metadata.valkey-provider-worker.retry-delay:PT2M}") Duration retryDelay) {
        this.pipelineQueue = pipelineQueue;
        this.providerWorker = providerWorker;
        this.objectMapper = objectMapper;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.batchSize = Math.max(1, batchSize);
        this.visibilityTimeout = positive(visibilityTimeout, Duration.ofMinutes(10));
        this.retryDelay = positive(retryDelay, Duration.ofMinutes(2));
    }

    @Scheduled(
            initialDelayString = "${app.metadata.valkey-provider-worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.metadata.valkey-provider-worker.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        int reclaimed = pipelineQueue.requeueExpired(PipelineTaskTypes.METADATA_PROVIDER, batchSize);
        if (reclaimed > 0) {
            log.info("Requeued expired metadata provider tasks from Valkey: count={}", reclaimed);
        }
        List<PipelineValkeyTask> tasks = pipelineQueue.claim(
                PipelineTaskTypes.METADATA_PROVIDER,
                workerId,
                batchSize,
                visibilityTimeout);
        tasks.forEach(this::process);
        return tasks.size();
    }

    private void process(PipelineValkeyTask task) {
        try {
            MetadataProviderTaskPayload payload =
                    objectMapper.readValue(task.payload(), MetadataProviderTaskPayload.class);
            providerWorker.execute(payload.context(), payload.provider());
            pipelineQueue.complete(task);
        } catch (RuntimeException ex) {
            pipelineQueue.fail(task, true, retryDelay, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            pipelineQueue.fail(task, true, retryDelay, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw new IllegalStateException("Unable to process metadata provider Valkey task", ex);
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
