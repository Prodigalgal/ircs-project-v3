package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
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
class CatalogMetadataScheduler {
    private final CatalogMetadataWorker worker;
    private final AtomicBoolean running=new AtomicBoolean();
    private final ExecutorService executor=ScheduledTriggers.virtualThreadExecutor("catalog-metadata-trigger-vt-");
    @Scheduled(initialDelayString="${app.metadata.catalog.initial-delay-ms:60000}",
            fixedDelayString="${app.metadata.catalog.fixed-delay-ms:60000}")
    void scheduled() {
        ScheduledTriggers.submit(executor,()-> {
            if (!running.compareAndSet(false,true)) return;
            try { worker.runBatch(); } finally { running.set(false); }
        },log,"metadata.catalog");
    }
    @PreDestroy
    void close() { executor.shutdownNow(); }
}
