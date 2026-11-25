package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.DoubanTarget;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.Lease;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class DoubanCatalogSyncWorker {
    static final String PROVIDER = "Douban";
    private static final String FEED = "work-metadata";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);
    private static final Duration WORK_REFRESH = Duration.ofDays(1);
    private static final Duration UNMATCHED_REFRESH = Duration.ofDays(7);
    private static final Duration RETRY_DELAY = Duration.ofMinutes(30);

    private final CatalogReferenceSyncRepository repository;
    private final CatalogDoubanSupplementSource source;
    private final RuntimeConfigService config;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("catalog-douban-sync-vt-");

    @Value("${app.catalog.reference.douban.enabled:false}")
    private boolean enabled;

    @Scheduled(initialDelayString = "${app.catalog.reference.douban.initial-delay-ms:75000}",
            fixedDelayString = "${app.catalog.reference.douban.fixed-delay-ms:60000}")
    void scheduled() {
        if (enabled) ScheduledTriggers.submit(executor, this::run, log, "catalog.douban.sync");
    }

    private void run() {
        if (!running.compareAndSet(false, true)) return;
        try {
            repository.claim(PROVIDER, FEED, LEASE_DURATION).ifPresent(this::sync);
        } finally {
            running.set(false);
        }
    }

    private void sync(Lease lease) {
        try {
            int limit = config.boundedIntValue("app.catalog.reference.douban.batch-size", 8, 1, 20);
            var targets = repository.dueDoubanTargets(PROVIDER, limit);
            for (DoubanTarget target : targets) {
                if (Thread.currentThread().isInterrupted()) {
                    repository.fail(lease, RETRY_DELAY, "INTERRUPTED");
                    return;
                }
                if (!syncTarget(lease, target)) return;
            }
            repository.complete(lease, targets.isEmpty() ? RETRY_DELAY : Duration.ofMinutes(1), null, null, null);
        } catch (RuntimeException ex) {
            repository.fail(lease, RETRY_DELAY, "SUPPLEMENT_WRITE_FAILED");
            log.warn("Douban catalog sync deferred, errorType={}", ex.getClass().getSimpleName());
        }
    }

    private boolean syncTarget(Lease lease, DoubanTarget target) {
        try {
            var facts = source.fetch(target.title(), target.doubanId(), target.imdbId());
            if (facts.isPresent()) {
                repository.storeDouban(lease, target, facts.orElseThrow(), WORK_REFRESH);
            } else {
                repository.deferDoubanTarget(lease, target, UNMATCHED_REFRESH, "NO_IDENTITY_MATCH");
            }
            return true;
        } catch (CatalogMetadataUnavailableException ex) {
            if ("DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT".equals(ex.getMessage())) {
                repository.deferDoubanTarget(lease, target, UNMATCHED_REFRESH, "IDENTITY_CONFLICT");
                return true;
            }
            repository.deferDoubanTarget(lease, target, RETRY_DELAY, "SOURCE_RETRY");
            repository.fail(lease, RETRY_DELAY, ex.getMessage());
            log.warn("Douban catalog source deferred, reason={}", ex.getMessage());
            return false;
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
