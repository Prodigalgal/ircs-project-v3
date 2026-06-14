package com.prodigalgal.ircs.search.sync;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.search.listener", name = "enabled", havingValue = "true")
public class SearchSyncConsumer {

    private final SearchSyncMessageParser messageParser;
    private final SearchSyncProcessor syncProcessor;
    private final WorkerJobAuditWriter auditWriter;

    @RabbitListener(queues = QueueTopic.Names.SEARCH_Q_RAW)
    public void onRawMessage(Message amqpMessage) {
        process(amqpMessage, SearchEntityType.RAW_VIDEO, "search.sync.raw");
    }

    @RabbitListener(queues = QueueTopic.Names.SEARCH_Q_UNIFIED)
    public void onUnifiedMessage(Message amqpMessage) {
        process(amqpMessage, SearchEntityType.UNIFIED_VIDEO, "search.sync.unified");
    }

    private void process(Message amqpMessage, SearchEntityType defaultType, String jobName) {
        Instant startedAt = Instant.now();
        SearchSyncMessage message = null;
        try {
            message = messageParser.parse(amqpMessage, defaultType);
            log.debug("Processing search sync message: {}", message);
            syncProcessor.process(message);
            recordAudit(WorkerJobAuditEvent.succeeded(
                    "queue-consumer",
                    jobName,
                    correlationId(message),
                    elapsedSince(startedAt)));
        } catch (RuntimeException ex) {
            recordAudit(WorkerJobAuditEvent.failed(
                    "queue-consumer",
                    jobName,
                    correlationId(message),
                    elapsedSince(startedAt),
                    auditError(message, ex)));
            throw ex;
        }
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Search sync consumer audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(SearchSyncMessage message) {
        return message == null || message.getEntityId() == null ? null : message.getEntityId().toString();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static RuntimeException auditError(SearchSyncMessage message, RuntimeException error) {
        return message == null ? error : new SearchSyncAuditException("search sync processing failed");
    }

    private static class SearchSyncAuditException extends RuntimeException {
        SearchSyncAuditException(String message) {
            super(message);
        }
    }
}
