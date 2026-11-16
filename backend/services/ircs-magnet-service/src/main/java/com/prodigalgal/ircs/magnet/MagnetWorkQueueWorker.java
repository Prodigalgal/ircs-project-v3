package com.prodigalgal.ircs.magnet;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(RuntimeWorkQueue.class)
@Slf4j
class MagnetWorkQueueWorker {

    private static final String ENABLED_KEY = "app.magnet.work-queue.worker.enabled";
    private static final String BATCH_SIZE_KEY = "app.magnet.work-queue.worker.batch-size";
    private static final String VISIBILITY_SECONDS_KEY = "app.magnet.work-queue.worker.visibility-seconds";
    private static final String MAX_RETRIES_KEY = "app.magnet.work-queue.worker.max-retries";
    private static final String MAX_BACKOFF_SECONDS_KEY = "app.magnet.work-queue.worker.max-backoff-seconds";
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_VISIBILITY_SECONDS = 3600;
    private static final int MAX_RETRIES = 20;
    private static final int MAX_BACKOFF_SECONDS = 3600;

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;
    private final MagnetQueryService magnetQueryService;
    private final MagnetWorkLeaseStore leaseStore;
    private final RuntimeConfigService runtimeConfig;
    private final ExecutorService triggerExecutor =
            ScheduledTriggers.virtualThreadExecutor("ircs-magnet-worker-trigger-vt-");
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.magnet.work-queue.worker.enabled:true}")
    private boolean enabledByDeployment;

    @Value("${app.magnet.work-queue.worker.batch-size:5}")
    private int batchSizeByDeployment;

    @Value("${app.magnet.work-queue.worker.visibility-seconds:900}")
    private int visibilitySecondsByDeployment;

    @Value("${app.magnet.work-queue.worker.max-retries:3}")
    private int maxRetriesByDeployment;

    @Value("${app.magnet.work-queue.worker.max-backoff-seconds:900}")
    private int maxBackoffSecondsByDeployment;

    @Value("${app.magnet.work-queue.worker.worker-id:${APP_MAGNET_WORK_QUEUE_WORKER_ID:}}")
    private String workerId;

    @Value("${spring.application.name:ircs-magnet-service}")
    private String applicationName;

    MagnetWorkQueueWorker(
            RuntimeWorkQueue workQueue,
            ObjectMapper objectMapper,
            MagnetQueryService magnetQueryService,
            MagnetWorkLeaseStore leaseStore,
            ObjectProvider<RuntimeConfigService> runtimeConfigProvider) {
        this.workQueue = workQueue;
        this.objectMapper = objectMapper;
        this.magnetQueryService = magnetQueryService;
        this.leaseStore = leaseStore;
        this.runtimeConfig = runtimeConfigProvider == null ? null : runtimeConfigProvider.getIfAvailable();
    }

    @Scheduled(
            initialDelayString = "${app.magnet.work-queue.worker.initial-delay-ms:10000}",
            fixedDelayString = "${app.magnet.work-queue.worker.fixed-delay-ms:1000}")
    void runScheduled() {
        ScheduledTriggers.submit(triggerExecutor, () -> runOnce(), log, "magnet.work-queue.run");
    }

    @Scheduled(
            initialDelayString = "${app.magnet.work-queue.worker.heartbeat-initial-delay-ms:5000}",
            fixedDelayString = "${app.magnet.work-queue.worker.heartbeat-fixed-delay-ms:15000}")
    void heartbeatScheduled() {
        ScheduledTriggers.submit(triggerExecutor, this::heartbeatIfEnabled, log, "magnet.work-queue.heartbeat");
    }

    @PreDestroy
    void shutdownTriggerExecutor() {
        triggerExecutor.shutdownNow();
    }

    int runOnce() {
        if (!enabled() || !running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            return runOnceLocked();
        } finally {
            running.set(false);
        }
    }

    private int runOnceLocked() {
        String owner = workerId();
        int effectiveBatchSize = batchSize();
        Duration visibility = visibility();
        heartbeat(owner, visibility.plusMinutes(1));
        List<RuntimeWorkItem> items = workQueue.claim(
                MagnetWorkTypes.SEARCH,
                owner,
                effectiveBatchSize,
                visibility);
        int processed = 0;
        if (!items.isEmpty()) {
            try (MagnetQueueClaimGuard claims = new MagnetQueueClaimGuard(
                    leaseStore, items, visibility, () -> heartbeat(owner, heartbeatTtl()))) {
                for (RuntimeWorkItem item : items) {
                    if (processItem(item)) {
                        processed++;
                    }
                }
            }
        }
        heartbeat(owner, heartbeatTtl());
        return processed;
    }

    private boolean processItem(RuntimeWorkItem item) {
        MagnetSearchWorkPayload payload = null;
        if (!MagnetQueueClaimGuard.ownsCurrent(item)) {
            log.warn("Magnet queue claim is stale before processing: taskId={}", item.taskId());
            return false;
        }
        try {
            payload = objectMapper.readValue(item.payload(), MagnetSearchWorkPayload.class);
            if (payload.jobId() == null || payload.unifiedVideoId() == null) {
                throw new IllegalArgumentException("Magnet search work payload requires jobId and unifiedVideoId");
            }
            MagnetQueueClaimGuard.checkCurrentOwnership();
            magnetQueryService.executeQueuedSearch(
                    payload.jobId(), payload.unifiedVideoId(), payload.triggerType(), item.attempt() < maxRetries());
            MagnetQueueClaimGuard.checkCurrentOwnership();
            if (!MagnetQueueClaimGuard.finishCurrent(item, () -> workQueue.complete(item))) {
                log.warn("Magnet queue completion rejected after claim loss: taskId={}", item.taskId());
                return false;
            }
            return true;
        } catch (MagnetSearchExecutionException ex) {
            boolean retryable = ex.retryable() && item.attempt() < maxRetries();
            failOwnedItem(item, retryable, ex.getMessage());
            return false;
        } catch (MagnetSearchJobCoordinator.MagnetJobBusyException ex) {
            deferOwnedItem(item, Duration.ofSeconds(30), ex.getMessage());
            return false;
        } catch (MagnetSearchJobCoordinator.MagnetLeaseLostException ex) {
            deferOwnedItem(item, Duration.ofSeconds(30), ex.getMessage());
            return false;
        } catch (RuntimeException ex) {
            boolean retryable = item.attempt() < maxRetries();
            failOwnedItem(item, retryable, "MAGNET_EXECUTION_FAILURE:" + ex.getClass().getSimpleName());
            return false;
        }
    }

    private void failOwnedItem(RuntimeWorkItem item, boolean retryable, String reason) {
        boolean transitioned = MagnetQueueClaimGuard.finishCurrent(
                item,
                () -> workQueue.fail(item, retryable, retryDelay(item.attempt()), reason));
        if (!transitioned) {
            log.warn("Magnet queue failure transition rejected after claim loss: taskId={}", item.taskId());
        }
    }

    private void deferOwnedItem(RuntimeWorkItem item, Duration delay, String reason) {
        boolean transitioned = MagnetQueueClaimGuard.finishCurrent(item, () -> leaseStore.defer(item, delay, reason));
        if (!transitioned) {
            log.warn("Magnet queue deferral rejected after claim loss: taskId={}", item.taskId());
        }
    }

    private void heartbeat(String owner, Duration ttl) {
        workQueue.heartbeatConsumer(MagnetWorkTypes.SEARCH, owner, ttl);
    }

    private void heartbeatIfEnabled() {
        if (enabled()) {
            heartbeat(workerId(), heartbeatTtl());
        }
    }

    private Duration visibility() {
        return Duration.ofSeconds(boundedInt(
                VISIBILITY_SECONDS_KEY,
                visibilitySecondsByDeployment,
                1,
                MAX_VISIBILITY_SECONDS));
    }

    private Duration heartbeatTtl() {
        return visibility().plusMinutes(1);
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
        return Duration.ofSeconds(Math.min(delaySeconds, maxBackoffSeconds()));
    }

    private boolean enabled() {
        return runtimeConfig == null
                ? enabledByDeployment
                : runtimeConfig.booleanValue(ENABLED_KEY, enabledByDeployment);
    }

    private int batchSize() {
        return boundedInt(BATCH_SIZE_KEY, batchSizeByDeployment, 1, MAX_BATCH_SIZE);
    }

    private int maxRetries() {
        return boundedInt(MAX_RETRIES_KEY, maxRetriesByDeployment, 1, MAX_RETRIES);
    }

    private int maxBackoffSeconds() {
        return boundedInt(MAX_BACKOFF_SECONDS_KEY, maxBackoffSecondsByDeployment, 1, MAX_BACKOFF_SECONDS);
    }

    private int boundedInt(String key, int fallback, int min, int max) {
        int safeFallback = Math.max(min, Math.min(max, fallback));
        return runtimeConfig == null
                ? safeFallback
                : runtimeConfig.boundedIntValue(key, safeFallback, min, max);
    }
}
