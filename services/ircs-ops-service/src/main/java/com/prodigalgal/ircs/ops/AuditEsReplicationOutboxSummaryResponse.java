package com.prodigalgal.ircs.ops;

import java.time.Instant;

public record AuditEsReplicationOutboxSummaryResponse(
        long total,
        long pending,
        long processing,
        long succeeded,
        long failed,
        long maxAttemptCount,
        Instant oldestPendingAt,
        Instant nextAttemptAt
) {
}
