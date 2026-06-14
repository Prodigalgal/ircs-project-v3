package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(value = "app.storage.r2.watchdog.enabled", havingValue = "true")
public class StorageR2Watchdog {

    private final AvatarSyncMemberRepository memberRepository;
    private final CoverImageAdminRepository coverImageRepository;
    private final StorageCommandPublisher commandPublisher;
    private final CoverImageR2SyncService coverImageR2SyncService;
    private final R2ObjectStorage r2ObjectStorage;
    private final StorageConfigValues configValues;
    private final WorkerJobAuditWriter auditWriter;
    private final String workerId;
    private final Duration claimTtl;
    private final int batchSize;
    public StorageR2Watchdog(
            AvatarSyncMemberRepository memberRepository,
            CoverImageAdminRepository coverImageRepository,
            StorageCommandPublisher commandPublisher,
            CoverImageR2SyncService coverImageR2SyncService,
            R2ObjectStorage r2ObjectStorage,
            StorageConfigValues configValues,
            WorkerJobAuditWriter auditWriter,
            @Value("${spring.application.name:ircs-storage-service}") String applicationName,
            @Value("${app.storage.r2.watchdog.worker-id:${APP_STORAGE_R2_WATCHDOG_WORKER_ID:${APP_STORAGE_CLUSTER_LOCK_WORKER_ID:}}}")
                    String configuredWorkerId,
            @Value("${app.storage.r2.watchdog.claim-ttl:PT10M}") String claimTtl,
            @Value("${app.storage.r2.watchdog.batch-size:25}") int batchSize) {
        this.memberRepository = memberRepository;
        this.coverImageRepository = coverImageRepository;
        this.commandPublisher = commandPublisher;
        this.coverImageR2SyncService = coverImageR2SyncService;
        this.r2ObjectStorage = r2ObjectStorage;
        this.configValues = configValues;
        this.auditWriter = auditWriter == null ? WorkerJobAuditWriter.noop() : auditWriter;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.claimTtl = parseDuration(claimTtl, Duration.ofMinutes(10));
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(
            initialDelayString = "${app.storage.r2.watchdog.initial-delay-ms:60000}",
            fixedDelayString = "${app.storage.r2.watchdog.fixed-delay-ms:300000}")
    public void scheduledRun() {
        runOnce();
    }

    WatchdogRunResult runOnce() {
        return runOnceLocked();
    }

    private WatchdogRunResult runOnceLocked() {
        Instant startedAt = Instant.now();
        try {
            WatchdogRunResult result = runInternal();
            if (result.skipped()) {
                recordSkipped(startedAt, "r2-inactive");
            } else {
                recordSucceeded(startedAt);
            }
            return result;
        } catch (RuntimeException ex) {
            recordFailed(startedAt, ex);
            throw ex;
        }
    }

    private WatchdogRunResult runInternal() {
        if (!r2ObjectStorage.isActive()) {
            log.debug("Storage R2 watchdog skipped because R2 is inactive");
            return new WatchdogRunResult(0, 0, 0, true);
        }

        List<UUID> avatarMembers = memberRepository.claimLocalAvatarMemberIds(
                localPublicPrefix(),
                batchSize,
                workerId,
                claimTtl);
        int avatarRepublished = 0;
        for (UUID memberId : avatarMembers) {
            try {
                commandPublisher.publishAvatarSync(memberId);
                avatarRepublished++;
            } catch (RuntimeException ex) {
                memberRepository.releaseAvatarSyncClaim(memberId, workerId);
                log.warn("Storage R2 watchdog failed to publish avatar sync {}: {}", memberId, ex.getMessage());
                throw ex;
            }
        }

        int coverSynced = 0;
        int coverFailed = 0;
        for (UUID imageId : coverImageRepository.claimDueLocalR2UploadIds(batchSize)) {
            try {
                CoverImageR2SyncService.CoverImageR2SyncResult result = coverImageR2SyncService.sync(imageId);
                if (result.synced()) {
                    coverSynced++;
                } else if (result.failed()) {
                    coverFailed++;
                }
            } catch (RuntimeException ex) {
                coverFailed++;
                log.warn("Storage R2 watchdog failed to process cover image {}: {}", imageId, ex.getMessage());
            }
        }

        if (avatarRepublished > 0 || coverSynced > 0 || coverFailed > 0) {
            log.info(
                    "Storage R2 watchdog completed: avatarRepublished={}, coverSynced={}, coverFailed={}",
                    avatarRepublished,
                    coverSynced,
                    coverFailed);
        }
        return new WatchdogRunResult(avatarRepublished, coverSynced, coverFailed, false);
    }

    private void recordSucceeded(Instant startedAt) {
        recordAudit(WorkerJobAuditEvent.succeeded(
                "scheduler",
                "storage.r2-watchdog",
                "execute",
                elapsedSince(startedAt)));
    }

    private void recordSkipped(Instant startedAt, String reason) {
        recordAudit(new WorkerJobAuditEvent(
                "scheduler",
                "storage.r2-watchdog",
                reason,
                "skipped",
                elapsedSince(startedAt),
                new StorageR2WatchdogAuditException(reason)));
    }

    private void recordFailed(Instant startedAt, RuntimeException error) {
        recordAudit(WorkerJobAuditEvent.failed(
                "scheduler",
                "storage.r2-watchdog",
                "execute",
                elapsedSince(startedAt),
                error));
    }

    private void recordAudit(WorkerJobAuditEvent event) {
        try {
            auditWriter.record(event);
        } catch (RuntimeException ex) {
            log.warn("Storage R2 watchdog audit write failed: {}", ex.getMessage());
        }
    }

    private static Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, Instant.now());
    }

    private String localPublicPrefix() {
        String base = configValues.storagePublicPath().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static Duration parseDuration(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        try {
            Duration duration = Duration.parse(trimmed);
            return duration.isPositive() ? duration : fallback;
        } catch (RuntimeException ignored) {
            // Continue with compact duration suffix parsing.
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith("ms")) {
                return positive(Duration.ofMillis(Long.parseLong(lower.substring(0, lower.length() - 2))), fallback);
            }
            if (lower.endsWith("s")) {
                return positive(Duration.ofSeconds(Long.parseLong(lower.substring(0, lower.length() - 1))), fallback);
            }
            if (lower.endsWith("m")) {
                return positive(Duration.ofMinutes(Long.parseLong(lower.substring(0, lower.length() - 1))), fallback);
            }
            if (lower.endsWith("h")) {
                return positive(Duration.ofHours(Long.parseLong(lower.substring(0, lower.length() - 1))), fallback);
            }
        } catch (RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private static Duration positive(Duration duration, Duration fallback) {
        return duration != null && duration.isPositive() ? duration : fallback;
    }

    record WatchdogRunResult(int avatarRepublished, int coverSynced, int coverFailed, boolean skipped) {
    }

    private static class StorageR2WatchdogAuditException extends RuntimeException {
        StorageR2WatchdogAuditException(String message) {
            super(message);
        }
    }
}
