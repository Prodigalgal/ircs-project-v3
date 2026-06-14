package com.prodigalgal.ircs.search.sync;

import java.time.Instant;

public record SearchSyncTaskStatsResponse(
        boolean available,
        String unavailableReason,
        long total,
        long pending,
        long processing,
        long completed,
        long failed,
        long duePending,
        long rawVideo,
        long unifiedVideo,
        long indexOperations,
        long deleteOperations,
        int maxRetryCount,
        Instant oldestPendingAt,
        Instant nextDueAt) {

    static SearchSyncTaskStatsResponse unavailable(String reason) {
        return new SearchSyncTaskStatsResponse(
                false,
                reason,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null);
    }
}
