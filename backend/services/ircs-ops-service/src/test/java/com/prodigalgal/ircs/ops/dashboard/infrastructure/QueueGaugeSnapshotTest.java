package com.prodigalgal.ircs.ops.dashboard.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.contracts.queue.QueueTopic;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueClient;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueueSnapshot;
import com.prodigalgal.ircs.ops.infrastructure.broker.BrokerManagementQueues;
import com.prodigalgal.ircs.ops.queue.domain.RuntimeWorkQueueCatalog;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.ObjectProvider;

class QueueGaugeSnapshotTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RuntimeWorkQueue> provider = mock(ObjectProvider.class);
    private final RuntimeWorkQueue queue = mock(RuntimeWorkQueue.class);
    private final BrokerManagementQueueClient broker = mock(BrokerManagementQueueClient.class);
    private final RabbitAdmin rabbitAdmin = mock(RabbitAdmin.class);
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    private final MutableClock clock = new MutableClock();
    private final QueueGaugeSnapshot gauges = new QueueGaugeSnapshot(provider, broker, rabbitAdmin, registry, clock);

    @BeforeEach
    void register() {
        gauges.registerGauges();
        when(provider.getIfAvailable()).thenReturn(queue);
        when(queue.counts(anyString())).thenReturn(new RuntimeWorkQueueCounts(7, 8, 9));
        when(broker.fetchQueueSnapshots()).thenReturn(nativeQueues(12));
    }

    @AfterEach
    void close() {
        gauges.close();
        registry.close();
    }

    @Test
    void initialScrapePreservesEveryQueueSeriesAsUnknownWithoutReadingDependencies() {
        registry.scrape();

        assertThat(registry.find("ircs.valkey.runtime_work.pending").gauges())
                .hasSize(RuntimeWorkQueueCatalog.descriptors().size())
                .allSatisfy(gauge -> assertThat(gauge.value()).isNaN());
        assertThat(registry.find("ircs.rabbit.queue.messages").gauges())
                .hasSize(QueueTopic.values().length * 3)
                .allSatisfy(gauge -> assertThat(gauge.value()).isNaN());
        assertThat(age("runtime")).isNaN();
        assertThat(age("rabbit")).isNaN();
        verifyNoInteractions(provider, queue, broker, rabbitAdmin);
    }

    @Test
    void repeatedRealPrometheusScrapesDoNotRepeatTheOncePerTaskBackgroundReads() {
        gauges.refresh();

        RuntimeWorkQueueCatalog.descriptors().forEach(descriptor -> verify(queue).counts(descriptor.taskType()));
        verify(broker).fetchQueueSnapshots();
        verifyNoInteractions(rabbitAdmin);
        assertThat(registry.get("ircs.valkey.runtime_work.pending").tag("task_type", firstTask()).gauge().value())
                .isEqualTo(7);
        assertThat(registry.get("ircs.rabbit.queue.messages").tag("queue", QueueTopic.INGEST_VIDEO.queueName())
                .tag("topic", QueueTopic.INGEST_VIDEO.name()).tag("queue_role", "main").tag("dlq", "false")
                .gauge().value()).isEqualTo(12);
        clearInvocations(provider, queue, broker, rabbitAdmin);

        for (int scrape = 0; scrape < 20; scrape++) {
            String exposition = registry.scrape();
            assertThat(exposition).contains("ircs_valkey_runtime_work_pending", "ircs_rabbit_queue_messages");
        }

        verifyNoInteractions(provider, queue, broker, rabbitAdmin);
    }

    @Test
    void unavailableSourcesRetainLastSuccessfulValuesWhileAgeAndFailureCountersAdvance() {
        gauges.refresh();
        QueueGaugeSnapshot.Snapshot previous = gauges.currentSnapshot();
        clock.advance(Duration.ofSeconds(12));
        when(queue.counts(anyString())).thenThrow(new IllegalStateException("Valkey unavailable"));
        when(broker.fetchQueueSnapshots()).thenReturn(Optional.empty());
        when(rabbitAdmin.getQueueProperties(anyString())).thenThrow(new IllegalStateException("Rabbit unavailable"));

        gauges.refresh();

        assertThat(gauges.currentSnapshot().runtime()).isSameAs(previous.runtime());
        assertThat(gauges.currentSnapshot().rabbit()).isSameAs(previous.rabbit());
        assertThat(age("runtime")).isEqualTo(12);
        assertThat(age("rabbit")).isEqualTo(12);
        for (String source : List.of("runtime", "rabbit")) {
            assertThat(registry.get("ircs.ops.queue.snapshot.refresh.failures").tag("source", source)
                    .counter().count()).isEqualTo(1);
            assertThat(registry.get("ircs.ops.queue.snapshot.refresh.duration").tag("source", source)
                    .timer().count()).isEqualTo(2);
        }
        assertThat(registry.get("ircs.valkey.runtime_work.dlq").tag("task_type", firstTask()).gauge().value())
                .isEqualTo(9);
    }

    @Test
    void failedFirstReadRemainsUnknownAndHealthySourceCanStillAdvance() {
        when(queue.counts(anyString())).thenThrow(new IllegalStateException("Valkey unavailable"));

        gauges.refresh();

        assertThat(age("runtime")).isNaN();
        assertThat(age("rabbit")).isZero();
        assertThat(registry.get("ircs.valkey.runtime_work.pending").tag("task_type", firstTask()).gauge().value())
                .isNaN();
        assertThat(gauges.currentSnapshot().rabbit().values().get(QueueTopic.INGEST_VIDEO.queueName()).messageCount())
                .isEqualTo(12);
    }

    @Test
    void managementFailureFallsBackOncePerQueueOnlyDuringRefresh() {
        when(broker.fetchQueueSnapshots()).thenReturn(Optional.empty());
        Properties properties = new Properties();
        properties.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 4);
        properties.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);
        when(rabbitAdmin.getQueueProperties(anyString())).thenReturn(properties);

        gauges.refresh();

        assertThat(gauges.currentSnapshot().rabbit().values()).allSatisfy((name, state) -> {
            verify(rabbitAdmin).getQueueProperties(name);
            assertThat(state.messageCount()).isEqualTo(4);
            assertThat(state.consumerCount()).isEqualTo(2);
        });
        clearInvocations(rabbitAdmin, broker);
        registry.scrape();
        verifyNoInteractions(rabbitAdmin, broker);
    }

    @Test
    void missingFallbackPropertiesAreUnknownRatherThanSuccessfulZeroes() {
        when(broker.fetchQueueSnapshots()).thenReturn(Optional.empty());
        when(rabbitAdmin.getQueueProperties(anyString())).thenReturn(null);

        gauges.refresh();

        assertThat(gauges.currentSnapshot().rabbit().values()).isEmpty();
        assertThat(age("rabbit")).isNaN();
        assertThat(registry.get("ircs.ops.queue.snapshot.refresh.failures").tag("source", "rabbit")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void scheduledRefreshRunsOffCallerAndOverlappingRefreshesNeverReadSources() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> refreshThread = new AtomicReference<>();
        when(queue.counts(firstTask())).thenAnswer(invocation -> {
            refreshThread.set(Thread.currentThread());
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new RuntimeWorkQueueCounts(7, 8, 9);
        });

        try {
            gauges.runScheduled();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(refreshThread.get()).isNotSameAs(Thread.currentThread());
            for (int overlap = 0; overlap < 10; overlap++) {
                gauges.refresh();
            }
            registry.scrape();
            verify(provider, times(1)).getIfAvailable();
            verify(broker, never()).fetchQueueSnapshots();
        } finally {
            release.countDown();
        }
        await(() -> gauges.currentSnapshot().rabbit().succeededAt() != null);
        RuntimeWorkQueueCatalog.descriptors().forEach(descriptor -> verify(queue).counts(descriptor.taskType()));
        verify(broker).fetchQueueSnapshots();
    }

    @Test
    void inFlightRefreshPublishesBothImmutableMapsAtomically() throws Exception {
        gauges.refresh();
        QueueGaugeSnapshot.Snapshot previous = gauges.currentSnapshot();
        clock.advance(Duration.ofSeconds(5));
        when(queue.counts(anyString())).thenReturn(new RuntimeWorkQueueCounts(21, 22, 23));
        when(broker.fetchQueueSnapshots()).thenReturn(nativeQueues(24));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        String lastTask = RuntimeWorkQueueCatalog.descriptors().getLast().taskType();
        when(queue.counts(lastTask)).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new RuntimeWorkQueueCounts(21, 22, 23);
        });
        Thread refresh = Thread.ofVirtual().start(gauges::refresh);
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            for (int read = 0; read < 20; read++) {
                assertThat(gauges.currentSnapshot()).isSameAs(previous);
                registry.scrape();
            }
            assertThatThrownBy(() -> previous.runtime().values().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            release.countDown();
            refresh.join(5000);
        }
        assertThat(refresh.isAlive()).isFalse();
        QueueGaugeSnapshot.Snapshot published = gauges.currentSnapshot();
        assertThat(published.runtime().values()).allSatisfy((task, counts) -> assertThat(counts.pending()).isEqualTo(21));
        assertThat(published.rabbit().values().get(QueueTopic.INGEST_VIDEO.queueName()).messageCount()).isEqualTo(24);
        assertThat(published.runtime().succeededAt()).isEqualTo(clock.instant());
        assertThat(published.rabbit().succeededAt()).isEqualTo(clock.instant());
    }

    @Test
    void closedComponentDoesNotScheduleOrRefresh() {
        gauges.close();
        gauges.runScheduled();
        gauges.refresh();

        verifyNoInteractions(provider, queue, broker, rabbitAdmin);
    }

    @Test
    void closingInterruptsInFlightRefreshAndDoesNotStartAnotherSourceOrPublish() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(queue.counts(firstTask())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("Refresh should have been interrupted");
            } catch (InterruptedException ex) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Refresh interrupted", ex);
            }
        });

        gauges.runScheduled();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        gauges.close();

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        await(() -> registry.get("ircs.ops.queue.snapshot.refresh.running").gauge().value() == 0);
        assertThat(gauges.currentSnapshot().runtime().values()).isEmpty();
        assertThat(gauges.currentSnapshot().rabbit().values()).isEmpty();
        verifyNoInteractions(broker, rabbitAdmin);
    }

    private double age(String source) {
        return registry.get("ircs.ops.queue.snapshot.age").tag("source", source).gauge().value();
    }

    private static String firstTask() {
        return RuntimeWorkQueueCatalog.descriptors().getFirst().taskType();
    }

    private static Optional<BrokerManagementQueues> nativeQueues(int total) {
        return Optional.of(new BrokerManagementQueues(null, List.of(
                new BrokerManagementQueueSnapshot(QueueTopic.INGEST_VIDEO.queueName(), total - 2, 2, total, 3))));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-14T00:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now.get(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
