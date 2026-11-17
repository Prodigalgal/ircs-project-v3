package com.prodigalgal.ircs.task.job;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.task.application.CatalogDiscoveryService;
import com.prodigalgal.ircs.task.config.CatalogDiscoveryConfigValues;
import com.prodigalgal.ircs.task.infrastructure.TaskDistributedLockRunner;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class CatalogDiscoveryScheduler {

    static final String LOCK_NAME = "task:catalog-discovery:dispatch";
    private final CatalogDiscoveryService service;
    private final CatalogDiscoveryConfigValues config;
    private final TaskDistributedLockRunner lockRunner;
    private final AtomicBoolean submitted = new AtomicBoolean();
    private final ExecutorService triggerExecutor =
            ScheduledTriggers.virtualThreadExecutor("ircs-catalog-discovery-trigger-vt-");

    @Scheduled(fixedDelayString = "${app.task.catalog-discovery.fixed-delay-ms:60000}",
            initialDelayString = "${app.task.catalog-discovery.initial-delay-ms:60000}")
    void dispatch() {
        if (!config.enabled() || !submitted.compareAndSet(false, true)) return;
        ScheduledTriggers.submit(triggerExecutor, () -> {
            try {
                dispatchOnce();
            } finally {
                submitted.set(false);
            }
        }, log, "task.catalog-discovery.dispatch");
    }

    void dispatchOnce() {
        if (config.enabled() && !lockRunner.runExclusive(LOCK_NAME, service::dispatchDue)) {
            log.debug("Catalog discovery skipped: another instance owns the dispatch lease");
        }
    }

    @PreDestroy
    void shutdown() {
        triggerExecutor.shutdownNow();
    }
}
