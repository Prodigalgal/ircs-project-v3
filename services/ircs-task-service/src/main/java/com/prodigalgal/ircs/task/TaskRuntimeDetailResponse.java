package com.prodigalgal.ircs.task;

import com.prodigalgal.ircs.contracts.task.TaskMasterSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TaskRuntimeDetailResponse(
        UUID masterTaskId,
        TaskDetailSummary task,
        TaskMasterSnapshot snapshot,
        TaskMasterRuntimeSummary master,
        List<TaskPageRuntimeSummary> pages,
        int pageOffset,
        int pageLimit,
        int visiblePageCount,
        int discoveredPageCount,
        boolean redisSnapshotPresent,
        boolean redisStatePresent,
        Instant refreshedAt
) {
}

record TaskMasterRuntimeSummary(
        String status,
        UUID dataSourceId,
        String taskName,
        boolean resume,
        int startPage,
        Integer endPage,
        long pageScheduled,
        long pageDiscovered,
        long pageCompleted,
        long pageFailed,
        long detailScheduled,
        long detailCompleted,
        long detailSucceeded,
        long detailFailed,
        Integer totalPages,
        Integer totalItems,
        String lastError,
        String correlationId,
        Instant queuedAt,
        Instant updatedAt,
        Map<String, String> state
) {
}

record TaskPageRuntimeSummary(
        UUID pageTaskId,
        Integer pageNumber,
        long detailScheduled,
        long detailCompleted,
        long detailSucceeded,
        long detailFailed,
        String status,
        String lastError,
        Instant updatedAt,
        int completedDetailTaskCount,
        List<String> completedDetailTaskIds,
        int failedDetailTaskCount,
        List<String> failedDetailTaskIds,
        Map<String, String> failedDetailErrors,
        long detailBacklog,
        int progressPercent,
        String attentionLevel,
        Map<String, String> state
) {
}
