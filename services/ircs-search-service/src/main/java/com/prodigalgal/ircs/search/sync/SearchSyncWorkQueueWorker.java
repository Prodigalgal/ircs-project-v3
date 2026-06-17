package com.prodigalgal.ircs.search.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPayload;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.search.work-queue.worker", name = "enabled", havingValue = "true")
class SearchSyncWorkQueueWorker {

    private static final String JOB_TYPE = "runtime-work-queue";

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;
    private final SearchSyncProcessor syncProcessor;
    private final WorkerJobAuditWriter auditWriter;
    private final SearchSyncWorkQueueMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private volatile Instant lastRunAt;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile int lastProcessed;
    private volatile int lastFailed;
    private volatile int lastRequeued;
    private volatile String lastError;

    @Value("${app.search.work-queue.worker.batch-size:100}")
    private int batchSize;

    @Value("${app.search.work-queue.worker.max-retries:5}")
    private int maxRetries;

    @Value("${app.search.work-queue.worker.visibility-seconds:300}")
    private long visibilitySeconds;

    @Value("${app.search.work-queue.worker.max-backoff-seconds:300}")
    private long maxBackoffSeconds;

    @Value("${app.search.work-queue.worker.worker-id:${APP_SEARCH_WORK_QUEUE_WORKER_ID:}}")
    private String workerId;

    @Value("${spring.application.name:ircs-search-service}")
    private String applicationName;

    SearchSyncWorkQueueWorker(
            RuntimeWorkQueue workQueue,
            ObjectMapper objectMapper,
            SearchSyncProcessor syncProcessor,
            WorkerJobAuditWriter auditWriter,
            SearchSyncWorkQueueMetrics metrics) {
        this.workQueue = workQueue;
        this.objectMapper = objectMapper;
        this.syncProcessor = syncProcessor;
        this.auditWriter = auditWriter;
        this.metrics = metrics;
    }

    @Scheduled(
            initialDelayString = "${app.search.work-queue.worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.search.work-queue.worker.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            return runOnceLocked();
        } finally {
            running.set(false);
        }
    }

    SearchSyncWorkQueueState state() {
        return new SearchSyncWorkQueueState(
                running.get(),
                workerId(),
                lastRunAt,
                lastSuccessAt,
                lastFailureAt,
                lastProcessed,
                lastFailed,
                lastRequeued,
                consecutiveFailures.get(),
                lastError);
    }

    private int runOnceLocked() {
        Instant startedAt = Instant.now();
        String resolvedWorkerId = workerId();
        lastRunAt = startedAt;
        int processed = 0;
        int failed = 0;
        int requeued = 0;
        RuntimeException fatal = null;
        try {
            int effectiveBatchSize = Math.max(1, batchSize);
            requeued += workQueue.requeueExpired(SearchSyncWorkTypes.RAW, effectiveBatchSize);
            requeued += workQueue.requeueExpired(SearchSyncWorkTypes.UNIFIED, effectiveBatchSize);
            WorkRunResult raw = processItems(SearchSyncWorkTypes.RAW, resolvedWorkerId, effectiveBatchSize);
            WorkRunResult unified = processItems(SearchSyncWorkTypes.UNIFIED, resolvedWorkerId, effectiveBatchSize);
            processed = raw.processed() + unified.processed();
            failed = raw.failed() + unified.failed();
            if (failed > 0) {
                markFailure(processed, failed, requeued, "search sync work item failures");
            } else {
                markSuccess(processed, requeued);
            }
            return processed;
        } catch (RuntimeException ex) {
            fatal = ex;
            markFailure(processed, failed, requeued, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
        } finally {
            metrics.recordRun(
                    resolvedWorkerId,
                    elapsedSince(startedAt),
                    fatal == null && lastFailed == 0 ? "success" : "failed",
                    processed,
                    lastFailed,
                    requeued);
        }
    }

    private WorkRunResult processItems(String taskType, String resolvedWorkerId, int limit) {
        List<RuntimeWorkItem> items = workQueue.claim(
                taskType,
                resolvedWorkerId,
                limit,
                Duration.ofSeconds(Math.max(1, visibilitySeconds)));
        int processed = 0;
        int failed = 0;
        for (RuntimeWorkItem item : items) {
            if (processItem(item, resolvedWorkerId)) {
                processed++;
            } else {
                failed++;
            }
        }
        return new WorkRunResult(processed, failed);
    }

    private boolean processItem(RuntimeWorkItem item, String resolvedWorkerId) {
        Instant startedAt = Instant.now();
        SearchSyncWorkPayload payload = null;
        try {
            payload = objectMapper.readValue(item.payload(), SearchSyncWorkPayload.class);
            syncProcessor.process(payload.toMessage());
            workQueue.complete(item);
            metrics.recordItem(resolvedWorkerId, item, "processed", Instant.now());
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE,
                    item.taskType(),
                    correlationId(payload, item),
                    elapsedSince(startedAt)));
            return true;
        } catch (RuntimeException | JsonProcessingException ex) {
            boolean retryable = item.attempt() < Math.max(1, maxRetries);
            workQueue.fail(
                    item,
                    retryable,
                    retryDelay(item.attempt()),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
            metrics.recordItem(resolvedWorkerId, item, retryable ? "retry" : "dlq", Instant.now());
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE,
                    item.taskType(),
                    correlationId(payload, item),
                    elapsedSince(startedAt),
                    new SearchSyncWorkQueueException("search sync work failed")));
            return false;
        }
    }

    private void markSuccess(int processed, int requeued) {
        lastSuccessAt = Instant.now();
        lastProcessed = processed;
        lastFailed = 0;
        lastRequeued = requeued;
        lastError = null;
        consecutiveFailures.set(0);
    }

    private void markFailure(int processed, int failed, int requeued, String error) {
        lastFailureAt = Instant.now();
        lastProcessed = processed;
        lastFailed = failed;
        lastRequeued = requeued;
        lastError = error;
        consecutiveFailures.incrementAndGet();
    }

    private String workerId() {
        String resolved = workerId;
        if (resolved == null || resolved.isBlank()) {
            synchronized (this) {
                if (workerId == null || workerId.isBlank()) {
                    workerId = WorkerInstanceIds.resolve(applicationName, null);
                }
                resolved = workerId;
            }
        } else {
            resolved = WorkerInstanceIds.resolve(applicationName, resolved);
            workerId = resolved;
        }
        return resolved.trim();
    }

    private Duration retryDelay(int attempt) {
        long delaySeconds = 1L << Math.min(Math.max(0, attempt), 10);
        long maxSeconds = Math.max(1, maxBackoffSeconds);
        return Duration.ofSeconds(Math.min(delaySeconds, maxSeconds));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Search sync work queue audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(SearchSyncWorkPayload payload, RuntimeWorkItem item) {
        if (payload != null && payload.correlationId() != null && !payload.correlationId().isBlank()) {
            return payload.correlationId();
        }
        if (payload != null && payload.entityId() != null) {
            return payload.entityId().toString();
        }
        return item == null ? null : item.aggregateId();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private static class SearchSyncWorkQueueException extends RuntimeException {
        SearchSyncWorkQueueException(String message) {
            super(message);
        }
    }

    private record WorkRunResult(int processed, int failed) {
    }
}
