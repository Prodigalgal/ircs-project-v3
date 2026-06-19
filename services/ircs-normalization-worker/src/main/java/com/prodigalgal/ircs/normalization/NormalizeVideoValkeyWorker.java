package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.pipeline.PipelineTaskTypes;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyTask;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NormalizeVideoValkeyWorker {

    private final PipelineValkeyQueue pipelineQueue;
    private final RawVideoNormalizationService normalizationService;
    private final NormalizationConfigValues configValues;
    private final WorkerJobAuditWriter auditWriter;
    private final String workerId;
    private final int batchSize;
    private final Duration visibilityTimeout;

    NormalizeVideoValkeyWorker(
            PipelineValkeyQueue pipelineQueue,
            RawVideoNormalizationService normalizationService,
            NormalizationConfigValues configValues,
            WorkerJobAuditWriter auditWriter,
            @Value("${spring.application.name:ircs-normalization-worker}") String applicationName,
            @Value("${app.normalization.pipeline.worker-id:${APP_NORMALIZATION_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId,
            @Value("${app.normalization.valkey-worker.batch-size:${APP_NORMALIZATION_VALKEY_WORKER_BATCH_SIZE:10}}")
                    int batchSize,
            @Value("${app.normalization.valkey-worker.visibility-timeout:PT10M}") Duration visibilityTimeout) {
        this.pipelineQueue = pipelineQueue;
        this.normalizationService = normalizationService;
        this.configValues = configValues;
        this.auditWriter = auditWriter == null ? WorkerJobAuditWriter.noop() : auditWriter;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.batchSize = Math.max(1, batchSize);
        this.visibilityTimeout = positive(visibilityTimeout, Duration.ofMinutes(10));
    }

    @Scheduled(
            initialDelayString = "${app.normalization.valkey-worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.normalization.valkey-worker.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    @Scheduled(
            initialDelayString = "${app.normalization.valkey-worker.heartbeat-initial-delay-ms:5000}",
            fixedDelayString = "${app.normalization.valkey-worker.heartbeat-fixed-delay-ms:15000}")
    public void heartbeatScheduled() {
        heartbeat();
    }

    int runOnce() {
        heartbeat();
        int reclaimed = pipelineQueue.requeueExpired(PipelineTaskTypes.NORMALIZE_VIDEO, batchSize);
        if (reclaimed > 0) {
            log.info("Requeued expired normalization tasks from Valkey: count={}", reclaimed);
        }
        List<PipelineValkeyTask> tasks = pipelineQueue.claim(
                PipelineTaskTypes.NORMALIZE_VIDEO,
                workerId,
                batchSize,
                visibilityTimeout);
        tasks.forEach(this::process);
        return tasks.size();
    }

    private void heartbeat() {
        pipelineQueue.heartbeatConsumer(PipelineTaskTypes.NORMALIZE_VIDEO, workerId, visibilityTimeout.plusMinutes(1));
    }

    private void process(PipelineValkeyTask task) {
        Instant startedAt = Instant.now();
        try {
            log.info(
                    "Processing Valkey normalization task: rawVideoId={}, pipelineVersion={}, attempt={}",
                    task.rawVideoId(),
                    task.pipelineVersion(),
                    task.attempt());
            RawVideoNormalizationService.NormalizationResult result =
                    normalizationService.normalize(task.rawVideoId(), task.pipelineVersion());
            if ("MISSING".equals(result.status())) {
                pipelineQueue.fail(task, false, Duration.ZERO, result.reason());
                recordFailed(startedAt, task.rawVideoId(), new IllegalStateException(result.reason()));
                return;
            }
            if ("FAILED".equals(result.status())) {
                pipelineQueue.fail(task, true, retryDelay(task.attempt()), result.reason());
                recordSucceeded(startedAt, task.rawVideoId());
                return;
            }
            pipelineQueue.complete(task);
            recordSucceeded(startedAt, task.rawVideoId());
        } catch (RuntimeException ex) {
            pipelineQueue.fail(task, true, retryDelay(task.attempt()), ex.getClass().getSimpleName() + ": " + ex.getMessage());
            recordFailed(startedAt, task.rawVideoId(), ex);
        }
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(10, Math.max(0, attempt - 1));
        return Duration.ofSeconds(Math.max(1L, configValues.backoffBaseSeconds()) * multiplier);
    }

    private void recordSucceeded(Instant startedAt, UUID rawVideoId) {
        recordAudit(WorkerJobAuditEvent.succeeded(
                "valkey-worker",
                "normalization.raw-video",
                correlationId(rawVideoId),
                elapsedSince(startedAt)));
    }

    private void recordFailed(Instant startedAt, UUID rawVideoId, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "valkey-worker",
                "normalization.raw-video",
                correlationId(rawVideoId),
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Normalize Valkey worker audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(UUID rawVideoId) {
        return rawVideoId == null ? null : rawVideoId.toString();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
