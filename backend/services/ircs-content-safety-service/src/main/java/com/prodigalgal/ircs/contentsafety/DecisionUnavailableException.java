package com.prodigalgal.ircs.contentsafety;

final class DecisionUnavailableException extends RuntimeException {
    private final String reason;
    private final long retryAfterSeconds;

    DecisionUnavailableException(String reason, long retryAfterSeconds) {
        super(reason);
        this.reason = reason;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    String reason() { return reason; }
    long retryAfterSeconds() { return retryAfterSeconds; }
}
