package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.WorkSubmissionGate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Exercises atomic magnet submissions against the CI-provided Valkey instance. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedMagnetWorkSubmissionStoreIntegrationTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private DistributedMagnetWorkSubmissionStore store;
    private String prefix;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        prefix = "ircs-test:magnet-submit:" + UUID.randomUUID();
        ObjectProvider<Clock> clock = mock(ObjectProvider.class);
        when(clock.getIfAvailable()).thenReturn(Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC));
        store = new DistributedMagnetWorkSubmissionStore(redis, null, clock, prefix, 1000,
                null, prefix + ":rate", null, null, null);
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            var keys = redis.keys(prefix + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
        if (connection != null) connection.destroy();
    }

    @Test
    void concurrentDuplicateSubmissionCreatesOneTaskAndEvent() throws Exception {
        RuntimeWorkItemRequest request = request();
        try (var pool = Executors.newFixedThreadPool(12)) {
            var results = java.util.stream.IntStream.range(0, 48)
                    .mapToObj(ignored -> pool.submit(() -> store.submitIfAbsent(request))).toList();
            long accepted = 0;
            for (var result : results) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(redis.opsForHash().get(taskKey(request), "status")).isEqualTo("PENDING");
        assertThat(redis.opsForStream().size(prefix + ":events")).isEqualTo(1L);
        assertThat(redis.opsForHash().get(prefix + ":rate:bucket:" + Instant.parse("2026-11-16T00:00:00Z").toEpochMilli(), "runtime.magnet_search.submitted"))
                .isEqualTo("1");
    }

    @Test
    void preservesProcessingOwnerSubmissionAndAttempt() {
        RuntimeWorkItemRequest request = request();
        seedTask(request, "PROCESSING", "owner-a", "submission-a", "4");
        assertThat(store.submitIfAbsent(request)).isFalse();
        assertThat(redis.opsForHash().get(taskKey(request), "ownerId")).isEqualTo("owner-a");
        assertThat(redis.opsForHash().get(taskKey(request), "submissionId")).isEqualTo("submission-a");
        assertThat(redis.opsForHash().get(taskKey(request), "attempt")).isEqualTo("4");
    }

    @Test
    void doesNotReviveTerminalTasksOrInflightAndDlqEntries() {
        RuntimeWorkItemRequest processed = request();
        seedTask(processed, "PROCESSED", "", "done", "1");
        assertThat(store.submitIfAbsent(processed)).isFalse();
        RuntimeWorkItemRequest inflight = request();
        redis.opsForZSet().add(prefix + ":magnet.search:inflight", inflight.taskId(), 1);
        assertThat(store.submitIfAbsent(inflight)).isFalse();
        RuntimeWorkItemRequest dlq = request();
        redis.opsForZSet().add(prefix + ":magnet.search:dlq", dlq.taskId(), 1);
        assertThat(store.submitIfAbsent(dlq)).isFalse();
        assertThat(redis.opsForHash().hasKey(taskKey(inflight), "status")).isFalse();
        assertThat(redis.opsForHash().hasKey(taskKey(dlq), "status")).isFalse();
        assertThat(redis.opsForZSet().score(prefix + ":magnet.search:inflight", inflight.taskId())).isEqualTo(1.0);
        assertThat(redis.opsForZSet().score(prefix + ":magnet.search:dlq", dlq.taskId())).isEqualTo(1.0);
    }

    @Test
    void disabledSubmissionGateMakesNoRedisWrites() {
        RuntimeWorkItemRequest request = request();
        WorkSubmissionGate gate = mock(WorkSubmissionGate.class);
        when(gate.canSubmitRuntime(MagnetWorkTypes.SEARCH)).thenReturn(false);
        @SuppressWarnings("unchecked") ObjectProvider<WorkSubmissionGate> gates = mock(ObjectProvider.class);
        when(gates.getIfAvailable()).thenReturn(gate);
        var gated = new DistributedMagnetWorkSubmissionStore(redis, gates, null, prefix, 1000,
                null, prefix + ":rate", null, null, null);

        assertThat(gated.submitIfAbsent(request)).isFalse();
        assertThat(redis.opsForHash().hasKey(taskKey(request), "status")).isFalse();
        assertThat(redis.opsForZSet().score(prefix + ":magnet.search:pending", request.taskId())).isNull();
        assertThat(redis.opsForStream().size(prefix + ":events")).isEqualTo(0L);
    }

    @Test
    void repairsPendingIndexWithoutChangingItsDueTime() {
        RuntimeWorkItemRequest request = request();
        String pending = prefix + ":magnet.search:pending";
        redis.opsForZSet().add(pending, request.taskId(), 12345);
        assertThat(store.submitIfAbsent(request)).isTrue();
        assertThat(redis.opsForHash().get(taskKey(request), "status")).isEqualTo("PENDING");
        assertThat(redis.opsForHash().get(taskKey(request), "dueAt")).isEqualTo("12345");
        assertThat(redis.opsForZSet().score(pending, request.taskId())).isEqualTo(12345.0);
    }

    @Test
    void refusesExistingHashEvenWhenItsStateIsPending() {
        RuntimeWorkItemRequest request = request();
        seedTask(request, "PENDING", "", "original", "2");
        assertThat(store.submitIfAbsent(request)).isFalse();
        assertThat(redis.opsForHash().get(taskKey(request), "submissionId")).isEqualTo("original");
    }

    private RuntimeWorkItemRequest request() {
        return new RuntimeWorkItemRequest(MagnetWorkTypes.SEARCH,
                MagnetWorkTypes.searchTaskId(UUID.randomUUID()), UUID.randomUUID().toString(), "v1", "{}");
    }

    private String taskKey(RuntimeWorkItemRequest request) {
        return prefix + ":magnet.search:task:" + request.taskId();
    }

    private void seedTask(RuntimeWorkItemRequest request, String status, String owner, String submission, String attempt) {
        redis.opsForHash().putAll(taskKey(request), java.util.Map.of(
                "status", status, "ownerId", owner, "submissionId", submission, "attempt", attempt));
    }
}
