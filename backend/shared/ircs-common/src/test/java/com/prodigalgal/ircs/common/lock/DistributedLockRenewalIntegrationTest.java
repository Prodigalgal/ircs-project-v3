package com.prodigalgal.ircs.common.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.common.lease.DistributedLeaseService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedLockRenewalIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private DistributedLeaseService service;
    private String prefix;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        prefix = "ircs-test:renewal:" + UUID.randomUUID() + ":";
        service = new DistributedLeaseService(redis, Clock.systemUTC(), prefix);
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        redis.delete(prefix + "maintenance");
        connectionFactory.destroy();
    }

    @Test
    void anotherReplicaCannotAcquireAfterOriginalTtlWhileActionStillRuns() {
        Duration ttl = Duration.ofMillis(800);
        assertThat(service.runWithLock("maintenance", "first", ttl, () -> {
            sleep(1800);
            assertThat(service.tryAcquire("maintenance", "second", ttl)).isEmpty();
        })).isTrue();
        assertThat(service.tryAcquire("maintenance", "second", ttl)).isPresent();
    }

    @Test
    void swallowedInterruptAfterTokenLossCannotBecomeSuccessOrDeleteNewOwner() {
        AtomicBoolean interrupted = new AtomicBoolean();
        assertThatThrownBy(() -> service.runWithLock("maintenance", "first", Duration.ofMillis(800), () -> {
            redis.opsForValue().set(prefix + "maintenance", "replacement-owner", Duration.ofSeconds(10));
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                interrupted.set(true);
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("ownership lost");
        assertThat(interrupted).isTrue();
        assertThat(redis.opsForValue().get(prefix + "maintenance")).isEqualTo("replacement-owner");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted test action", ex);
        }
    }
}
