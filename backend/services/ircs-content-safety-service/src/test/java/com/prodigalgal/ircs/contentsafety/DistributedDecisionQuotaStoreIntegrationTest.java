package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Uses only the disposable Actions fixture; never points at a production namespace. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedDecisionQuotaStoreIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private DistributedDecisionQuotaStore first;
    private DistributedDecisionQuotaStore second;
    private String prefix;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        factory.afterPropertiesSet(); factory.start(); redis = new StringRedisTemplate(factory);
        prefix = "ircs-test:{decision-"+UUID.randomUUID()+"}:";
        first = new DistributedDecisionQuotaStore(redis, Clock.systemUTC(), prefix);
        second = new DistributedDecisionQuotaStore(redis, Clock.systemUTC(), prefix);
    }
    @AfterEach
    void tearDown() {
        var keys = redis.keys(prefix+"*"); if (keys != null && !keys.isEmpty()) { redis.delete(keys); }
        factory.destroy();
    }

    @Test
    void concurrentReplicasCannotOversubscribeRpmOrPartiallyConsumeDailyBudget() throws Exception {
        var accepted = concurrently(260, 1);
        assertThat(accepted).hasSize(200);
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isEqualTo(200L);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("200");
    }

    @Test
    void tpmAndDailyLimitsAreJointlyAtomic() throws Exception {
        var accepted = concurrently(40, 3000);
        assertThat(accepted).hasSize(16);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("16");
        // No minute reservation is added when a different dimension rejects the call.
        redis.opsForValue().set(dayKey(), "10000");
        assertThatThrownBy(() -> second.reserve(1)).hasMessage("DAILY_LIMIT");
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isEqualTo(16L);
    }

    @Test
    void reconciliationIsReplacementAndIdempotentWhileCancellationIsPreSendOnly() {
        var sent = first.reserve(1000);
        first.beginSend(sent);
        first.reconcile(sent, 100); second.reconcile(sent, 200);
        assertThat(redis.opsForHash().get(prefix+"amounts", sent.id())).isEqualTo("100");
        second.cancelBeforeSend(sent); // cannot refund a reconciled/attempted request
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("1");
        var cancelled = second.reserve(1000);
        first.cancelBeforeSend(cancelled); first.cancelBeforeSend(cancelled);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("1");
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isEqualTo(1L);
    }

    @Test
    void sharedCooldownCannotBeShortenedAndBlocksOtherReplicas() {
        first.coolDown(Instant.now().plusSeconds(120));
        second.coolDown(Instant.now().plusSeconds(1));
        assertThatThrownBy(() -> second.reserve(1)).hasMessage("COOLDOWN");
        assertThat(redis.opsForValue().get(prefix+"cooldown")).isNotNull();
        assertThat(redis.opsForValue().get(dayKey())).isNull();
    }

    @Test
    void newPoolCooldownFencesAlreadyReservedButNotYetSentWork() {
        var reserved = first.reserve(1000);
        second.coolDown(Instant.now().plusSeconds(120));
        assertThatThrownBy(() -> first.beginSend(reserved)).hasMessage("COOLDOWN");
        first.cancelBeforeSend(reserved);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("0");
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isZero();
    }

    @Test
    void staleReservationCannotBeSentOutsideItsRollingBudgetWindow() {
        var stale = first.reserve(1000);
        redis.opsForZSet().add(prefix+"minute", stale.id(), Instant.now().minusSeconds(61).toEpochMilli());
        assertThatThrownBy(() -> first.beginSend(stale)).hasMessage("RATE_LIMITED");
        first.cancelBeforeSend(stale);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("0");
    }

    @Test
    void tokenWaitReflectsEarliestSufficientExpiryInsteadOfAlwaysSleepingOneMinute() {
        var old = first.reserve(49000);
        redis.opsForZSet().add(prefix+"minute", old.id(), Instant.now().minusSeconds(55).toEpochMilli());
        try { second.reserve(2000); throw new AssertionError("Expected token limit"); }
        catch (DecisionUnavailableException ex) {
            assertThat(ex.reason()).isEqualTo("TOKEN_LIMITED");
            assertThat(ex.retryAfterSeconds()).isBetween(1L, 6L);
        }
    }

    @Test
    void staleCalendarClockFailsClosedInsteadOfSpendingPreviousDayBudget() {
        var stale = new DistributedDecisionQuotaStore(redis, Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), java.time.ZoneOffset.UTC), prefix);
        assertThatThrownBy(() -> stale.reserve(1)).hasMessage("QUOTA_UNAVAILABLE");
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isZero();
    }

    @Test
    void expiredRollingWindowReleasesMinuteCapacityWithoutRefundingDailyAttempts() {
        var old = first.reserve(49000);
        redis.opsForZSet().add(prefix+"minute", old.id(), Instant.now().minusSeconds(61).toEpochMilli());
        second.reserve(49000);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("2");
        assertThat(redis.opsForZSet().zCard(prefix+"minute")).isEqualTo(1L);
    }

    @Test
    void ambiguousRequestsKeepBothEstimatedInputAndDailyAttemptUntilWindowExpires() {
        var unknown = first.reserve(49000); // no success, reconciliation or cancellation was observed
        first.beginSend(unknown);
        second.cancelBeforeSend(unknown); // attempted/unknown consumption cannot be refunded
        assertThatThrownBy(() -> second.reserve(1001)).hasMessage("TOKEN_LIMITED");
        assertThat(redis.opsForHash().get(prefix+"amounts", unknown.id())).isEqualTo("49000");
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("1");
        redis.opsForZSet().add(prefix+"minute", unknown.id(), Instant.now().minusSeconds(61).toEpochMilli());
        second.reserve(49000);
        assertThat(redis.opsForValue().get(dayKey())).isEqualTo("2");
        assertThat(redis.opsForHash().hasKey(prefix+"amounts", unknown.id())).isFalse();
    }

    private List<DecisionQuotaStore.Reservation> concurrently(int count, long estimate) throws Exception {
        List<Future<DecisionQuotaStore.Reservation>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(16)) {
            for (int i=0; i<count; i++) {
                DistributedDecisionQuotaStore store = i%2==0 ? first : second;
                futures.add(executor.submit(() -> {
                    try { return store.reserve(estimate); }
                    catch (DecisionUnavailableException ex) {
                        if (!"RATE_LIMITED".equals(ex.reason()) && !"TOKEN_LIMITED".equals(ex.reason())) { throw ex; }
                        return null;
                    }
                }));
            }
            List<DecisionQuotaStore.Reservation> result = new ArrayList<>();
            for (var future : futures) { var value = future.get(); if (value != null) { result.add(value); } }
            return result;
        }
    }
    private String dayKey() { return prefix+"day:"+LocalDate.now(DistributedDecisionQuotaStore.PACIFIC); }
}
