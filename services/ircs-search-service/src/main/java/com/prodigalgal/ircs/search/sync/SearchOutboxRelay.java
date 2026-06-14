package com.prodigalgal.ircs.search.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.search.outbox.relay", name = "enabled", havingValue = "true")
class SearchOutboxRelay {

    private static final String JOB_TYPE = "scheduler";
    private static final String JOB_NAME = "search.outbox.relay";

    private final SearchSyncOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final WorkerJobAuditWriter auditWriter;
    private final SearchOutboxRelayMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private volatile Instant lastRunAt;
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile int lastPublished;
    private volatile int lastRestored;
    private volatile String lastError;

    @Value("${app.search.outbox.relay.batch-size:100}")
    private int batchSize;

    @Value("${app.search.outbox.relay.max-retries:5}")
    private int maxRetries;

    @Value("${app.search.outbox.relay.lease-seconds:300}")
    private long leaseSeconds;

    @Value("${app.search.outbox.relay.max-backoff-seconds:300}")
    private long maxBackoffSeconds;

    @Value("${app.search.outbox.relay.worker-id:${APP_SEARCH_OUTBOX_RELAY_WORKER_ID:}}")
    private String workerId;

    @Value("${spring.application.name:ircs-search-service}")
    private String applicationName;
    SearchOutboxRelay(
            SearchSyncOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            WorkerJobAuditWriter auditWriter,
            SearchOutboxRelayMetrics metrics) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.auditWriter = auditWriter;
        this.metrics = metrics;
    }

    @Scheduled(
            initialDelayString = "${app.search.outbox.relay.initial-delay-ms:30000}",
            fixedDelayString = "${app.search.outbox.relay.fixed-delay-ms:1000}")
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

    private int runOnceLocked() {
        Instant startedAt = Instant.now();
        String resolvedWorkerId = workerId();
        lastRunAt = startedAt;
        int published = 0;
        int restored = 0;
        RuntimeException failure = null;
        try {
            restored = repository.restoreExpiredProcessingLeases();
            List<SearchSyncTaskRecord> tasks = repository.claimDue(
                    Math.max(1, batchSize),
                    resolvedWorkerId,
                    Duration.ofSeconds(Math.max(1, leaseSeconds)));
            if (tasks.isEmpty()) {
                markSuccess(0, restored);
                return 0;
            }
            Map<EntityKey, List<SearchSyncTaskRecord>> grouped = tasks.stream()
                    .collect(Collectors.groupingBy(task -> new EntityKey(task.entityType(), task.entityId())));
            for (List<SearchSyncTaskRecord> group : grouped.values()) {
                published += publishGroup(group, resolvedWorkerId);
            }
            recordAudit(WorkerJobAuditEvent.succeeded(
                    JOB_TYPE,
                    JOB_NAME,
                    auditCorrelation(resolvedWorkerId, published, restored),
                    elapsedSince(startedAt)));
            markSuccess(published, restored);
            return published;
        } catch (RuntimeException ex) {
            failure = ex;
            recordAudit(WorkerJobAuditEvent.failed(
                    JOB_TYPE,
                    JOB_NAME,
                    auditCorrelation(resolvedWorkerId, published, restored),
                    elapsedSince(startedAt),
                    ex));
            markFailure(published, restored, ex);
            throw ex;
        } finally {
            metrics.recordRun(
                    resolvedWorkerId,
                    elapsedSince(startedAt),
                    failure == null ? "success" : "failed",
                    published,
                    restored);
        }
    }

    SearchOutboxRelayState state() {
        return new SearchOutboxRelayState(
                running.get(),
                workerId(),
                lastRunAt,
                lastSuccessAt,
                lastFailureAt,
                lastPublished,
                lastRestored,
                consecutiveFailures.get(),
                lastError);
    }

    private int publishGroup(List<SearchSyncTaskRecord> group, String resolvedWorkerId) {
        if (group.isEmpty()) {
            return 0;
        }
        List<SearchSyncTaskRecord> ordered = group.stream()
                .sorted(Comparator
                        .comparing(SearchSyncTaskRecord::createdAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(SearchSyncTaskRecord::id))
                .toList();
        SearchSyncTaskRecord latest = ordered.getLast();
        try {
            publish(latest);
            repository.deleteTasks(ordered.stream().map(SearchSyncTaskRecord::id).toList());
            metrics.recordGroupPublished(resolvedWorkerId, latest, Instant.now());
            return 1;
        } catch (RuntimeException ex) {
            log.error("Failed to relay search outbox group entityType={}, entityId={}",
                    latest.entityType(), latest.entityId(), ex);
            metrics.recordGroupFailed(resolvedWorkerId, latest);
            repository.markFailed(
                    ordered,
                    Math.max(1, maxRetries),
                    Duration.ofSeconds(Math.max(1, maxBackoffSeconds)),
                    ex);
            return 0;
        }
    }

    private void markSuccess(int published, int restored) {
        lastSuccessAt = Instant.now();
        lastPublished = published;
        lastRestored = restored;
        lastError = null;
        consecutiveFailures.set(0);
    }

    private void markFailure(int published, int restored, RuntimeException ex) {
        lastFailureAt = Instant.now();
        lastPublished = published;
        lastRestored = restored;
        lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
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

    private static String auditCorrelation(String workerId, int published, int restored) {
        return "workerId=" + workerId + ",published=" + published + ",restored=" + restored;
    }

    private void publish(SearchSyncTaskRecord task) {
        QueueTopic topic = task.entityType() == SearchEntityType.RAW_VIDEO
                ? QueueTopic.SEARCH_SYNC_RAW
                : QueueTopic.SEARCH_SYNC_UNIFIED;
        try {
            String payload = objectMapper.writeValueAsString(
                    new SearchSyncMessage(task.entityId(), task.entityType(), task.operation()));
            rabbitTemplate.convertAndSend(topic.exchange(), topic.routingKey(), payload, message -> {
                message.getMessageProperties().setContentType("application/json");
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                if (task.correlationId() != null) {
                    message.getMessageProperties().setCorrelationId(task.correlationId());
                }
                return message;
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize search sync message", ex);
        }
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Search outbox relay audit write failed: {}", ex.getMessage());
        }
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private record EntityKey(SearchEntityType entityType, UUID entityId) {
    }
}
