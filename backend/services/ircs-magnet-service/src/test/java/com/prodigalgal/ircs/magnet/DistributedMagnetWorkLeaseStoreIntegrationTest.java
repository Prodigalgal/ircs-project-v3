package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Verifies the lease script against the tracked queue protocol, when a test key-value port is provided. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedMagnetWorkLeaseStoreIntegrationTest {

    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private DistributedRuntimeWorkQueue queue;
    private DistributedMagnetWorkLeaseStore leaseStore;
    private MutableClock clock;
    private String prefix;
    private String taskId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = new LettuceConnectionFactory(
                "127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        clock = new MutableClock(Instant.parse("2026-11-16T00:00:00Z"));
        prefix = "ircs-test:magnet-lease:" + UUID.randomUUID();
        ObjectProvider<Clock> clockProvider = mock(ObjectProvider.class);
        when(clockProvider.getIfAvailable()).thenReturn(clock);
        queue = new DistributedRuntimeWorkQueue(
                redis, null, null, clockProvider, prefix, 1000, 5,
                prefix + ":metrics", Duration.ofSeconds(10), Duration.ofMinutes(30), Duration.ofMinutes(31));
        leaseStore = new DistributedMagnetWorkLeaseStore(redis, clockProvider, prefix, 1000);
        taskId = MagnetWorkTypes.searchTaskId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            var keys = redis.keys(prefix + ":*");
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        if (connection != null) {
            connection.destroy();
        }
    }

    @Test
    void renewsLiveClaimAndNeverRevivesExpiredOrReclaimedOwnership() {
        queue.submit(new RuntimeWorkItemRequest(MagnetWorkTypes.SEARCH, taskId, "aggregate", "v1", "{}"));
        RuntimeWorkItem oldClaim = queue.claim(
                MagnetWorkTypes.SEARCH, "owner-1", 1, Duration.ofSeconds(10)).getFirst();

        assertThat(leaseStore.renew(oldClaim, Duration.ofSeconds(10))).isTrue();
        clock.advance(Duration.ofSeconds(10));
        assertThat(leaseStore.renew(oldClaim, Duration.ofMinutes(5))).isFalse();

        RuntimeWorkItem reclaimed = queue.claim(
                MagnetWorkTypes.SEARCH, "owner-2", 1, Duration.ofSeconds(10)).getFirst();
        assertThat(reclaimed.attempt()).isEqualTo(oldClaim.attempt() + 1);
        assertThat(leaseStore.renew(oldClaim, Duration.ofMinutes(5))).isFalse();
        assertThat(leaseStore.renew(reclaimed, Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void defersBusyClaimWithoutConsumingRetryAndFencesOldSubmissionToken() {
        queue.submit(new RuntimeWorkItemRequest(MagnetWorkTypes.SEARCH, taskId, "aggregate", "v1", "{}"));
        RuntimeWorkItem oldClaim = queue.claim(
                MagnetWorkTypes.SEARCH, "owner-1", 1, Duration.ofSeconds(10)).getFirst();

        assertThat(leaseStore.defer(oldClaim, Duration.ofSeconds(30), "MAGNET_SEARCH_JOB_ALREADY_RUNNING"))
                .isTrue();
        assertThat(leaseStore.renew(oldClaim, Duration.ofMinutes(5))).isFalse();
        assertThat(queue.counts(MagnetWorkTypes.SEARCH).pending()).isEqualTo(1);
        assertThat(queue.counts(MagnetWorkTypes.SEARCH).inflight()).isZero();

        clock.advance(Duration.ofSeconds(30));
        RuntimeWorkItem nextClaim = queue.claim(
                MagnetWorkTypes.SEARCH, "owner-1", 1, Duration.ofSeconds(10)).getFirst();
        assertThat(nextClaim.attempt()).isEqualTo(oldClaim.attempt());
        assertThat(nextClaim.submissionId()).isNotEqualTo(oldClaim.submissionId());
        assertThat(leaseStore.renew(oldClaim, Duration.ofMinutes(5))).isFalse();
        assertThat(leaseStore.renew(nextClaim, Duration.ofSeconds(10))).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
