package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(value = "app.normalization.watchdog.enabled", havingValue = "true")
public class NormalizationRetryWatchdog {

    private final RawVideoNormalizationRepository repository;
    private final NormalizeVideoPublisher publisher;
    private final NormalizationConfigValues configValues;
    private final NormalizeVideoQueueDepthProbe queueDepthProbe;
    NormalizationRetryWatchdog(
            RawVideoNormalizationRepository repository,
            NormalizeVideoPublisher publisher,
            NormalizationConfigValues configValues,
            NormalizeVideoQueueDepthProbe queueDepthProbe) {
        this.repository = repository;
        this.publisher = publisher;
        this.configValues = configValues;
        this.queueDepthProbe = queueDepthProbe;
    }

    @Scheduled(
            initialDelayString = "${app.normalization.watchdog.initial-delay-ms:60000}",
            fixedDelayString = "${app.normalization.watchdog.fixed-delay-ms:300000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        int batchSize = configValues.watchdogBatchSize();
        if (batchSize <= 0) {
            log.debug("Normalization retry watchdog skipped because batchSize={}", batchSize);
            return 0;
        }
        Instant now = Instant.now();
        long stalePendingSeconds = configValues.watchdogStalePendingSeconds();
        Instant stalePendingBefore = now.minusSeconds(Math.max(1, stalePendingSeconds));
        List<PipelineCommand> dueCommands = new ArrayList<>(
                repository.claimDueFailedNormalizationRecoveryCommands(now, batchSize));
        int remaining = batchSize - dueCommands.size();
        if (remaining > 0 && shouldRescueStaleActiveRecords()) {
            dueCommands.addAll(repository.claimStaleActiveNormalizationRecoveryCommands(stalePendingBefore, remaining));
        }
        if (dueCommands.isEmpty()) {
            return 0;
        }
        int published = 0;
        for (int i = 0; i < dueCommands.size(); i++) {
            PipelineCommand command = dueCommands.get(i);
            try {
                publisher.publish(command);
                published++;
            } catch (RuntimeException ex) {
                List<UUID> unpublished = dueCommands.subList(i, dueCommands.size()).stream()
                        .map(PipelineCommand::rawVideoId)
                        .toList();
                Instant nextRetryTime = Instant.now().plusSeconds(Math.max(1L, configValues.backoffBaseSeconds()));
                int released = repository.releaseRecoveryPublishFailures(unpublished, nextRetryTime);
                log.warn(
                        "Normalization retry watchdog publish failed after {} successes; released {} unpublished raw videos for retry at {}",
                        published,
                        released,
                        nextRetryTime,
                        ex);
                throw ex;
            }
        }
        log.info("Normalization retry watchdog republished {} raw videos", published);
        return published;
    }

    private boolean shouldRescueStaleActiveRecords() {
        OptionalInt pendingMessages = queueDepthProbe.pendingMessages();
        if (pendingMessages.isPresent() && pendingMessages.getAsInt() > 0) {
            log.info("Normalization retry watchdog skipped stale active rescue because queue depth={}",
                    pendingMessages.getAsInt());
            return false;
        }
        return true;
    }
}
