package com.prodigalgal.ircs.notification.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MailSendRateLimiterTest {

    @Test
    void selectsNextCredentialWhenFirstCredentialExhaustsDailyQuota() {
        NotificationMailConfigValues configValues = configValues(0, 0, 0);
        FakeUsageRepository usageRepository = new FakeUsageRepository();
        MutableClock clock = new MutableClock();
        MailCredential limited = credential("11111111-1111-4111-8111-111111111111", 0, null, 1L, 0L);
        MailCredential available = credential("22222222-2222-4222-8222-222222222222", 0, null, 0L, 0L);
        usageRepository.counts = Map.of(limited.id(), 1L);
        MailSendRateLimiter limiter = new MailSendRateLimiter(
                configValues,
                usageRepository,
                provider(clock),
                provider((MailSendRateLimiter.Sleeper) clock::advance),
                redisLimiter(clock));

        MailCredential selected = limiter.selectCredential(List.of(limited, available));

        assertEquals(available.id(), selected.id());
        assertEquals(0L, clock.sleptMs.get());
    }

    @Test
    void waitsForSingleCredentialRateLimitWindowWhenEveryCandidateIsLimited() {
        NotificationMailConfigValues configValues = configValues(0, 0, 0);
        MutableClock clock = new MutableClock();
        MailCredential credential = credential("11111111-1111-4111-8111-111111111111", 1, "SECOND", 0L, 0L);
        MailSendRateLimiter limiter = new MailSendRateLimiter(
                configValues,
                new FakeUsageRepository(),
                provider(clock),
                provider((MailSendRateLimiter.Sleeper) clock::advance),
                redisLimiter(clock));

        assertEquals(credential.id(), limiter.selectCredential(List.of(credential)).id());
        assertEquals(credential.id(), limiter.selectCredential(List.of(credential)).id());

        assertEquals(1000L, clock.sleptMs.get());
    }

    @Test
    void globalDelayAppliesWhenNoCredentialPoolIsUsed() {
        NotificationMailConfigValues configValues = configValues(500, 500, 0);
        MutableClock clock = new MutableClock();
        MailSendRateLimiter limiter = new MailSendRateLimiter(
                configValues,
                new FakeUsageRepository(),
                provider(clock),
                provider((MailSendRateLimiter.Sleeper) clock::advance),
                redisLimiter(clock));

        limiter.awaitGlobalPermit();
        limiter.awaitGlobalPermit();

        assertEquals(500L, clock.sleptMs.get());
    }

    @Test
    void springConstructorUsesSinglePublicInjectionPath() throws NoSuchMethodException {
        assertEquals(
                1,
                MailSendRateLimiter.class.getDeclaredConstructors().length);
        assertEquals(
                MailSendRateLimiter.class,
                MailSendRateLimiter.class
                        .getDeclaredConstructor(
                                NotificationMailConfigValues.class,
                                MailCredentialUsageRepository.class,
                                ObjectProvider.class,
                                ObjectProvider.class,
                                org.springframework.data.redis.core.StringRedisTemplate.class)
                        .getDeclaringClass());
    }

    @Test
    void missingDistributedPermitResponseFailsClosed() {
        MailSendRateLimiter limiter = new MailSendRateLimiter(configValues(500, 500, 1000),
                new FakeUsageRepository(), null, null,
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(limiter::awaitGlobalPermit)
                .isInstanceOf(MailCredentialLeaseException.class).hasMessageContaining("invalid response");
    }

    private static org.springframework.data.redis.core.StringRedisTemplate redisLimiter(MutableClock clock) {
        org.springframework.data.redis.core.StringRedisTemplate redis =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        java.util.Map<String, Long> nextAllowed = new java.util.HashMap<>();
        when(redis.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    java.util.List<String> keys = invocation.getArgument(1);
                    long gap = Long.parseLong(invocation.getArgument(2));
                    int limit = Integer.parseInt(invocation.getArgument(3));
                    long window = Long.parseLong(invocation.getArgument(4));
                    long now = clock.millis();
                    long waitMs = Math.max(0L, nextAllowed.getOrDefault(keys.get(0), 0L) - now);
                    if (limit > 0) {
                        waitMs = Math.max(waitMs, nextAllowed.getOrDefault(keys.get(1), 0L) - now);
                    }
                    if (waitMs == 0L) {
                        nextAllowed.put(keys.get(0), now + gap);
                        if (limit > 0) {
                            nextAllowed.put(keys.get(1), now + window);
                        }
                    }
                    return waitMs;
                });
        return redis;
    }

    private static NotificationMailConfigValues configValues(int minMs, int maxMs, int maxWaitMs) {
        NotificationMailConfigValues values = org.mockito.Mockito.mock(NotificationMailConfigValues.class);
        when(values.rateLimitMinMs()).thenReturn(minMs);
        when(values.rateLimitMaxMs()).thenReturn(maxMs);
        when(values.rateLimitMaxWaitMs()).thenReturn(maxWaitMs);
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static MailCredential credential(
            String id,
            Integer rateLimit,
            String rateLimitUnit,
            Long dayLimit,
            Long monthLimit) {
        return new MailCredential(
                UUID.fromString(id),
                id + "@example.invalid",
                "secret",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                rateLimit,
                rateLimitUnit,
                dayLimit,
                monthLimit);
    }

    private static final class FakeUsageRepository extends MailCredentialUsageRepository {

        private Map<UUID, Long> counts = Map.of();

        private FakeUsageRepository() {
            super("", null, null);
        }

        @Override
        long countSentSince(UUID credentialId, Instant since) {
            return counts.getOrDefault(credentialId, 0L);
        }
    }

    private static final class MutableClock extends Clock {

        private final AtomicLong millis = new AtomicLong();
        private final AtomicLong sleptMs = new AtomicLong();

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
            return Instant.ofEpochMilli(millis());
        }

        @Override
        public long millis() {
            return millis.get();
        }

        private void advance(long deltaMs) {
            sleptMs.addAndGet(deltaMs);
            millis.addAndGet(deltaMs);
        }
    }
}
