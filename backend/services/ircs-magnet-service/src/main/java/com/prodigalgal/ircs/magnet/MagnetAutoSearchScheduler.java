package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.lock.DistributedLockService;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
class MagnetAutoSearchScheduler {

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_QUEUE_DEPTH = 100_000;
    private static final String MAX_QUEUE_DEPTH_KEY = "app.magnet.auto-search.max-queue-depth";
    private static final Duration DEFAULT_COOLDOWN = Duration.ofHours(12);

    private final JdbcMagnetRepository magnetRepository;
    private final MagnetQueryService magnetQueryService;
    private final RuntimeConfigService runtimeConfig;
    private final DistributedLockService locks;
    private final MagnetWorkPublisher workPublisher;
    private final String lockOwner = UUID.randomUUID().toString();
    private final ExecutorService triggerExecutor =
            ScheduledTriggers.virtualThreadExecutor("ircs-magnet-auto-search-trigger-vt-");
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${app.magnet.auto-search.enabled:false}")
    private boolean enabledByDeployment;

    @Value("${app.magnet.auto-search.batch-size:10}")
    private int batchSizeByDeployment;

    @Value("${app.magnet.auto-search.cooldown:PT12H}")
    private Duration cooldownByDeployment;

    @Value("${app.magnet.auto-search.max-queue-depth:2000}")
    private int maxQueueDepthByDeployment;

    MagnetAutoSearchScheduler(
            JdbcMagnetRepository magnetRepository,
            MagnetQueryService magnetQueryService,
            ObjectProvider<RuntimeConfigService> runtimeConfigProvider,
            DistributedLockService locks,
            ObjectProvider<MagnetWorkPublisher> workPublisherProvider) {
        this.locks = locks;
        this.workPublisher = workPublisherProvider == null ? null : workPublisherProvider.getIfAvailable();
        this.magnetRepository = magnetRepository;
        this.magnetQueryService = magnetQueryService;
        this.runtimeConfig = runtimeConfigProvider == null ? null : runtimeConfigProvider.getIfAvailable();
    }

    @Scheduled(
            initialDelayString = "${app.magnet.auto-search.initial-delay-ms:60000}",
            fixedDelayString = "${app.magnet.auto-search.fixed-delay-ms:300000}")
    void runScheduled() {
        ScheduledTriggers.submit(triggerExecutor, this::runScheduledBatch, log, "magnet.auto-search.run");
    }

    @PreDestroy
    void shutdownTriggerExecutor() {
        triggerExecutor.shutdownNow();
    }

    void runScheduledBatch() {
        if (!enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            locks.runWithLock("lock:magnet:auto-search", lockOwner, Duration.ofMinutes(10), this::runBatch);
        } catch (RuntimeException ex) {
            log.warn("Magnet auto search batch failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    int runBatch() {
        if (workPublisher == null) {
            log.warn("Magnet auto search skipped because the work queue publisher is unavailable");
            return 0;
        }
        int maxQueueDepth = maxQueueDepth();
        if (!hasCapacity(maxQueueDepth)) {
            return 0;
        }
        List<UUID> candidates = magnetRepository.findAutoSearchCandidates(
                batchSize(),
                Instant.now().minus(cooldown()));
        int triggered = 0;
        for (UUID catalogEntryId : candidates) {
            ClusterLeaseGuard.checkCurrentOwnership();
            if (!hasCapacity(maxQueueDepth)) {
                break;
            }
            try {
                magnetQueryService.enqueueAutomaticSearch(catalogEntryId);
                triggered++;
            } catch (RuntimeException ex) {
                log.warn("Magnet auto search failed: catalogEntryId={}, error={}", catalogEntryId, ex.getMessage());
                break;
            }
        }
        if (triggered > 0) {
            log.info("Triggered magnet auto search: count={}", triggered);
        }
        return triggered;
    }

    private boolean enabled() {
        return runtimeConfig == null
                ? enabledByDeployment
                : runtimeConfig.booleanValue("app.magnet.auto-search.enabled", enabledByDeployment);
    }

    private int batchSize() {
        int fallback = Math.max(1, Math.min(MAX_BATCH_SIZE, batchSizeByDeployment));
        return runtimeConfig == null
                ? fallback
                : runtimeConfig.boundedIntValue("app.magnet.auto-search.batch-size", fallback, 1, MAX_BATCH_SIZE);
    }

    private Duration cooldown() {
        Duration fallback = positive(cooldownByDeployment, DEFAULT_COOLDOWN);
        Duration value = runtimeConfig == null
                ? fallback
                : runtimeConfig.positiveDurationValue("app.magnet.auto-search.cooldown", fallback);
        return positive(value, fallback);
    }

    private int maxQueueDepth() {
        int fallback = Math.max(1, Math.min(MAX_QUEUE_DEPTH, maxQueueDepthByDeployment));
        return runtimeConfig == null
                ? fallback
                : runtimeConfig.boundedIntValue(MAX_QUEUE_DEPTH_KEY, fallback, 1, MAX_QUEUE_DEPTH);
    }

    private boolean hasCapacity(int maxQueueDepth) {
        try {
            return workPublisher.backlogDepth() < maxQueueDepth;
        } catch (RuntimeException ex) {
            log.warn("Magnet auto search stopped because queue backlog is unavailable: {}", ex.getMessage());
            return false;
        }
    }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
