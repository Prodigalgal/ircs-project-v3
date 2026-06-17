package com.prodigalgal.ircs.search.sync;

import java.time.Instant;

record SearchSyncWorkQueueState(
        boolean running,
        String workerId,
        Instant lastRunAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int lastProcessed,
        int lastFailed,
        int lastRequeued,
        long consecutiveFailures,
        String lastError) {
}
