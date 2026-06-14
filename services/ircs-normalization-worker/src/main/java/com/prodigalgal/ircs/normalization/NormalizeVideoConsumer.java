package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.normalization.listener", name = "enabled", havingValue = "true")
public class NormalizeVideoConsumer {

    private final NormalizeVideoMessageParser messageParser;
    private final RawVideoNormalizationService normalizationService;
    private final WorkerJobAuditWriter auditWriter;

    @RabbitListener(queues = QueueTopic.Names.PROCESS_Q_NORM)
    public void onMessage(Message message) {
        Instant startedAt = Instant.now();
        UUID rawVideoId = null;
        try {
            rawVideoId = messageParser.parse(message);
            log.info("Processing raw video normalization: rawVideoId={}", rawVideoId);
            RawVideoNormalizationService.NormalizationResult result = normalizationService.normalize(rawVideoId);
            log.info("Raw video normalization finished: rawVideoId={}, status={}", rawVideoId, result.status());
            recordSucceeded(startedAt, rawVideoId);
        } catch (RuntimeException ex) {
            recordFailed(startedAt, rawVideoId, ex);
            throw ex;
        }
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
