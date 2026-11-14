package com.prodigalgal.ircs.ops.dashboard.infrastructure;

import com.prodigalgal.ircs.common.scheduling.ScheduledTriggers;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueues;
import com.prodigalgal.ircs.ops.queue.domain.QueueState;
import com.prodigalgal.ircs.ops.queue.domain.RuntimeWorkQueueCatalog;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Prometheus callbacks never perform broker or Valkey I/O. */
@Component
@RequiredArgsConstructor
@Slf4j
class QueueGaugeSnapshot {

    private static final List<String> TASK_TYPES = RuntimeWorkQueueCatalog.descriptors().stream()
            .map(descriptor -> descriptor.taskType()).distinct().toList();
    private static final List<String> QUEUE_NAMES = Arrays.stream(QueueTopic.values())
            .flatMap(topic -> Stream.of(topic.queueName(), topic.retryName(), topic.dlqName()))
            .distinct().toList();

    private final ObjectProvider<RuntimeWorkQueue> runtimeWorkQueueProvider;
    private final BrokerManagementQueueClient brokerClient;
    private final RabbitAdmin rabbitAdmin;
    private final MeterRegistry registry;
    private final Clock clock;
    private final ExecutorService refreshExecutor =
            ScheduledTriggers.virtualThreadExecutor("ops-queue-gauges-refresh-");
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile boolean closed;
    private volatile Snapshot snapshot = new Snapshot(SourceSnapshot.empty(), SourceSnapshot.empty());
    private Counter runtimeFailures;
    private Counter rabbitFailures;
    private Timer runtimeDuration;
    private Timer rabbitDuration;

    @PostConstruct
    void registerGauges() {
        for (String taskType : TASK_TYPES) {
            runtimeGauge("pending", taskType, RuntimeWorkQueueCounts::pending);
            runtimeGauge("inflight", taskType, RuntimeWorkQueueCounts::inflight);
            runtimeGauge("dlq", taskType, RuntimeWorkQueueCounts::dlq);
        }
        for (QueueTopic topic : QueueTopic.values()) {
            rabbitGauges(topic, topic.queueName(), "main", false);
            rabbitGauges(topic, topic.retryName(), "retry", false);
            rabbitGauges(topic, topic.dlqName(), "dlq", true);
        }
        runtimeFailures = failureCounter("runtime");
        rabbitFailures = failureCounter("rabbit");
        runtimeDuration = refreshTimer("runtime");
        rabbitDuration = refreshTimer("rabbit");
        ageGauge("runtime", () -> snapshot.runtime().succeededAt());
        ageGauge("rabbit", () -> snapshot.rabbit().succeededAt());
        Gauge.builder("ircs.ops.queue.snapshot.refresh.running", refreshing, value -> value.get() ? 1 : 0)
                .description("Whether a queue metric snapshot refresh is in progress")
                .register(registry);
    }

    @Scheduled(initialDelayString = "${app.ops.queue-gauges.initial-delay-ms:0}",
            fixedDelayString = "${app.ops.queue-gauges.refresh-delay-ms:5000}")
    public void runScheduled() {
        if (!closed) {
            ScheduledTriggers.submit(refreshExecutor, this::refresh, log, "ops.queue-gauges.refresh");
        }
    }

    void refresh() {
        if (closed || !refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            Snapshot previous = snapshot;
            SourceSnapshot<RuntimeWorkQueueCounts> runtime = refreshRuntime(previous.runtime());
            if (closed || Thread.currentThread().isInterrupted()) {
                return;
            }
            SourceSnapshot<QueueState> rabbit = refreshRabbit(previous.rabbit());
            // Both maps and their success timestamps become visible with one volatile publication.
            if (!closed) {
                snapshot = new Snapshot(runtime, rabbit);
            }
        } finally {
            refreshing.set(false);
        }
    }

    private SourceSnapshot<RuntimeWorkQueueCounts> refreshRuntime(SourceSnapshot<RuntimeWorkQueueCounts> previous) {
        long started = System.nanoTime();
        try {
            RuntimeWorkQueue queue = Objects.requireNonNull(runtimeWorkQueueProvider.getIfAvailable(),
                    "Runtime work queue unavailable");
            Map<String, RuntimeWorkQueueCounts> counts = new LinkedHashMap<>();
            for (String taskType : TASK_TYPES) {
                counts.put(taskType, Objects.requireNonNull(queue.counts(taskType), "Runtime counts unavailable"));
            }
            return new SourceSnapshot<>(counts, clock.instant());
        } catch (RuntimeException ex) {
            runtimeFailures.increment();
            log.warn("Runtime queue gauge refresh failed; retaining last successful snapshot", ex);
            return previous;
        } finally {
            runtimeDuration.record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private SourceSnapshot<QueueState> refreshRabbit(SourceSnapshot<QueueState> previous) {
        long started = System.nanoTime();
        try {
            Optional<BrokerManagementQueues> nativeQueues = brokerClient.fetchQueueSnapshots();
            Map<String, BrokerManagementQueueSnapshot> nativeByName = nativeQueues
                    .map(BrokerManagementQueues::byName).orElse(Map.of());
            Map<String, QueueState> queues = new LinkedHashMap<>();
            for (String queueName : QUEUE_NAMES) {
                if (nativeQueues.isPresent()) {
                    BrokerManagementQueueSnapshot nativeQueue = nativeByName.get(queueName);
                    // A successful complete broker listing authoritatively identifies absent queues.
                    queues.put(queueName, nativeQueue == null ? QueueState.empty()
                            : new QueueState(nativeQueue.messagesTotal(), nativeQueue.consumers()));
                } else {
                    Properties properties = Objects.requireNonNull(rabbitAdmin.getQueueProperties(queueName),
                            "Rabbit queue properties unavailable");
                    queues.put(queueName, new QueueState(property(properties, RabbitAdmin.QUEUE_MESSAGE_COUNT),
                            property(properties, RabbitAdmin.QUEUE_CONSUMER_COUNT)));
                }
            }
            return new SourceSnapshot<>(queues, clock.instant());
        } catch (RuntimeException ex) {
            rabbitFailures.increment();
            log.warn("Rabbit queue gauge refresh failed; retaining last successful snapshot", ex);
            return previous;
        } finally {
            rabbitDuration.record(Duration.ofNanos(System.nanoTime() - started));
        }
    }

    private int property(Properties properties, String key) {
        Object value = properties.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Rabbit queue count unavailable: " + key);
        }
        return number.intValue();
    }

    private void runtimeGauge(String role, String taskType, ToDoubleFunction<RuntimeWorkQueueCounts> value) {
        Gauge.builder("ircs.valkey.runtime_work." + role, this, owner -> {
                    RuntimeWorkQueueCounts counts = owner.snapshot.runtime().values().get(taskType);
                    return counts == null ? Double.NaN : value.applyAsDouble(counts);
                })
                .tag("task_type", taskType)
                .description("Valkey runtime work " + role + " task count observed by ops-service")
                .register(registry);
    }

    private void rabbitGauges(QueueTopic topic, String queueName, String role, boolean dlq) {
        rabbitGauge("messages", topic, queueName, role, dlq, QueueState::messageCount);
        rabbitGauge("consumers", topic, queueName, role, dlq, QueueState::consumerCount);
    }

    private void rabbitGauge(String metric, QueueTopic topic, String queueName, String role, boolean dlq,
            ToDoubleFunction<QueueState> value) {
        Gauge.builder("ircs.rabbit.queue." + metric, this, owner -> {
                    QueueState state = owner.snapshot.rabbit().values().get(queueName);
                    return state == null ? Double.NaN : value.applyAsDouble(state);
                })
                .tag("topic", topic.name()).tag("queue", queueName)
                .tag("queue_role", role).tag("dlq", String.valueOf(dlq))
                .description("RabbitMQ queue " + metric + " observed by ops-service")
                .register(registry);
    }

    private void ageGauge(String source, java.util.function.Supplier<Instant> successTime) {
        Gauge.builder("ircs.ops.queue.snapshot.age", successTime, value -> {
                    Instant succeededAt = value.get();
                    return succeededAt == null ? Double.NaN
                            : Math.max(0, Duration.between(succeededAt, clock.instant()).toMillis() / 1000.0);
                })
                .tag("source", source).baseUnit("seconds").strongReference(true)
                .description("Age of last successful queue snapshot; NaN until first success")
                .register(registry);
    }

    private Counter failureCounter(String source) {
        return Counter.builder("ircs.ops.queue.snapshot.refresh.failures").tag("source", source)
                .description("Failed queue snapshot refreshes; previous values are retained").register(registry);
    }

    private Timer refreshTimer(String source) {
        return Timer.builder("ircs.ops.queue.snapshot.refresh.duration").tag("source", source)
                .description("Queue snapshot source refresh duration, including failed attempts").register(registry);
    }

    Snapshot currentSnapshot() {
        return snapshot;
    }

    @PreDestroy
    void close() {
        closed = true;
        refreshExecutor.shutdownNow();
    }

    record Snapshot(SourceSnapshot<RuntimeWorkQueueCounts> runtime, SourceSnapshot<QueueState> rabbit) {
    }

    record SourceSnapshot<T>(Map<String, T> values, Instant succeededAt) {
        SourceSnapshot {
            values = Map.copyOf(values);
        }

        static <T> SourceSnapshot<T> empty() {
            return new SourceSnapshot<>(Map.of(), null);
        }
    }
}
