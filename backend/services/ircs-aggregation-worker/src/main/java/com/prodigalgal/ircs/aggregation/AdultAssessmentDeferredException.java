package com.prodigalgal.ircs.aggregation;

import java.time.Duration;

/** Safe, bounded retry metadata. Provider response bodies and credentials never enter this exception. */
final class AdultAssessmentDeferredException extends RuntimeException {
    private final Duration retryAfter;

    AdultAssessmentDeferredException(String reason, long seconds) {
        super(reason);
        this.retryAfter = Duration.ofSeconds(Math.max(1, Math.min(604800, seconds)));
    }

    Duration retryAfter() { return retryAfter; }

    boolean waiting() { return isWaitingReason(getMessage()); }

    static boolean isWaitingReason(String reason) {
        return "QUEUE_PENDING".equals(reason) || "QUEUE_FULL".equals(reason)
                || "QUOTA_WAIT".equals(reason) || "COOLDOWN".equals(reason)
                || "CONTENT_SAFETY_SNAPSHOT_CHANGED".equals(reason);
    }
}
