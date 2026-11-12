package com.prodigalgal.ircs.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class TaskFailureClassifierTest {

    private final TaskFailureClassifier classifier = new TaskFailureClassifier();

    @Test
    void illegalArgumentIsFatal() {
        assertThat(classifier.classify(new IllegalArgumentException("bad mapping config")))
                .isEqualTo(TaskFailureDisposition.DLQ);
    }

    @Test
    void nestedIoFailureIsRetryable() {
        RuntimeException error = new RuntimeException("source fetch failed", new IOException("connection reset"));

        assertThat(classifier.classify(error)).isEqualTo(TaskFailureDisposition.RETRY);
    }

    @Test
    void unknownRuntimeFailureDefaultsRetryable() {
        assertThat(classifier.classify(new IllegalStateException("redis unavailable")))
                .isEqualTo(TaskFailureDisposition.RETRY);
    }

    @Test
    void httpRateLimitAndServerErrorsAreRetryable() {
        assertThat(classifier.classify(new TaskHttpStatusException(429, "Fake", "https://source.example/list")))
                .isEqualTo(TaskFailureDisposition.RETRY);
        assertThat(classifier.classify(new IllegalStateException("provider fetch failed: upstream status 503")))
                .isEqualTo(TaskFailureDisposition.RETRY);
    }

    @Test
    void deterministicHttpClientErrorsAreFatal() {
        assertThat(classifier.classify(new TaskHttpStatusException(404, "Fake", "https://source.example/list")))
                .isEqualTo(TaskFailureDisposition.DLQ);
        assertThat(classifier.classify(new IllegalStateException(
                "wrapped",
                new IllegalArgumentException("HTTP status 410 from data source Fake"))))
                .isEqualTo(TaskFailureDisposition.DLQ);
    }

    @Test
    void sourceTerminalFailureIsFatal() {
        assertThat(classifier.classify(new TaskSourceTerminalException(
                "Fake",
                "invalid JSON list response",
                new RuntimeException("html"))))
                .isEqualTo(TaskFailureDisposition.DLQ);
    }
}
