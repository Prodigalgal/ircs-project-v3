package com.prodigalgal.ircs.magnet;

/** A persisted provider outcome; retryability is independent of the job's partial results. */
final class MagnetSearchExecutionException extends RuntimeException {
    private final boolean retryable;

    MagnetSearchExecutionException(boolean retryable, String reason) {
        super(reason);
        this.retryable = retryable;
    }

    boolean retryable() { return retryable; }
}
