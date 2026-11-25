package com.prodigalgal.ircs.metadata.inventory;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventoryRepository.Claim;
import com.prodigalgal.ircs.metadata.inventory.TmdbInventoryRepository.LeaseLost;
import com.prodigalgal.ircs.metadata.inventory.CatalogSnapshotHttpClient.SnapshotStatusException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
class TmdbInventoryScheduler {
    private static final LocalTime PUBLISH_TIME_UTC = LocalTime.of(8, 15);

    private final TmdbInventoryRepository repository;
    private final TmdbInventorySnapshotSource source;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = ScheduledTriggers.virtualThreadExecutor("tmdb-inventory-trigger-vt-");

    @Value("${app.catalog.authority-first.enabled:false}")
    private boolean enabled;

    @Scheduled(initialDelayString = "${app.catalog.tmdb-inventory.initial-delay-ms:30000}",
            fixedDelayString = "${app.catalog.tmdb-inventory.fixed-delay-ms:900000}")
    void scheduled() {
        if (enabled && running.compareAndSet(false, true)) {
            ScheduledTriggers.submit(executor, this::run, log, "catalog.tmdb-inventory");
        }
    }

    private void run() {
        try {
            LocalDate date = latestPublishedDate(Instant.now());
            for (TmdbInventoryMediaType mediaType : TmdbInventoryMediaType.values()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (importSnapshot(mediaType, date)) {
                    importSnapshot(mediaType, date.minusDays(1));
                }
            }
        } finally {
            running.set(false);
        }
    }

    static LocalDate latestPublishedDate(Instant currentTime) {
        var utc = currentTime.atOffset(ZoneOffset.UTC);
        return utc.toLocalTime().isBefore(PUBLISH_TIME_UTC) ? utc.toLocalDate().minusDays(1) : utc.toLocalDate();
    }

    private boolean importSnapshot(TmdbInventoryMediaType mediaType, LocalDate date) {
        var attempt = repository.claim(mediaType, date);
        if (attempt.isEmpty()) {
            return false;
        }
        Claim claim = attempt.orElseThrow();
        try {
            clearInactiveSlot(claim);
            int rows = source.download(mediaType, date, entries -> repository.append(claim, entries));
            if (repository.publish(claim, rows)) {
                log.info("Published TMDB inventory: mediaType={}, snapshotDate={}, rows={}", mediaType.catalogType, date, rows);
            } else {
                recordFailure(claim, "SNAPSHOT_INCOMPLETE_OR_EXPIRED", null);
            }
        } catch (SnapshotStatusException exception) {
            recordFailure(claim, exception.getMessage(), null);
            // The official host responds 403 for a not-yet-published export.
            return exception.status() == 403 || exception.status() == 404;
        } catch (InterruptedException exception) {
            recordFailure(claim, "SNAPSHOT_INTERRUPTED", exception);
            Thread.currentThread().interrupt();
        } catch (LeaseLost exception) {
            log.info("TMDB inventory lease expired: mediaType={}, snapshotDate={}", mediaType.catalogType, date);
        } catch (IOException exception) {
            recordFailure(claim, readErrorCode(exception), exception);
        } catch (RuntimeException exception) {
            recordFailure(claim, "SNAPSHOT_WRITE_FAILED", exception);
        }
        return false;
    }

    private void clearInactiveSlot(Claim claim) throws InterruptedException {
        // Every batch is a separate short transaction. A failed attempt never leaves a third slot.
        while (repository.clearInactiveBatch(claim) > 0) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
        }
    }

    private String readErrorCode(IOException exception) {
        String reason = exception.getMessage();
        return reason != null && reason.matches("SNAPSHOT_[A-Z0-9_]{1,48}") ? reason : "SNAPSHOT_READ_FAILED";
    }

    private void recordFailure(Claim claim, String code, Exception exception) {
        log.warn("TMDB inventory deferred: mediaType={}, snapshotDate={}, errorCode={}, errorType={}",
                claim.mediaType().catalogType, claim.date(), code,
                exception == null ? null : exception.getClass().getSimpleName());
        repository.fail(claim, code);
    }

    @PreDestroy
    void close() {
        executor.shutdownNow();
    }
}
