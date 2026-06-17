package com.prodigalgal.ircs.metadata.dispatch;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.metadata.MetadataConfigValues;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(value = "app.metadata.watchdog.enabled", havingValue = "true")
public class MetadataRetryWatchdog {

    private final RawVideoEnrichmentRepository repository;
    private final MetadataEnrichPublisher publisher;
    private final MetadataConfigValues configValues;

    MetadataRetryWatchdog(
            RawVideoEnrichmentRepository repository,
            MetadataEnrichPublisher publisher,
            MetadataConfigValues configValues) {
        this.repository = repository;
        this.publisher = publisher;
        this.configValues = configValues;
    }

    @Scheduled(
            initialDelayString = "${app.metadata.watchdog.initial-delay-ms:60000}",
            fixedDelayString = "${app.metadata.watchdog.fixed-delay-ms:300000}")
    public void runScheduled() {
        runOnce();
    }

    int runOnce() {
        int batchSize = configValues.watchdogBatchSize();
        if (batchSize <= 0) {
            log.debug("Metadata retry watchdog skipped because batchSize={}", batchSize);
            return 0;
        }
        Instant staleBefore = Instant.now().minusSeconds(Math.max(1L, configValues.watchdogStalePendingSeconds()));
        List<PipelineCommand> dueCommands = repository.claimDueEnrichmentRecoveryCommands(staleBefore, batchSize);
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
                int released = repository.releaseRecoveryPublishFailures(unpublished);
                log.warn(
                        "Metadata retry watchdog publish failed after {} successes; released {} unpublished raw videos",
                        published,
                        released,
                        ex);
                throw ex;
            }
        }

        log.info("Metadata retry watchdog republished {} raw videos", published);
        return published;
    }
}
