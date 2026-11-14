package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.work.DistributedRuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Real Valkey contract test against the tracked common queue, never a duplicate fake queue. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedAggregationWorkDeferrerIntegrationTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private DistributedRuntimeWorkQueue queue;
    private DistributedAggregationWorkDeferrer deferrer;
    private final MutableClock clock = new MutableClock();
    private String prefix;
    private String metrics;
    private final String type = AggregationWorkTypes.RAW_VIDEO;
    private final String taskId = AggregationWorkTypes.taskId(UUID.randomUUID());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        prefix = "ircs-test:aggregation-wait:" + UUID.randomUUID();
        metrics = prefix + ":metrics";
        ObjectProvider<Clock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(clock);
        queue = new DistributedRuntimeWorkQueue(redis, null, null, provider, prefix, 1000, 5,
                metrics, Duration.ofSeconds(10), Duration.ofMinutes(30), Duration.ofMinutes(31));
        deferrer = new DistributedAggregationWorkDeferrer(redis, provider, prefix, 1000);
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            // Each test owns this unique prefix, including its rate metric keys.
            var keys = redis.keys(prefix + ":*");
            if (keys != null && !keys.isEmpty()) { redis.delete(keys); }
        }
        if (connection != null) { connection.destroy(); }
    }

    @Test
    void repeatedWaitingRetainsClaimFencesAndNeverConsumesFiveFailureAttempts() {
        queue.submit(request("v1"));
        RuntimeWorkItem old = null;
        for (int i = 1; i <= 9; i++) {
            RuntimeWorkItem current = claim("same-owner");
            assertThat(current.attempt()).isEqualTo(i);
            assertThat(deferrer.failureAttempt(current)).isEqualTo(1);
            if (old != null) {
                assertThat(deferrer.defer(old, Duration.ofSeconds(2), "QUEUE_PENDING")).isFalse();
                assertThat(queue.complete(old)).isFalse();
            }
            assertThat(deferrer.defer(current, Duration.ofSeconds(2), "QUEUE_PENDING")).isTrue();
            assertThat(deferrer.defer(current, Duration.ofSeconds(2), "QUEUE_PENDING")).isFalse();
            assertThat(queue.complete(current)).isFalse();
            assertThat(queue.fail(current, true, Duration.ZERO, "late")).isFalse();
            assertThat(queue.counts(type).pending()).isEqualTo(1);
            assertThat(queue.counts(type).inflight()).isZero();
            assertThat(queue.counts(type).dlq()).isZero();
            assertThat(queue.claim(type, "other-owner", 1, Duration.ofMinutes(1))).isEmpty();
            old = current;
            clock.advance(Duration.ofSeconds(2));
        }
        RuntimeWorkItem current = claim("fresh-owner");
        assertThat(queue.complete(current)).isTrue();
        assertThat(deferrer.defer(old, Duration.ofSeconds(2), "QUEUE_PENDING")).isFalse();
    }

    @Test
    void genuineFailuresStillReachDlqAfterFiveFailuresDespiteInterleavedWaiting() {
        queue.submit(request("v1"));
        for (int failure = 1; failure <= 5; failure++) {
            RuntimeWorkItem waiting = claim("worker");
            assertThat(deferrer.defer(waiting, Duration.ofSeconds(2), "QUOTA_WAIT")).isTrue();
            clock.advance(Duration.ofSeconds(2));
            RuntimeWorkItem failed = claim("worker");
            assertThat(deferrer.failureAttempt(failed)).isEqualTo(failure);
            assertThat(queue.fail(failed, true, Duration.ZERO, "PROVIDER_ERROR")).isTrue();
        }
        assertThat(queue.counts(type).dlq()).isEqualTo(1);
        assertThat(queue.claim(type, "worker", 1, Duration.ofMinutes(1))).isEmpty();
        assertThat(queue.requeueDlq(type, 1, 1)).isEqualTo(1);
        RuntimeWorkItem replay = claim("worker");
        assertThat(deferrer.failureAttempt(replay)).isEqualTo(1);
        assertThat(queue.complete(replay)).isTrue();
    }

    @Test
    void newerSubmissionAndDifferentTaskTypeCannotBeChangedByOldDeferral() {
        queue.submit(request("v1"));
        RuntimeWorkItem old = claim("owner");
        queue.submit(request("v2"));
        assertThat(deferrer.defer(old, Duration.ofSeconds(2), "QUEUE_FULL")).isFalse();
        RuntimeWorkItem current = claim("owner");
        assertThat(current.version()).isEqualTo("v2");
        var wrongType = new RuntimeWorkItem("search.raw-video", current.taskId(), current.submissionId(),
                current.aggregateId(), current.version(), current.payload(), current.status(), current.attempt(),
                current.createdAt(), current.updatedAt(), current.dueAt(), current.visibleAt(), current.ownerId(), current.lastError());
        assertThat(deferrer.defer(wrongType, Duration.ofSeconds(2), "QUEUE_PENDING")).isFalse();
        assertThat(deferrer.defer(current, Duration.ofSeconds(2), "PROVIDER_ERROR")).isFalse();
        assertThat(queue.counts(type).inflight()).isEqualTo(1);
        assertThat(queue.complete(current)).isTrue();
    }

    @Test
    void expiredOwnerCannotDeferEvenBeforeItsReplacementClaims() {
        queue.submit(request("v1"));
        RuntimeWorkItem old = queue.claim(type, "owner-a", 1, Duration.ofSeconds(1)).getFirst();
        clock.advance(Duration.ofSeconds(2));
        assertThat(deferrer.defer(old, Duration.ofSeconds(2), "COOLDOWN")).isFalse();
        RuntimeWorkItem replacement = claim("owner-b");
        assertThat(deferrer.defer(old, Duration.ofSeconds(2), "QUEUE_PENDING")).isFalse();
        assertThat(queue.complete(replacement)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"MATCH_LOCK_BUSY", "NOT_ELIGIBLE", "WAITING_DEPENDENCIES", "SOURCE_CHANGED",
            "IDENTITY_CONFLICT", "MERGE_AMBIGUOUS"})
    void aggregationWaitReasonsRemainDurableAndDoNotSpendFailureBudget(String reason) {
        queue.submit(request("v1"));
        RuntimeWorkItem waiting = claim("worker");
        assertThat(deferrer.defer(waiting, Duration.ofHours(1), reason)).isTrue();
        assertThat(queue.counts(type).pending()).isEqualTo(1);
        assertThat(queue.counts(type).inflight()).isZero();
        assertThat(queue.claim(type, "other-worker", 1, Duration.ofMinutes(1))).isEmpty();
        clock.advance(Duration.ofHours(1));
        RuntimeWorkItem resumed = claim("worker");
        assertThat(deferrer.failureAttempt(resumed)).isEqualTo(1);
        assertThat(deferrer.defer(waiting, Duration.ofSeconds(2), reason)).isFalse();
        assertThat(queue.complete(resumed)).isTrue();
    }

    @Test
    void renewalKeepsTailClaimPastOriginalDeadlineWithoutChangingItsAttempt() {
        queue.submit(request("v1"));
        RuntimeWorkItem tail = queue.claim(type, "owner-a", 1, Duration.ofSeconds(1)).getFirst();
        clock.advance(Duration.ofMillis(500));
        assertThat(deferrer.renew(tail, Duration.ofMinutes(1))).isTrue();
        clock.advance(Duration.ofSeconds(2));
        assertThat(queue.requeueExpired(type, 100)).isZero();
        assertThat(queue.claim(type, "owner-b", 1, Duration.ofMinutes(1))).isEmpty();
        assertThat(deferrer.failureAttempt(tail)).isEqualTo(1);
        assertThat(queue.complete(tail)).isTrue();
        assertThat(deferrer.renew(tail, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void renewalCannotReviveExpiredTokenOrOverwriteNewSubmissionOrAttempt() {
        queue.submit(request("v1"));
        RuntimeWorkItem expired = queue.claim(type, "same-owner", 1, Duration.ofSeconds(1)).getFirst();
        clock.advance(Duration.ofSeconds(2));
        assertThat(deferrer.renew(expired, Duration.ofMinutes(1))).isFalse();
        RuntimeWorkItem replacement = claim("same-owner");
        assertThat(deferrer.renew(expired, Duration.ofMinutes(1))).isFalse();
        assertThat(deferrer.renew(replacement, Duration.ofMinutes(1))).isTrue();
        queue.submit(request("v2"));
        assertThat(deferrer.renew(replacement, Duration.ofMinutes(1))).isFalse();
        RuntimeWorkItem current = claim("same-owner");
        assertThat(current.version()).isEqualTo("v2");
        assertThat(deferrer.renew(current, Duration.ofMinutes(1))).isTrue();
        assertThat(queue.complete(current)).isTrue();
    }

    private RuntimeWorkItem claim(String owner) {
        return queue.claim(type, owner, 1, Duration.ofMinutes(1)).getFirst();
    }

    private RuntimeWorkItemRequest request(String version) {
        return new RuntimeWorkItemRequest(type, taskId, "raw", version, "{}");
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T00:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
