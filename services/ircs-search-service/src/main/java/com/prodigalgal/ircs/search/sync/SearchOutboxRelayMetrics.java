package com.prodigalgal.ircs.search.sync;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
class SearchOutboxRelayMetrics {

    private final MeterRegistry registry;

    SearchOutboxRelayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordRun(String workerId, Duration duration, String outcome, int published, int restored) {
        String safeWorkerId = safe(workerId);
        String safeOutcome = safe(outcome);
        Counter.builder("ircs.search.outbox.relay.runs")
                .tag("worker_id", safeWorkerId)
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("ircs.search.outbox.relay.run.duration")
                .tag("worker_id", safeWorkerId)
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(duration == null ? Duration.ZERO : duration);
        if (published > 0) {
            Counter.builder("ircs.search.outbox.relay.published")
                    .tag("worker_id", safeWorkerId)
                    .register(registry)
                    .increment(published);
        }
        if (restored > 0) {
            Counter.builder("ircs.search.outbox.relay.restored")
                    .tag("worker_id", safeWorkerId)
                    .register(registry)
                    .increment(restored);
        }
    }

    void recordGroupPublished(String workerId, SearchSyncTaskRecord task, Instant observedAt) {
        recordGroup(workerId, task, "published");
        if (task != null && task.createdAt() != null && observedAt != null) {
            Timer.builder("ircs.search.outbox.relay.task.delay")
                    .tag("worker_id", safe(workerId))
                    .tag("entity_type", safe(task.entityType().name()))
                    .tag("operation", safe(task.operation().name()))
                    .tag("result", "published")
                    .register(registry)
                    .record(Duration.between(task.createdAt(), observedAt));
        }
    }

    void recordGroupFailed(String workerId, SearchSyncTaskRecord task) {
        recordGroup(workerId, task, "failed");
    }

    private void recordGroup(String workerId, SearchSyncTaskRecord task, String result) {
        Counter.builder("ircs.search.outbox.relay.groups")
                .tag("worker_id", safe(workerId))
                .tag("entity_type", task == null ? "unknown" : safe(task.entityType().name()))
                .tag("operation", task == null ? "unknown" : safe(task.operation().name()))
                .tag("result", result)
                .register(registry)
                .increment();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
