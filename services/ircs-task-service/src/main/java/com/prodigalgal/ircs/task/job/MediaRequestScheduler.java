package com.prodigalgal.ircs.task.job;

import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleRequest;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.task.application.TrendDiscoveryTaskService;
import com.prodigalgal.ircs.task.domain.MediaRequestCandidate;
import com.prodigalgal.ircs.task.infrastructure.JdbcCollectionTaskRepository;
import com.prodigalgal.ircs.task.infrastructure.TaskDistributedLockRunner;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "app.task.media-request.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
class MediaRequestScheduler {

    static final String LOCK_NAME = "task:media-request:schedule";

    private final JdbcCollectionTaskRepository taskRepository;
    private final TrendDiscoveryTaskService trendDiscoveryTaskService;
    private final TaskDistributedLockRunner lockRunner;

    @Value("${app.task.media-request.batch-size:20}")
    private int batchSize;

    @Value("${app.task.media-request.start-page:1}")
    private int startPage;

    @Value("${app.task.media-request.end-page:1}")
    private int endPage;

    @Value("${app.task.media-request.request-fixed-delay-ms:0}")
    private int requestFixedDelayMs;

    @Value("${app.task.media-request.max-data-sources:0}")
    private int maxDataSources;

    @Scheduled(
            fixedDelayString = "${app.task.media-request.fixed-delay-ms:60000}",
            initialDelayString = "${app.task.media-request.initial-delay-ms:30000}")
    void schedulePendingMediaRequests() {
        if (!lockRunner.runExclusive(LOCK_NAME, this::schedulePendingMediaRequestsLocked)) {
            log.debug("Media request scheduler skipped: distributed lock is held by another instance");
        }
    }

    void schedulePendingMediaRequestsLocked() {
        List<MediaRequestCandidate> candidates = taskRepository.claimPendingMediaRequests(batchSize);
        if (candidates.isEmpty()) {
            return;
        }
        List<String> keywords = candidates.stream()
                .map(MediaRequestCandidate::keyword)
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .distinct()
                .toList();
        if (keywords.isEmpty()) {
            taskRepository.markMediaRequestsPending(ids(candidates), "No valid media request keywords");
            return;
        }
        try {
            TrendDiscoveryScheduleResponse response = trendDiscoveryTaskService.schedule(
                    new TrendDiscoveryScheduleRequest(
                            keywords,
                            positiveOrDefault(startPage, 1),
                            positiveOrDefault(endPage, 1),
                            Math.max(0, requestFixedDelayMs),
                            false,
                            Math.max(0, maxDataSources)),
                    "media-request:" + candidates.getFirst().id());
            if (response.errors().isEmpty()) {
                taskRepository.markMediaRequestsScheduled(ids(candidates));
            } else {
                taskRepository.markMediaRequestsPending(ids(candidates), String.join("; ", response.errors()));
            }
        } catch (RuntimeException ex) {
            taskRepository.markMediaRequestsPending(ids(candidates), safeMessage(ex));
            throw ex;
        }
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static List<UUID> ids(List<MediaRequestCandidate> candidates) {
        return candidates.stream().map(MediaRequestCandidate::id).toList();
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
