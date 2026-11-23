package com.prodigalgal.ircs.ingestion;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class SourceRecordIngestConsumer {

    static final String JOB_TYPE = "queue-consumer";
    static final String JOB_NAME = "ingestion.raw-video";

    private final IngestionMessageParser messageParser;
    private final SourceRecordIngestionService ingestionService;
    private final WorkerJobAuditWriter auditWriter;

    @RabbitListener(queues = QueueTopic.Names.INGEST_Q, autoStartup = "${app.ingestion.listener.enabled:false}")
    public void onMessage(Message message) {
        Instant startedAt = Instant.now();
        IngestionItem item = null;
        try {
            item = messageParser.parse(message);
            log.info("Processing raw video ingestion: sourceHash={}, force={}",
                    item.video().sourceHash(), item.forceIngest());
            ingestionService.ingest(item);
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(item),
                    elapsedSince(startedAt)));
        } catch (RuntimeException ex) {
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(item),
                    elapsedSince(startedAt),
                    new SourceRecordIngestAuditException("raw video ingestion failed")));
            throw ex;
        }
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Raw video ingestion audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(IngestionItem item) {
        if (item == null || item.video() == null) {
            return null;
        }
        return item.video().sourceHash();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static class SourceRecordIngestAuditException extends RuntimeException {
        SourceRecordIngestAuditException(String message) {
            super(message);
        }
    }
}
