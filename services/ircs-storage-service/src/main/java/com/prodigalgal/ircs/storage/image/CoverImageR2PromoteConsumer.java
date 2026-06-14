package com.prodigalgal.ircs.storage.image;

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
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class CoverImageR2PromoteConsumer {

    private final StorageUuidMessageParser messageParser;
    private final CoverImageR2PromoteService promoteService;
    private final WorkerJobAuditWriter auditWriter;

    @RabbitListener(queues = QueueTopic.Names.STORAGE_Q_COVER_R2_PROMOTE)
    public void onMessage(Message message) {
        Instant startedAt = Instant.now();
        UUID imageId = null;
        try {
            imageId = messageParser.parse(message);
            CoverImageR2PromoteService.CoverImageR2PromoteResult result = promoteService.promote(imageId);
            if (result.scheduled()) {
                log.info("Processed cover image R2 promote command: {}", imageId);
                recordSucceeded(startedAt, imageId);
            } else {
                log.debug("Skipped cover image R2 promote command: {}, reason={}", imageId, result.reason());
                recordSkipped(startedAt, imageId, result.reason());
            }
        } catch (RuntimeException ex) {
            recordFailed(startedAt, imageId, ex);
            throw ex;
        }
    }

    private void recordSucceeded(Instant startedAt, UUID imageId) {
        recordAudit(WorkerJobAuditEvent.succeeded(
                "queue-consumer",
                "storage.cover-r2-promote",
                correlationId(imageId),
                elapsedSince(startedAt)));
    }

    private void recordSkipped(Instant startedAt, UUID imageId, String reason) {
        recordAudit(new WorkerJobAuditEvent(
                "queue-consumer",
                "storage.cover-r2-promote",
                correlationId(imageId),
                "skipped",
                elapsedSince(startedAt),
                new StorageAuditException(reason)));
    }

    private void recordFailed(Instant startedAt, UUID imageId, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "queue-consumer",
                "storage.cover-r2-promote",
                correlationId(imageId),
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Cover image R2 promote audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(UUID id) {
        return id == null ? null : id.toString();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static class StorageAuditException extends RuntimeException {
        StorageAuditException(String message) {
            super(message);
        }
    }
}
