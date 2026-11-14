package com.prodigalgal.ircs.aggregation;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPayload;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class AggregationWorkQueueWorker {

    private static final String JOB_TYPE = "runtime-work-queue";
    private static final String JOB_NAME = "aggregation.raw-video";
    private static final long CONSUMER_HEARTBEAT_INTERVAL_NANOS = Duration.ofSeconds(15).toNanos();

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;
    private final AggregationService aggregationService;
    private final WorkerJobAuditWriter auditWriter;
    private final AggregationWorkDeferrer workDeferrer;
    private final ExecutorService triggerExecutor =
            ScheduledTriggers.virtualThreadExecutor("ircs-aggregation-trigger-vt-");
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicLong nextHeartbeatAtNanos = new AtomicLong(System.nanoTime());
    private volatile Instant lastRunAt;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile Instant lastProgressAt;
    private volatile int lastProcessed;
    private volatile int lastFailed;
    private volatile int lastRequeued;
    private volatile String lastRunState = "NEVER_RUN";
    private volatile String lastError;
    private volatile String currentStage;
    private volatile String currentRawVideoId;
    private volatile String currentTaskId;
    private volatile int currentBatchSize;
    private volatile Instant currentStageStartedAt;

    @Value("${app.aggregation.work-queue.worker.batch-size:100}")
    private int batchSize;

    @Value("${app.aggregation.work-queue.worker.processing-chunk-size:5}")
    private int processingChunkSize;

    @Value("${app.aggregation.work-queue.worker.visibility-seconds:600}")
    private long visibilitySeconds;

    @Value("${app.aggregation.work-queue.worker.max-retries:8}")
    private int maxRetries;

    @Value("${app.aggregation.work-queue.worker.max-backoff-seconds:900}")
    private long maxBackoffSeconds;

    @Value("${app.aggregation.work-queue.worker.dependency-delay-seconds:30}")
    private long dependencyDelaySeconds;

    @Value("${app.aggregation.work-queue.worker.contention-delay-seconds:2}")
    private long contentionDelaySeconds;

    @Value("${app.aggregation.work-queue.worker.review-delay-seconds:3600}")
    private long reviewDelaySeconds;

    @Value("${app.aggregation.work-queue.worker.wait-warning-seconds:3600}")
    private long waitWarningSeconds;

    @Value("${app.aggregation.work-queue.worker.worker-id:${APP_AGGREGATION_WORK_QUEUE_WORKER_ID:}}")
    private String workerId;

    @Value("${spring.application.name:ircs-aggregation-worker}")
    private String applicationName;

    AggregationWorkQueueWorker(
            RuntimeWorkQueue workQueue,
            ObjectMapper objectMapper,
            AggregationService aggregationService,
            WorkerJobAuditWriter auditWriter,
            AggregationWorkDeferrer workDeferrer) {
        this.workQueue = workQueue;
        this.objectMapper = objectMapper;
        this.aggregationService = aggregationService;
        this.auditWriter = auditWriter == null ? WorkerJobAuditWriter.noop() : auditWriter;
        this.workDeferrer = workDeferrer;
    }

    @Scheduled(
            initialDelayString = "${app.aggregation.work-queue.worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.aggregation.work-queue.worker.fixed-delay-ms:1000}")
    public void runScheduled() {
        ScheduledTriggers.submit(triggerExecutor, () -> runOnce(), log, "aggregation.work-queue.run");
    }

    @PreDestroy
    void shutdownTriggerExecutor() {
        triggerExecutor.shutdownNow();
    }

    int runOnce() {
        if (!running.compareAndSet(false, true)) {
            lastRunState = "BUSY";
            return 0;
        }
        try {
            return runOnceLocked();
        } finally {
            clearStage();
            running.set(false);
        }
    }

    AggregationWorkQueueState state() {
        Instant stageStartedAt = currentStageStartedAt;
        return new AggregationWorkQueueState(
                running.get(),
                workerId(),
                lastRunAt,
                lastSuccessAt,
                lastFailureAt,
                lastProgressAt,
                lastProcessed,
                lastFailed,
                lastRequeued,
                consecutiveFailures.get(),
                lastRunState,
                lastError,
                currentStage,
                currentRawVideoId,
                currentTaskId,
                currentBatchSize,
                stageStartedAt,
                runningForSeconds(stageStartedAt));
    }

    private int runOnceLocked() {
        Instant startedAt = Instant.now();
        lastRunAt = startedAt;
        int effectiveBatchSize = Math.max(1, batchSize);
        String resolvedWorkerId = workerId();
        int requeued = 0;
        ProcessResult result = ProcessResult.empty();
        try {
            heartbeat(resolvedWorkerId);
            setStage("CLAIM", null, null, effectiveBatchSize);
            List<RuntimeWorkItem> items = workQueue.claim(
                    AggregationWorkTypes.RAW_VIDEO,
                    resolvedWorkerId,
                    effectiveBatchSize,
                    visibilityTimeout());
            if (items.isEmpty()) {
                markSuccess(0, 0);
                return 0;
            }
            lastProgressAt = Instant.now();
            try (AggregationQueueClaimGuard claims = new AggregationQueueClaimGuard(
                    workDeferrer, items, visibilityTimeout(),
                    () -> heartbeat(resolvedWorkerId))) {
                result = process(items, resolvedWorkerId, startedAt);
            }
            requeued = result.deferred();
            if (result.failed() > 0) {
                markFailure(result.processed(), result.failed(), requeued, "aggregation work item failures");
            } else {
                markSuccess(result.processed(), requeued);
            }
            return items.size();
        } catch (RuntimeException ex) {
            markFailure(
                    result.processed(),
                    result.failed(),
                    requeued,
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
            log.warn("Aggregation work queue run failed: {}", ex.getMessage());
            return result.processed();
        }
    }

    private void heartbeat(String resolvedWorkerId) {
        long now = System.nanoTime();
        long next = nextHeartbeatAtNanos.get();
        if (now - next < 0 || !nextHeartbeatAtNanos.compareAndSet(next, now + CONSUMER_HEARTBEAT_INTERVAL_NANOS)) {
            return;
        }
        try {
            workQueue.heartbeatConsumer(
                    AggregationWorkTypes.RAW_VIDEO,
                    resolvedWorkerId,
                    visibilityTimeout().plusMinutes(1));
        } catch (RuntimeException ex) {
            log.warn("Aggregation consumer heartbeat failed: workerId={}", resolvedWorkerId, ex);
        }
    }

    private Duration visibilityTimeout() {
        return Duration.ofSeconds(Math.max(1, Math.min(604800, visibilitySeconds)));
    }

    private ProcessResult process(List<RuntimeWorkItem> items, String resolvedWorkerId, Instant startedAt) {
        if (items.isEmpty()) {
            return ProcessResult.empty();
        }
        Map<UUID, List<RuntimeWorkItem>> itemsByRawVideoId = new LinkedHashMap<>();
        int processed = 0;
        int failed = 0;
        int deferred = 0;
        for (RuntimeWorkItem item : items) {
            if (!AggregationQueueClaimGuard.ownsCurrent(item)) {
                recordStaleClaim(item, null, "RENEW_REJECTED");
                processed++;
                failed++;
                continue;
            }
            setStage("PARSE_PAYLOAD", null, item.taskId(), 1);
            heartbeat(resolvedWorkerId);
            UUID rawVideoId = null;
            try {
                AggregationWorkPayload payload = objectMapper.readValue(item.payload(), AggregationWorkPayload.class);
                if (payload == null || payload.rawVideoId() == null) {
                    throw new IllegalArgumentException("Aggregation payload rawVideoId is required");
                }
                rawVideoId = payload.rawVideoId();
                itemsByRawVideoId.computeIfAbsent(rawVideoId, ignored -> new ArrayList<>()).add(item);
            } catch (RuntimeException ex) {
                failRetryable(item, startedAt, rawVideoId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
                processed++;
                failed++;
                lastProgressAt = Instant.now();
            }
        }
        if (itemsByRawVideoId.isEmpty()) {
            return new ProcessResult(processed, failed, deferred);
        }
        List<Map.Entry<UUID, List<RuntimeWorkItem>>> entries = new ArrayList<>(itemsByRawVideoId.entrySet());
        int chunkSize = Math.max(1, processingChunkSize);
        for (int start = 0; start < entries.size(); start += chunkSize) {
            List<Map.Entry<UUID, List<RuntimeWorkItem>>> chunk =
                    entries.subList(start, Math.min(start + chunkSize, entries.size()));
            ProcessResult chunkResult = processChunk(chunk, startedAt, resolvedWorkerId);
            processed += chunkResult.processed();
            failed += chunkResult.failed();
            deferred += chunkResult.deferred();
        }
        return new ProcessResult(processed, failed, deferred);
    }

    private ProcessResult processChunk(
            List<Map.Entry<UUID, List<RuntimeWorkItem>>> chunk,
            Instant startedAt,
            String resolvedWorkerId) {
        if (chunk.isEmpty()) {
            return ProcessResult.empty();
        }
        int processed = 0;
        int failed = 0;
        int deferred = 0;
        List<Map.Entry<UUID, List<RuntimeWorkItem>>> ownedChunk = new ArrayList<>();
        for (Map.Entry<UUID, List<RuntimeWorkItem>> entry : chunk) {
            List<RuntimeWorkItem> owned = new ArrayList<>();
            for (RuntimeWorkItem item : entry.getValue()) {
                if (AggregationQueueClaimGuard.ownsCurrent(item)) {
                    owned.add(item);
                } else {
                    recordStaleClaim(item, entry.getKey(), "RENEW_REJECTED");
                    processed++;
                    failed++;
                }
            }
            if (!owned.isEmpty()) { ownedChunk.add(Map.entry(entry.getKey(), owned)); }
        }
        if (ownedChunk.isEmpty()) { return new ProcessResult(processed, failed, deferred); }
        List<UUID> rawVideoIds = ownedChunk.stream()
                .map(Map.Entry::getKey)
                .toList();
        try {
            setStage("AGGREGATE_BATCH", rawVideoIds.getFirst(), firstTaskId(ownedChunk), rawVideoIds.size());
            heartbeat(resolvedWorkerId);
            List<AggregationResult> results = aggregationService.aggregateRuntimeWorkBatch(rawVideoIds);
            heartbeat(resolvedWorkerId);
            Map<UUID, AggregationResult> resultByRawVideoId = new LinkedHashMap<>();
            for (AggregationResult result : results) {
                resultByRawVideoId.put(result.rawVideoId(), result);
            }
            for (Map.Entry<UUID, List<RuntimeWorkItem>> entry : ownedChunk) {
                AggregationResult result = resultByRawVideoId.getOrDefault(
                        entry.getKey(),
                        AggregationResult.skipped(entry.getKey(), "MISSING_RAW_VIDEO"));
                for (RuntimeWorkItem item : entry.getValue()) {
                    setStage(
                            "BOUND".equals(result.status()) ? "FINISH_COMPLETE" : "FINISH_FAIL",
                            entry.getKey(),
                            item.taskId(),
                            1);
                    heartbeat(resolvedWorkerId);
                    FinishOutcome outcome = finish(item, startedAt, result);
                    if (outcome == FinishOutcome.FAILED) { failed++; }
                    if (outcome == FinishOutcome.DEFERRED) { deferred++; }
                    processed++;
                    lastProgressAt = Instant.now();
                }
            }
        } catch (RuntimeException ex) {
            for (Map.Entry<UUID, List<RuntimeWorkItem>> entry : ownedChunk) {
                for (RuntimeWorkItem item : entry.getValue()) {
                    setStage("FINISH_FAIL", entry.getKey(), item.taskId(), 1);
                    heartbeat(resolvedWorkerId);
                    if (ex instanceof AggregationDeferredException aggregation) {
                        FinishOutcome outcome = defer(item, entry.getKey(), aggregation.retryAfter(), aggregation.getMessage());
                        if (outcome == FinishOutcome.DEFERRED) { deferred++; }
                        else { failed++; }
                    } else if (ex instanceof AdultAssessmentDeferredException assessment && assessment.waiting()) {
                        setStage("FINISH_DEFER", entry.getKey(), item.taskId(), 1);
                        FinishOutcome outcome = defer(item, entry.getKey(), assessment.retryAfter(), assessment.getMessage());
                        if (outcome == FinishOutcome.DEFERRED) { deferred++; }
                        else { failed++; }
                    } else {
                        if (ex instanceof AdultAssessmentDeferredException assessment) {
                            failRetryable(item, startedAt, entry.getKey(), assessment.getMessage(), assessment.retryAfter());
                        } else {
                            failRetryable(item, startedAt, entry.getKey(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                        }
                        failed++;
                    }
                    processed++;
                    lastProgressAt = Instant.now();
                }
            }
        }
        return new ProcessResult(processed, failed, deferred);
    }

    private FinishOutcome finish(RuntimeWorkItem item, Instant startedAt, AggregationResult result) {
        UUID rawVideoId = result.rawVideoId();
        if ("BOUND".equals(result.status()) || "ALREADY_BOUND".equals(result.status())) {
            if (!AggregationQueueClaimGuard.finishCurrent(item, () -> workQueue.complete(item))) {
                recordStaleClaim(item, rawVideoId, "COMPLETE_REJECTED");
                return FinishOutcome.FAILED;
            }
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(rawVideoId, item),
                    elapsedSince(startedAt)));
            return FinishOutcome.COMPLETED;
        }
        if ("MATCH_LOCK_BUSY".equals(result.status()) || "SOURCE_CHANGED".equals(result.status())) {
            return defer(item, rawVideoId, boundedDelay(contentionDelaySeconds), result.status());
        }
        if ("WAITING_DEPENDENCIES".equals(result.status()) || "NOT_ELIGIBLE".equals(result.status())) {
            return defer(item, rawVideoId, boundedDelay(dependencyDelaySeconds), result.status());
        }
        if ("IDENTITY_CONFLICT".equals(result.status()) || "MERGE_AMBIGUOUS".equals(result.status())) {
            return defer(item, rawVideoId, boundedDelay(reviewDelaySeconds), result.status());
        }
        if ("MISSING_RAW_VIDEO".equals(result.status())) {
            if (!AggregationQueueClaimGuard.finishCurrent(
                    item, () -> workQueue.fail(item, false, Duration.ZERO, result.status()))) {
                recordStaleClaim(item, rawVideoId, "FAIL_REJECTED");
                return FinishOutcome.FAILED;
            }
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(rawVideoId, item),
                    elapsedSince(startedAt),
                    new AggregationWorkQueueException(result.status())));
            return FinishOutcome.FAILED;
        }
        failRetryable(item, startedAt, rawVideoId, result.status());
        return FinishOutcome.FAILED;
    }

    private FinishOutcome defer(RuntimeWorkItem item, UUID rawVideoId, Duration delay, String reason) {
        setStage("FINISH_DEFER", rawVideoId, item.taskId(), 1);
        if (!AggregationQueueClaimGuard.finishCurrent(item, () -> workDeferrer.defer(item, delay, reason))) {
            recordStaleClaim(item, rawVideoId, "DEFER_REJECTED");
            return FinishOutcome.FAILED;
        }
        long ageSeconds = item.createdAt() == null ? 0 : Math.max(0, elapsedSince(item.createdAt()).toSeconds());
        if (ageSeconds >= Math.max(1, waitWarningSeconds)) {
            log.warn("Aggregation work still waiting: rawVideoId={}, taskId={}, reason={}, workAgeSeconds={}, retryAfter={}",
                    rawVideoId, item.taskId(), reason, ageSeconds, delay);
        } else {
            log.info("Aggregation work deferred: rawVideoId={}, taskId={}, reason={}, workAgeSeconds={}, retryAfter={}",
                    rawVideoId, item.taskId(), reason, ageSeconds, delay);
        }
        return FinishOutcome.DEFERRED;
    }

    private static Duration boundedDelay(long seconds) {
        return Duration.ofSeconds(Math.max(2, Math.min(604800, seconds)));
    }

    /**
     * The queue rejected our terminal write because this claim is no longer the current one: the item
     * was recovered by the visibility reaper and possibly taken by another worker. Count it as a
     * failure so it surfaces in run state, and do not record an audit success for work we do not own.
     */
    private void recordStaleClaim(RuntimeWorkItem item, UUID rawVideoId, String reason) {
        AggregationQueueClaimGuard.finishCurrent(item, () -> false);
        log.warn("Aggregation claim is stale, not recording success: rawVideoId={}, taskId={}, reason={}",
                rawVideoId, item.taskId(), reason);
        recordAudit(WorkerJobAuditEvent.failed(
                JOB_TYPE,
                JOB_NAME,
                correlationId(rawVideoId, item),
                Duration.ZERO,
                new AggregationWorkQueueException(reason)));
    }

    private void failRetryable(RuntimeWorkItem item, Instant startedAt, UUID rawVideoId, String reason) {
        failRetryable(item, startedAt, rawVideoId, reason, Duration.ZERO);
    }

    private void failRetryable(RuntimeWorkItem item, Instant startedAt, UUID rawVideoId, String reason, Duration delay) {
        int failureAttempt;
        try {
            failureAttempt = workDeferrer.failureAttempt(item);
        } catch (RuntimeException ex) {
            log.warn("Aggregation retry budget could not be read: taskId={}", item.taskId(), ex);
            recordStaleClaim(item, rawVideoId, "RETRY_BUDGET_READ_FAILED");
            return;
        }
        boolean retryable = failureAttempt < Math.max(1, maxRetries);
        Duration backoff = retryDelay(failureAttempt);
        Duration effectiveDelay = delay.compareTo(backoff) > 0 ? delay : backoff;
        if (!AggregationQueueClaimGuard.finishCurrent(
                item, () -> workQueue.fail(item, retryable, effectiveDelay, reason))) {
            recordStaleClaim(item, rawVideoId, "FAIL_REJECTED");
            return;
        }
        if (retryable) {
            log.info(
                    "Aggregation work item scheduled for retry: rawVideoId={}, taskId={}, attempt={}, reason={}",
                    rawVideoId,
                    item.taskId(),
                    item.attempt(),
                    reason);
        } else {
            log.warn(
                    "Aggregation work item moved to DLQ: rawVideoId={}, taskId={}, attempt={}, reason={}",
                    rawVideoId,
                    item.taskId(),
                    item.attempt(),
                    reason);
        }
        recordAudit(WorkerJobAuditEvent.failed(
                JOB_TYPE,
                JOB_NAME,
                correlationId(rawVideoId, item),
                elapsedSince(startedAt),
                new AggregationWorkQueueException(reason)));
    }

    private String workerId() {
        String resolved = workerId;
        if (resolved == null || resolved.isBlank()) {
            workerId = WorkerInstanceIds.resolve(applicationName, null);
        } else {
            workerId = WorkerInstanceIds.resolve(applicationName, resolved);
        }
        return workerId;
    }

    private Duration retryDelay(int attempt) {
        long delaySeconds = 1L << Math.min(Math.max(0, attempt), 10);
        long maxSeconds = Math.max(1, maxBackoffSeconds);
        return Duration.ofSeconds(Math.min(delaySeconds, maxSeconds));
    }

    private void markSuccess(int processed, int requeued) {
        lastSuccessAt = Instant.now();
        lastProcessed = processed;
        lastFailed = 0;
        lastRequeued = requeued;
        lastError = null;
        lastRunState = requeued > 0 && requeued == processed ? "WAITING" : processed > 0 ? "PROCESSED" : "IDLE";
        consecutiveFailures.set(0);
    }

    private void markFailure(int processed, int failed, int requeued, String error) {
        lastFailureAt = Instant.now();
        lastProcessed = processed;
        lastFailed = failed;
        lastRequeued = requeued;
        lastError = error;
        lastRunState = "DEPENDENCY_FAILURE";
        consecutiveFailures.incrementAndGet();
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Aggregation work queue audit write failed: {}", ex.getMessage());
        }
    }

    private static String correlationId(UUID rawVideoId, RuntimeWorkItem item) {
        if (rawVideoId != null) {
            return rawVideoId.toString();
        }
        return item == null ? null : item.aggregateId();
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private void setStage(String stage, UUID rawVideoId, String taskId, int batchSize) {
        currentStage = stage;
        currentRawVideoId = rawVideoId == null ? null : rawVideoId.toString();
        currentTaskId = taskId;
        currentBatchSize = Math.max(0, batchSize);
        currentStageStartedAt = Instant.now();
    }

    private void clearStage() {
        currentStage = null;
        currentRawVideoId = null;
        currentTaskId = null;
        currentBatchSize = 0;
        currentStageStartedAt = null;
    }

    private long runningForSeconds(Instant stageStartedAt) {
        if (stageStartedAt == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(stageStartedAt, Instant.now()).toSeconds());
    }

    private String firstTaskId(List<Map.Entry<UUID, List<RuntimeWorkItem>>> chunk) {
        if (chunk.isEmpty() || chunk.getFirst().getValue().isEmpty()) {
            return null;
        }
        return chunk.getFirst().getValue().getFirst().taskId();
    }

    private static class AggregationWorkQueueException extends RuntimeException {
        AggregationWorkQueueException(String message) {
            super(message);
        }
    }

    private enum FinishOutcome { COMPLETED, DEFERRED, FAILED }

    private record ProcessResult(int processed, int failed, int deferred) {
        static ProcessResult empty() {
            return new ProcessResult(0, 0, 0);
        }
    }
}
