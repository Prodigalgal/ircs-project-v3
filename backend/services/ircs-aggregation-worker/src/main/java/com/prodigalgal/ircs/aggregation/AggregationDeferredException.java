package com.prodigalgal.ircs.aggregation;

import java.time.Duration;

/** Expected, retryable aggregation contention after its database transaction has rolled back. */
final class AggregationDeferredException extends RuntimeException {
    private final Duration retryAfter;

    AggregationDeferredException(String reason, long retryAfterSeconds) {
        super(reason);
        if (!isWaitingReason(reason)) {
            throw new IllegalArgumentException("Unsupported aggregation waiting reason: " + reason);
        }
        retryAfter = Duration.ofSeconds(Math.max(2, Math.min(604800, retryAfterSeconds)));
    }

    Duration retryAfter() { return retryAfter; }

    static boolean isWaitingReason(String reason) {
        return "WAITING_DEPENDENCIES".equals(reason) || "NOT_ELIGIBLE".equals(reason)
                || "MATCH_LOCK_BUSY".equals(reason) || "SOURCE_CHANGED".equals(reason)
                || "IDENTITY_CONFLICT".equals(reason) || "MERGE_AMBIGUOUS".equals(reason);
    }
}
