package com.prodigalgal.ircs.aggregation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPayload;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class AggregationWorkQueueWorker {

    private static final String ENABLED_KEY = "app.aggregation.work-queue.worker.enabled";
    private static final long ENABLED_CACHE_TTL_NANOS = Duration.ofSeconds(30).toNanos();
    private static final String JOB_TYPE = "runtime-work-queue";
    private static final String JOB_NAME = "aggregation.raw-video";

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;
    private final AggregationService aggregationService;
    private final WorkerJobAuditWriter auditWriter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Boolean cachedEnabled;
    private volatile long cachedEnabledExpiresAtNanos;

    @Autowired(required = false)
    private SystemConfigRepository systemConfigRepository;

    @Value("${app.aggregation.work-queue.worker.enabled:false}")
    private boolean enabledByDeployment = true;

    @Value("${app.aggregation.work-queue.worker.batch-size:100}")
    private int batchSize;

    @Value("${app.aggregation.work-queue.worker.visibility-seconds:600}")
    private long visibilitySeconds;

    @Value("${app.aggregation.work-queue.worker.max-retries:8}")
    private int maxRetries;

    @Value("${app.aggregation.work-queue.worker.max-backoff-seconds:900}")
    private long maxBackoffSeconds;

    @Value("${app.aggregation.work-queue.worker.worker-id:${APP_AGGREGATION_WORK_QUEUE_WORKER_ID:}}")
    private String workerId;

    @Value("${spring.application.name:ircs-aggregation-worker}")
    private String applicationName;

    AggregationWorkQueueWorker(
            RuntimeWorkQueue workQueue,
            ObjectMapper objectMapper,
            AggregationService aggregationService,
            WorkerJobAuditWriter auditWriter) {
        this.workQueue = workQueue;
        this.objectMapper = objectMapper;
        this.aggregationService = aggregationService;
        this.auditWriter = auditWriter == null ? WorkerJobAuditWriter.noop() : auditWriter;
    }

    @Scheduled(
            initialDelayString = "${app.aggregation.work-queue.worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.aggregation.work-queue.worker.fixed-delay-ms:1000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        if (!workerEnabled()) {
            return 0;
        }
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            return runOnceLocked();
        } finally {
            running.set(false);
        }
    }

    private int runOnceLocked() {
        int effectiveBatchSize = Math.max(1, batchSize);
        int requeued = workQueue.requeueExpired(AggregationWorkTypes.RAW_VIDEO, effectiveBatchSize);
        if (requeued > 0) {
            log.info("Requeued expired aggregation work: count={}", requeued);
        }
        List<RuntimeWorkItem> items = workQueue.claim(
                AggregationWorkTypes.RAW_VIDEO,
                workerId(),
                effectiveBatchSize,
                Duration.ofSeconds(Math.max(1, visibilitySeconds)));
        process(items);
        return items.size();
    }

    private void process(List<RuntimeWorkItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Instant startedAt = Instant.now();
        Map<UUID, List<RuntimeWorkItem>> itemsByRawVideoId = new LinkedHashMap<>();
        for (RuntimeWorkItem item : items) {
            UUID rawVideoId = null;
            try {
                AggregationWorkPayload payload = objectMapper.readValue(item.payload(), AggregationWorkPayload.class);
                rawVideoId = payload.rawVideoId();
                itemsByRawVideoId.computeIfAbsent(rawVideoId, ignored -> new ArrayList<>()).add(item);
            } catch (RuntimeException | JsonProcessingException ex) {
                failRetryable(item, startedAt, rawVideoId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
        if (itemsByRawVideoId.isEmpty()) {
            return;
        }
        try {
            List<AggregationResult> results =
                    aggregationService.aggregateRuntimeWorkBatch(List.copyOf(itemsByRawVideoId.keySet()));
            Map<UUID, AggregationResult> resultByRawVideoId = new LinkedHashMap<>();
            for (AggregationResult result : results) {
                resultByRawVideoId.put(result.rawVideoId(), result);
            }
            for (Map.Entry<UUID, List<RuntimeWorkItem>> entry : itemsByRawVideoId.entrySet()) {
                AggregationResult result = resultByRawVideoId.getOrDefault(
                        entry.getKey(),
                        AggregationResult.skipped(entry.getKey(), "MISSING_RAW_VIDEO"));
                for (RuntimeWorkItem item : entry.getValue()) {
                    finish(item, startedAt, result);
                }
            }
        } catch (RuntimeException ex) {
            for (Map.Entry<UUID, List<RuntimeWorkItem>> entry : itemsByRawVideoId.entrySet()) {
                for (RuntimeWorkItem item : entry.getValue()) {
                    failRetryable(item, startedAt, entry.getKey(), ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private void finish(RuntimeWorkItem item, Instant startedAt, AggregationResult result) {
        UUID rawVideoId = result.rawVideoId();
        if ("BOUND".equals(result.status())) {
            workQueue.complete(item);
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(rawVideoId, item),
                    elapsedSince(startedAt)));
            return;
        }
        if ("MISSING_RAW_VIDEO".equals(result.status())) {
            workQueue.fail(item, false, Duration.ZERO, result.status());
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE,
                    JOB_NAME,
                    correlationId(rawVideoId, item),
                    elapsedSince(startedAt),
                    new AggregationWorkQueueException(result.status())));
            return;
        }
        failRetryable(item, startedAt, rawVideoId, result.status());
    }

    private void failRetryable(RuntimeWorkItem item, Instant startedAt, UUID rawVideoId, String reason) {
        boolean retryable = item.attempt() < Math.max(1, maxRetries);
        workQueue.fail(item, retryable, retryDelay(item.attempt()), reason);
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

    private boolean workerEnabled() {
        SystemConfigRepository repository = systemConfigRepository;
        if (repository == null) {
            return enabledByDeployment;
        }
        long now = System.nanoTime();
        Boolean value = cachedEnabled;
        if (value != null && now < cachedEnabledExpiresAtNanos) {
            return value;
        }
        synchronized (this) {
            value = cachedEnabled;
            if (value != null && now < cachedEnabledExpiresAtNanos) {
                return value;
            }
            boolean refreshed = enabledByDeployment;
            try {
                repository.evict(ENABLED_KEY);
                refreshed = repository.findValue(ENABLED_KEY)
                        .map(raw -> parseBoolean(raw, enabledByDeployment))
                        .orElse(enabledByDeployment);
            } catch (RuntimeException ex) {
                log.warn("Unable to read aggregation worker enabled config: {}", ex.getMessage());
            }
            cachedEnabled = refreshed;
            cachedEnabledExpiresAtNanos = now + ENABLED_CACHE_TTL_NANOS;
            return refreshed;
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "y", "on", "enabled" -> true;
            case "false", "0", "no", "n", "off", "disabled" -> false;
            default -> defaultValue;
        };
    }

    private static class AggregationWorkQueueException extends RuntimeException {
        AggregationWorkQueueException(String message) {
            super(message);
        }
    }
}
