package com.prodigalgal.ircs.task.application;

import com.prodigalgal.ircs.common.lease.ClusterLeaseGuard;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.task.config.CatalogDiscoveryConfigValues;
import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import com.prodigalgal.ircs.task.infrastructure.CatalogDiscoveryRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogDiscoveryService {

    private final CatalogDiscoveryRepository repository;
    private final TrendDiscoveryTaskService discovery;
    private final CatalogDiscoveryConfigValues config;

    public void dispatchDue() {
        if (!config.enabled()) return;
        int batchSize = config.batchSize();
        repository.reconcileSubmitted(batchSize * 4);
        for (CatalogDiscoveryCandidate candidate : repository.claimDue(batchSize, config.leaseDuration())) {
            try {
                checkOwnership(candidate);
                if (repository.hasRequestedResources(candidate)) {
                    repository.markFulfilled(candidate);
                    continue;
                }
                if (repository.isPausedByOperator(candidate)) {
                    repository.retry(candidate, Duration.ofHours(6), "OPERATOR_PAUSED");
                    continue;
                }
                if (repository.hasActiveTasks(candidate)) {
                    repository.markSubmitted(candidate);
                    continue;
                }
                int capacity = config.maxActiveTasks() - repository.activeTaskCount(config.maxActiveTasks());
                if (capacity <= 0) {
                    repository.retry(candidate, Duration.ofMinutes(5), "COLLECTION_BACKPRESSURE");
                    continue;
                }
                TrendDiscoveryScheduleResponse result = discovery.scheduleCatalog(candidate,
                        Math.min(config.maxDataSources(), capacity), () -> checkOwnership(candidate));
                checkOwnership(candidate);
                boolean accepted = result.tasks().stream()
                        .anyMatch(task -> "QUEUED".equals(task.status()) || "ACTIVE".equals(task.status()));
                if (accepted) {
                    repository.markSubmitted(candidate);
                } else if (result.dataSourceCount() == 0) {
                    // A busy source or a source in cooldown can become eligible on the next pass.
                    repository.retry(candidate, Duration.ofMinutes(5), "NO_ELIGIBLE_SOURCE");
                } else {
                    repository.retry(candidate, retryDelay(candidate.attempts()), "DISCOVERY_NOT_ACCEPTED");
                }
            } catch (RuntimeException ex) {
                repository.retry(candidate, retryDelay(candidate.attempts()), "DISCOVERY_DISPATCH_FAILED");
                // External exception text can contain source URLs and credentials; persist only a stable code.
                log.warn("Catalog discovery deferred: videoId={}, season={}, episode={}, failureType={}",
                        candidate.unifiedVideoId(), candidate.targetSeason(), candidate.targetEpisode(),
                        ex.getClass().getSimpleName());
            }
        }
    }

    private void checkOwnership(CatalogDiscoveryCandidate candidate) {
        ClusterLeaseGuard.checkCurrentOwnership();
        if (!repository.renew(candidate, config.leaseDuration())) {
            throw new IllegalStateException("Catalog discovery claim expired or was reclaimed");
        }
    }

    static Duration retryDelay(int attempts) {
        long minutes = Math.min(360, 5L << Math.min(7, Math.max(0, attempts - 1)));
        return Duration.ofMinutes(minutes);
    }
}
