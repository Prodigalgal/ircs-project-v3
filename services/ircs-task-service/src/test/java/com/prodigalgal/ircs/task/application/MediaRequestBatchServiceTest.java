package com.prodigalgal.ircs.task.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduleResponse;
import com.prodigalgal.ircs.contracts.trend.TrendDiscoveryScheduledTask;
import com.prodigalgal.ircs.task.domain.MediaRequestBatchItemCandidate;
import com.prodigalgal.ircs.task.domain.MediaRequestCandidate;
import com.prodigalgal.ircs.task.domain.MediaRequestExistingVideo;
import com.prodigalgal.ircs.task.infrastructure.JdbcCollectionTaskRepository;
import com.prodigalgal.ircs.task.infrastructure.MediaRequestContentLookupRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MediaRequestBatchServiceTest {

    private final JdbcCollectionTaskRepository taskRepository = org.mockito.Mockito.mock(JdbcCollectionTaskRepository.class);
    private final MediaRequestContentLookupRepository contentLookupRepository =
            org.mockito.Mockito.mock(MediaRequestContentLookupRepository.class);
    private final TrendDiscoveryTaskService trendDiscoveryTaskService = org.mockito.Mockito.mock(TrendDiscoveryTaskService.class);
    private final TaskCommandService taskCommandService = org.mockito.Mockito.mock(TaskCommandService.class);
    private final MediaRequestBatchService service = new MediaRequestBatchService(
            taskRepository,
            contentLookupRepository,
            trendDiscoveryTaskService,
            taskCommandService);

    @Test
    void filtersExistingVideosBeforeCreatingBatch() {
        UUID existingRequestId = UUID.randomUUID();
        UUID missingRequestId = UUID.randomUUID();
        UUID existingVideoId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        MediaRequestCandidate existing = new MediaRequestCandidate(existingRequestId, "黑客帝国", 1999, 4);
        MediaRequestCandidate missing = new MediaRequestCandidate(missingRequestId, "异形", null, 2);
        when(contentLookupRepository.findExistingVideo("黑客帝国", 1999))
                .thenReturn(Optional.of(new MediaRequestExistingVideo(existingVideoId, "UNIFIED")));
        when(contentLookupRepository.findExistingVideo("异形", null)).thenReturn(Optional.empty());
        when(taskRepository.createMediaRequestBatch(List.of(missing))).thenReturn(batchId);

        Optional<UUID> actual = service.createBatchFromPendingRequests(List.of(existing, missing));

        Assertions.assertThat(actual).contains(batchId);
        verify(taskRepository).markMediaRequestSkippedExisting(existingRequestId, existingVideoId, "UNIFIED");
        verify(taskRepository).createMediaRequestBatch(List.of(missing));
    }

    @Test
    void skipsBatchCreationWhenAllRequestsAlreadyExist() {
        UUID firstRequestId = UUID.randomUUID();
        UUID secondRequestId = UUID.randomUUID();
        UUID firstVideoId = UUID.randomUUID();
        UUID secondVideoId = UUID.randomUUID();
        MediaRequestCandidate first = new MediaRequestCandidate(firstRequestId, "黑客帝国", 1999, 4);
        MediaRequestCandidate second = new MediaRequestCandidate(secondRequestId, "异形", null, 2);
        when(contentLookupRepository.findExistingVideo("黑客帝国", 1999))
                .thenReturn(Optional.of(new MediaRequestExistingVideo(firstVideoId, "UNIFIED")));
        when(contentLookupRepository.findExistingVideo("异形", null))
                .thenReturn(Optional.of(new MediaRequestExistingVideo(secondVideoId, "RAW")));

        Optional<UUID> actual = service.createBatchFromPendingRequests(List.of(first, second));

        Assertions.assertThat(actual).isEmpty();
        verify(taskRepository).markMediaRequestSkippedExisting(firstRequestId, firstVideoId, "UNIFIED");
        verify(taskRepository).markMediaRequestSkippedExisting(secondRequestId, secondVideoId, "RAW");
        verify(taskRepository, never()).createMediaRequestBatch(any());
    }

    @Test
    void skipsItemWhenInternalVideoAlreadyExists() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        MediaRequestBatchItemCandidate item = new MediaRequestBatchItemCandidate(
                itemId,
                requestId,
                "黑客帝国",
                1999,
                3);
        when(taskRepository.markMediaRequestBatchRunning(batchId)).thenReturn(true);
        when(taskRepository.findReadyMediaRequestBatchItems(batchId)).thenReturn(List.of(item));
        when(contentLookupRepository.findExistingVideo("黑客帝国", 1999))
                .thenReturn(Optional.of(new MediaRequestExistingVideo(videoId, "UNIFIED")));

        service.startBatch(batchId);

        verify(taskRepository).markMediaRequestBatchItemSkipped(itemId, requestId, videoId, "UNIFIED");
        verify(taskRepository).finishMediaRequestBatch(batchId, "COMPLETED", null);
    }

    @Test
    void preparesAndStartsCollectionTasksWhenInternalVideoIsMissing() {
        UUID batchId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        MediaRequestBatchItemCandidate item = new MediaRequestBatchItemCandidate(
                itemId,
                requestId,
                "异形",
                null,
                2);
        when(taskRepository.markMediaRequestBatchRunning(batchId)).thenReturn(true);
        when(taskRepository.findReadyMediaRequestBatchItems(batchId)).thenReturn(List.of(item));
        when(contentLookupRepository.findExistingVideo("异形", null)).thenReturn(Optional.empty());
        when(trendDiscoveryTaskService.prepare(any(), eq("media-request-batch:" + batchId + ":" + itemId), any()))
                .thenReturn(new TrendDiscoveryScheduleResponse(
                        "trend-discovery",
                        1,
                        1,
                        1,
                        0,
                        0,
                        List.of(new TrendDiscoveryScheduledTask(taskId, UUID.randomUUID(), "资源站", "异形", "READY")),
                        List.of()));

        service.startBatch(batchId);

        verify(taskRepository).markMediaRequestBatchItemScheduled(itemId, requestId, 1);
        verify(taskCommandService).startInternal(taskId, false);
        verify(taskRepository).finishMediaRequestBatch(batchId, "COMPLETED", null);
    }
}
