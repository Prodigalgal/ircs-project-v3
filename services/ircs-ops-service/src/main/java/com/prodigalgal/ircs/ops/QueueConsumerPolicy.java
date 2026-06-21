package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.common.work.SystemConfigWorkSubmissionGate;
import java.util.List;

record QueueConsumerPolicy(
        boolean hasConsumer,
        List<SystemConfigWorkSubmissionGate.ConfigFlag> enabledFlags) {

    QueueConsumerPolicy {
        enabledFlags = enabledFlags == null ? List.of() : List.copyOf(enabledFlags);
    }

    static QueueConsumerPolicy enabled() {
        return new QueueConsumerPolicy(true, List.of());
    }

    static QueueConsumerPolicy enabled(List<SystemConfigWorkSubmissionGate.ConfigFlag> enabledFlags) {
        return new QueueConsumerPolicy(true, enabledFlags);
    }

    static QueueConsumerPolicy missing() {
        return new QueueConsumerPolicy(false, List.of());
    }
}
