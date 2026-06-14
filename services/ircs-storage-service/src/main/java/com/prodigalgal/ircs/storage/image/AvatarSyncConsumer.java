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
public class AvatarSyncConsumer {

    private final StorageUuidMessageParser messageParser;
    private final AvatarSyncService avatarSyncService;
    private final WorkerJobAuditWriter auditWriter;

    @RabbitListener(queues = QueueTopic.Names.STORAGE_Q_AVATAR)
    public void onMessage(Message message) {
        Instant startedAt = Instant.now();
        UUID memberId = null;
        try {
            memberId = messageParser.parse(message);
            log.info("Processing avatar sync: {}", memberId);
            avatarSyncService.sync(memberId);
            recordSucceeded(startedAt, memberId);
        } catch (RuntimeException ex) {
            recordFailed(startedAt, memberId, ex);
            throw ex;
        }
    }

    private void recordSucceeded(Instant startedAt, UUID memberId) {
        recordAudit(WorkerJobAuditEvent.succeeded(
                "queue-consumer",
                "storage.avatar-sync",
                correlationId(memberId),
                elapsedSince(startedAt)));
    }

    private void recordFailed(Instant startedAt, UUID memberId, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "queue-consumer",
                "storage.avatar-sync",
                correlationId(memberId),
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Avatar sync audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(UUID id) {
        return id == null ? null : id.toString();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }
}
