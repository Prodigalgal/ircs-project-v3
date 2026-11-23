package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import jakarta.annotation.PreDestroy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
class AuthorityCatalogImportWorker {

    private static final List<String> TYPES = List.of("movie", "tv");
    private final AuthorityCatalogImportRepository repository;
    private final CatalogTmdbMetadataSource source;
    private final RuntimeConfigService config;
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("authority-catalog-vt-");
    private final AtomicBoolean importing = new AtomicBoolean();
    private final AtomicBoolean syncing = new AtomicBoolean();
    @Value("${app.catalog.authority-first.enabled:false}")
    private boolean enabled;

    @Scheduled(initialDelayString = "${app.catalog.authority-first.initial-delay-ms:45000}",
            fixedDelayString = "${app.catalog.authority-first.import-delay-ms:15000}")
    void importDue() {
        ScheduledTriggers.submit(executor, () -> runIfAvailable(importing, this::runImports),
                log, "catalog.authority.details");
    }

    @Scheduled(initialDelayString = "${app.catalog.authority-first.initial-sync-delay-ms:105000}",
            fixedDelayString = "${app.catalog.authority-first.sync-delay-ms:60000}")
    void syncChanges() {
        ScheduledTriggers.submit(executor, () -> runIfAvailable(syncing, this::runChanges),
                log, "catalog.authority.changes");
    }

    private void runIfAvailable(AtomicBoolean running, Runnable action) {
        if (!enabled || !running.compareAndSet(false, true)) return;
        try {
            action.run();
        } finally {
            running.set(false);
        }
    }

    private void runImports() {
        int batch = config.boundedIntValue("app.catalog.authority-first.import-batch-size", 8, 1, 32);
        for (int i = 0; i < batch && !Thread.currentThread().isInterrupted(); i++) {
            var job = repository.claim();
            if (job.isEmpty() || !runImport(job.get())) break;
        }
    }

    private boolean runImport(AuthorityCatalogImportRepository.Import job) {
        try {
            repository.publish(job, source.fetchAuthority(job.mediaType(), job.tmdbId(), job.seasonNumber()));
            return true;
        } catch (CatalogMetadataUnavailableException ex) {
            String reason = ex.getMessage();
            boolean credentialUnavailable = "CREDENTIAL_UNAVAILABLE".equals(reason);
            repository.defer(job, reason, !credentialUnavailable);
            return !credentialUnavailable;
        } catch (RuntimeException ex) {
            repository.defer(job, "SOURCE_OR_WRITE_FAILED", false);
            log.warn("Authority import deferred, type={}, id={}, season={}, errorType={}",
                    job.mediaType(), job.tmdbId(), job.seasonNumber(), ex.getClass().getSimpleName());
            return false;
        }
    }

    private void runChanges() {
        int warmup = config.boundedIntValue("app.catalog.authority-first.warmup-per-type", 400, 0, 10000);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (String type : TYPES) {
            if (warmup > 0) repository.seed(type, warmup);
            var cursor = repository.cursor(type, today.minusDays(2));
            if (!cursor.day().isBefore(today)) continue;
            try {
                repository.saveChanges(cursor, TmdbChangePage.from(source.changes(type, cursor.day(), cursor.page()), cursor.page()));
            } catch (RuntimeException ex) {
                log.warn("Authority change page deferred, type={}, day={}, page={}, errorType={}",
                        type, cursor.day(), cursor.page(), ex.getClass().getSimpleName());
            }
        }
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
