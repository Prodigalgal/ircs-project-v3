package com.prodigalgal.ircs.metadata.dispatch.worker;

import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTask;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.metadata.dispatch.application.MetadataDispatchService;
import com.prodigalgal.ircs.metadata.dispatch.dto.MetadataDispatchResult;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetadataDispatchValkeyWorker {

    private final PipelineValkeyQueue pipelineQueue;
    private final MetadataDispatchService dispatchService;
    private final String workerId;
    private final int batchSize;
    private final Duration visibilityTimeout;
    private final Duration retryDelay;

    MetadataDispatchValkeyWorker(
            PipelineValkeyQueue pipelineQueue,
            MetadataDispatchService dispatchService,
            @Value("${spring.application.name:ircs-metadata-worker}") String applicationName,
            @Value("${app.metadata.pipeline.worker-id:${APP_METADATA_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId,
            @Value("${app.metadata.valkey-dispatcher.batch-size:${APP_METADATA_VALKEY_DISPATCHER_BATCH_SIZE:10}}")
                    int batchSize,
            @Value("${app.metadata.valkey-dispatcher.visibility-timeout:PT10M}") Duration visibilityTimeout,
            @Value("${app.metadata.valkey-dispatcher.retry-delay:PT5M}") Duration retryDelay) {
        this.pipelineQueue = pipelineQueue;
        this.dispatchService = dispatchService;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.batchSize = Math.max(1, batchSize);
        this.visibilityTimeout = positive(visibilityTimeout, Duration.ofMinutes(10));
        this.retryDelay = positive(retryDelay, Duration.ofMinutes(5));
    }

    @Scheduled(
            initialDelayString = "${app.metadata.valkey-dispatcher.initial-delay-ms:10000}",
            fixedDelayString = "${app.metadata.valkey-dispatcher.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    @Scheduled(
            initialDelayString = "${app.metadata.valkey-dispatcher.heartbeat-initial-delay-ms:5000}",
            fixedDelayString = "${app.metadata.valkey-dispatcher.heartbeat-fixed-delay-ms:15000}")
    public void heartbeatScheduled() {
        heartbeat();
    }

    int runOnce() {
        heartbeat();
        int reclaimed = pipelineQueue.requeueExpired(PipelineTaskTypes.ENRICH_METADATA, batchSize);
        if (reclaimed > 0) {
            log.info("Requeued expired metadata dispatch tasks from Valkey: count={}", reclaimed);
        }
        List<PipelineValkeyTask> tasks = pipelineQueue.claim(
                PipelineTaskTypes.ENRICH_METADATA,
                workerId,
                batchSize,
                visibilityTimeout);
        tasks.forEach(this::process);
        return tasks.size();
    }

    private void heartbeat() {
        pipelineQueue.heartbeatConsumer(PipelineTaskTypes.ENRICH_METADATA, workerId, visibilityTimeout.plusMinutes(1));
    }

    private void process(PipelineValkeyTask task) {
        try {
            MetadataDispatchResult result = dispatchService.dispatch(task.rawVideoId(), task.pipelineVersion());
            if (!result.videoFound()) {
                pipelineQueue.fail(task, false, Duration.ZERO, "RAW_VIDEO_NOT_FOUND");
                return;
            }
            if ("FAILED".equals(result.status())) {
                pipelineQueue.fail(task, true, retryDelay, "ENRICHMENT_RETRYABLE_FAILURE");
                return;
            }
            pipelineQueue.complete(task);
        } catch (RuntimeException ex) {
            pipelineQueue.fail(task, true, retryDelay, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        }
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
