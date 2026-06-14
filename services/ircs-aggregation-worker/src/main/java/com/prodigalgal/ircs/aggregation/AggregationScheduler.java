package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.aggregation.scheduler", name = "enabled", havingValue = "true")
public class AggregationScheduler {

    static final String JOB_TYPE_SCHEDULER = "scheduler";
    static final String JOB_NAME_AGGREGATION_BATCH = "aggregation.batch";
    static final String JOB_NAME_RESET_STUCK = "aggregation.reset-stuck";

    private final AggregationService aggregationService;
    private final WorkerJobAuditWriter auditWriter;
    private final AggregationConfigValues configValues;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.aggregation.stuck-timeout-minutes:30}")
    private int stuckTimeoutMinutes;
    AggregationScheduler(
            AggregationService aggregationService,
            WorkerJobAuditWriter auditWriter,
            AggregationConfigValues configValues) {
        this.aggregationService = aggregationService;
        this.auditWriter = auditWriter;
        this.configValues = configValues;
    }

    @Scheduled(fixedDelayString = "${app.aggregation.scheduler.fixed-delay-ms:5000}")
    public void runBatch() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            runBatchLocked();
        } finally {
            running.set(false);
        }
    }

    private void runBatchLocked() {
        Instant startedAt = Instant.now();
        String auditJobName = JOB_NAME_AGGREGATION_BATCH;
        try {
            int batchSize = configValues.batchSize();
            int processed = aggregationService.aggregateBatch(batchSize);
            if (processed == 0) {
                auditJobName = JOB_NAME_RESET_STUCK;
                aggregationService.resetStuckProcessing(stuckTimeoutMinutes, batchSize);
            }
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE_SCHEDULER,
                    auditJobName,
                    null,
                    elapsedSince(startedAt)));
        } catch (Exception ex) {
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE_SCHEDULER,
                    auditJobName,
                    null,
                    elapsedSince(startedAt),
                    ex));
            log.error("Aggregation batch failed", ex);
        }
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Aggregation scheduler audit write failed", ex);
        }
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }
}
