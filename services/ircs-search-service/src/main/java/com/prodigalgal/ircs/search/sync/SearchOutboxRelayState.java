package com.prodigalgal.ircs.search.sync;

import java.time.Instant;

record SearchOutboxRelayState(
        boolean running,
        String workerId,
        Instant lastRunAt,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        int lastPublished,
        int lastRestored,
        long consecutiveFailures,
        String lastError
) {
}
