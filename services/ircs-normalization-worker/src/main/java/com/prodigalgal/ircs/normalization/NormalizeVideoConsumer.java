package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.pipeline.PipelineLease;
import com.prodigalgal.ircs.common.pipeline.PipelineRedisState;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.normalization.listener", name = "enabled", havingValue = "true")
public class NormalizeVideoConsumer {

    private static final Duration LEASE_TTL = Duration.ofMinutes(10);
    private static final Duration COMPLETE_HINT_TTL = Duration.ofHours(6);

    private final NormalizeVideoMessageParser messageParser;
    private final RawVideoNormalizationService normalizationService;
    private final WorkerJobAuditWriter auditWriter;
    private final PipelineRedisState pipelineState;
    private final String workerId;

    public NormalizeVideoConsumer(
            NormalizeVideoMessageParser messageParser,
            RawVideoNormalizationService normalizationService,
            WorkerJobAuditWriter auditWriter,
            PipelineRedisState pipelineState,
            @Value("${spring.application.name:ircs-normalization-worker}") String applicationName,
            @Value("${app.normalization.pipeline.worker-id:${APP_NORMALIZATION_PIPELINE_WORKER_ID:}}")
                    String configuredWorkerId) {
        this.messageParser = messageParser;
        this.normalizationService = normalizationService;
        this.auditWriter = auditWriter;
        this.pipelineState = pipelineState;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
    }

    @RabbitListener(queues = QueueTopic.Names.PROCESS_Q_NORM)
    public void onMessage(Message message) {
        Instant startedAt = Instant.now();
        UUID rawVideoId = null;
        PipelineLease lease = null;
        try {
            PipelineCommand command = messageParser.parse(message);
            rawVideoId = command.rawVideoId();
            Optional<PipelineLease> maybeLease = pipelineState.tryLease(
                    PipelineStep.NORMALIZE_VIDEO,
                    rawVideoId,
                    command.pipelineVersion(),
                    workerId,
                    LEASE_TTL);
            if (maybeLease.isEmpty()) {
                log.info("Skipping raw video normalization because lease is held: rawVideoId={}", rawVideoId);
                recordSucceeded(startedAt, rawVideoId);
                return;
            }
            lease = maybeLease.get();

            log.info(
                    "Processing raw video normalization: rawVideoId={}, pipelineVersion={}",
                    rawVideoId,
                    command.pipelineVersion());
            RawVideoNormalizationService.NormalizationResult result =
                    normalizationService.normalize(rawVideoId, command.pipelineVersion());
            log.info("Raw video normalization finished: rawVideoId={}, status={}", rawVideoId, result.status());
            if ("MISSING".equals(result.status())) {
                throw new AmqpRejectAndDontRequeueException("Normalize raw video not found: " + rawVideoId);
            }
            if (isTerminal(result.status())) {
                pipelineState.markComplete(
                        PipelineStep.NORMALIZE_VIDEO,
                        rawVideoId,
                        command.pipelineVersion(),
                        COMPLETE_HINT_TTL);
            }
            recordSucceeded(startedAt, rawVideoId);
        } catch (RuntimeException ex) {
            recordFailed(startedAt, rawVideoId, ex);
            throw ex;
        } finally {
            releaseLease(lease, rawVideoId);
        }
    }

    private void releaseLease(PipelineLease lease, UUID rawVideoId) {
        if (lease == null) {
            return;
        }
        try {
            if (!pipelineState.release(lease)) {
                log.debug("Normalize video consumer lease was not released by owner: rawVideoId={}", rawVideoId);
            }
        } catch (RuntimeException ex) {
            log.warn("Normalize video consumer lease release failed: rawVideoId={}, error={}", rawVideoId, ex.getMessage());
        }
    }

    private boolean isTerminal(String status) {
        return "READY".equals(status)
                || "SKIPPED".equals(status)
                || "STALE".equals(status)
                || "PERMANENT_FAILURE".equals(status);
    }

    private void recordSucceeded(Instant startedAt, UUID rawVideoId) {
        recordAudit(WorkerJobAuditEvent.succeeded(
                "queue-consumer",
                "normalization.raw-video",
                correlationId(rawVideoId),
                elapsedSince(startedAt)));
    }

    private void recordFailed(Instant startedAt, UUID rawVideoId, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "queue-consumer",
                "normalization.raw-video",
                correlationId(rawVideoId),
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Normalize video consumer audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(UUID rawVideoId) {
        return rawVideoId == null ? null : rawVideoId.toString();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }
}
