package com.prodigalgal.ircs.common.work;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedRuntimeWorkQueueIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private DistributedRuntimeWorkQueue queue;
    private Runnable afterClaim = () -> {};

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        var redis = new StringRedisTemplate(connectionFactory) {
            @Override
            public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
                T result = super.execute(script, keys, args);
                if (script.getResultType() == List.class && keys.getFirst().endsWith(":pending")) {
                    afterClaim.run();
                }
                return result;
            }
        };
        queue = new DistributedRuntimeWorkQueue(redis, null, null, null,
                "ircs-test:" + UUID.randomUUID(), 1000, 5, "ircs-test-metrics:" + UUID.randomUUID(),
                Duration.ofSeconds(10), Duration.ofMinutes(30), Duration.ofMinutes(31));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void expiredTaskRecoversWithoutOpsAndRejectsOldConsumerCompletion() throws Exception {
        queue.submit(request("v1"));
        var oldClaim = queue.claim("test", "worker-a", 1, Duration.ofMillis(30)).getFirst();
        Thread.sleep(60);
        var current = queue.claim("test", "worker-b", 1, Duration.ofSeconds(10)).getFirst();

        assertThat(queue.complete(oldClaim)).isFalse();
        assertThat(queue.fail(oldClaim, false, Duration.ZERO, "late failure")).isFalse();
        assertThat(queue.counts("test").inflight()).isEqualTo(1);
        assertThat(queue.complete(current)).isTrue();
        assertThat(queue.complete(current)).isFalse();
    }

    @Test
    void attemptFencesAnExpiredClaimEvenWhenWorkerNameIsReused() throws Exception {
        queue.submit(request("v1"));
        var oldClaim = queue.claim("test", "same-worker", 1, Duration.ofMillis(30)).getFirst();
        Thread.sleep(60);
        var current = queue.claim("test", "same-worker", 1, Duration.ofSeconds(10)).getFirst();

        assertThat(current.attempt()).isEqualTo(oldClaim.attempt() + 1);
        assertThat(queue.complete(oldClaim)).isFalse();
        assertThat(queue.fail(oldClaim, true, Duration.ZERO, "late retry")).isFalse();
        assertThat(queue.complete(current)).isTrue();
    }

    @Test
    void repeatedFailuresReachDlqInsteadOfRetryingForever() {
        queue.submit(request("v1"));
        for (int attempt = 0; attempt < 5; attempt++) {
            var task = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
            assertThat(queue.fail(task, true, Duration.ZERO, "transient failure")).isTrue();
        }
        assertThat(queue.claim("test", "worker", 1, Duration.ofSeconds(10))).isEmpty();
        assertThat(queue.counts("test").dlq()).isEqualTo(1);
    }

    @Test
    void newerSubmissionCannotBeRemovedByAnEarlierCompletion() {
        queue.submit(request("v1"));
        var oldClaim = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
        queue.submit(request("v2"));

        assertThat(queue.complete(oldClaim)).isFalse();
        assertThat(queue.counts("test").pending()).isEqualTo(1);
        var current = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
        assertThat(current.version()).isEqualTo("v2");
        assertThat(queue.complete(current)).isTrue();
    }

    @Test
    void claimReturnsItsAtomicSnapshotWhenNewSubmissionArrivesBeforeReply() {
        queue.submit(request("v1"));
        afterClaim = () -> queue.submit(request("v2"));

        var oldClaim = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();

        assertThat(oldClaim.version()).isEqualTo("v1");
        assertThat(oldClaim.status()).isEqualTo("PROCESSING");
        assertThat(queue.complete(oldClaim)).isFalse();
        assertThat(queue.counts("test").pending()).isEqualTo(1);
    }

    @Test
    void dlqReplayGetsBoundedRetryBudgetWithoutReusingAnOldAttempt() {
        queue.submit(request("v1"));
        RuntimeWorkItem previous = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            previous = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
            queue.fail(previous, true, Duration.ZERO, "unavailable");
        }
        assertThat(queue.requeueDlq("test", 1, 1)).isEqualTo(1);
        var replay = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
        assertThat(replay.attempt()).isEqualTo(6);
        assertThat(queue.complete(previous)).isFalse();
        assertThat(queue.fail(replay, true, Duration.ZERO, "still unavailable")).isTrue();
        assertThat(queue.counts("test").pending()).isEqualTo(1);
        for (int attempt = 0; attempt < 4; attempt++) {
            var task = queue.claim("test", "worker", 1, Duration.ofSeconds(10)).getFirst();
            queue.fail(task, true, Duration.ZERO, "unavailable");
        }
        assertThat(queue.counts("test").dlq()).isEqualTo(1);
        assertThat(queue.requeueDlq("test", 1, 1)).isZero();
    }

    @Test
    void repeatedVisibilityExpirationsReachDlq() throws Exception {
        queue.submit(request("v1"));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(queue.claim("test", "worker", 1, Duration.ofMillis(10))).hasSize(1);
            Thread.sleep(30);
        }
        assertThat(queue.claim("test", "worker", 1, Duration.ofSeconds(10))).isEmpty();
        assertThat(queue.counts("test").dlq()).isEqualTo(1);
        assertThat(queue.sampleDlq("test", 1).getFirst().lastError()).isEqualTo("VISIBILITY_RETRY_EXHAUSTED");
    }

    private RuntimeWorkItemRequest request(String version) {
        return new RuntimeWorkItemRequest("test", "same-id", "aggregate", version, "{}");
    }
}
