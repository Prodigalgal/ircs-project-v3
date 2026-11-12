package com.prodigalgal.ircs.notification.mail;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Component
@Slf4j
class MailSendRateLimiter {

    private static final long SECOND_WINDOW_MS = 1_000L;
    private static final long MINUTE_WINDOW_MS = 60_000L;

    private final NotificationMailConfigValues configValues;
    private final MailCredentialUsageRepository usageRepository;
    private final Clock clock;
    private final Sleeper sleeper;
    private static final String GLOBAL_KEY = "traffic:limit:mail:global";
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>(
            """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local gap = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local window = tonumber(ARGV[3])
            local wait = math.max(0, tonumber(redis.call('GET', KEYS[1]) or '0') - now)
            if limit > 0 then
              redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - window)
              if redis.call('ZCARD', KEYS[2]) >= limit then
                local oldest = redis.call('ZRANGE', KEYS[2], 0, 0, 'WITHSCORES')
                wait = math.max(wait, tonumber(oldest[2]) + window - now)
              end
            end
            if wait > 0 then return wait end
            if gap > 0 then redis.call('SET', KEYS[1], now + gap, 'PX', gap + 60000) end
            if limit > 0 then
              redis.call('ZADD', KEYS[2], now, ARGV[4])
              redis.call('PEXPIRE', KEYS[2], window + 60000)
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    MailSendRateLimiter(
            NotificationMailConfigValues configValues,
            MailCredentialUsageRepository usageRepository,
            ObjectProvider<Clock> clockProvider,
            ObjectProvider<Sleeper> sleeperProvider,
            StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.configValues = configValues;
        this.usageRepository = usageRepository;
        Clock providedClock = clockProvider == null ? null : clockProvider.getIfAvailable();
        Sleeper providedSleeper = sleeperProvider == null ? null : sleeperProvider.getIfAvailable();
        this.clock = providedClock == null ? Clock.systemUTC() : providedClock;
        this.sleeper = providedSleeper == null ? Thread::sleep : providedSleeper;
    }

    void awaitGlobalPermit() {
        long started = System.nanoTime();
        while (true) {
            long waitMs = reservePermit(null);
            if (waitMs == 0L) {
                return;
            }
            sleepWithinConfiguredBound(waitMs, started);
        }
    }

    MailCredential selectCredential(List<MailCredential> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new MailCredentialLeaseException("MAIL credential is not available");
        }
        long started = System.nanoTime();
        while (true) {
            long shortestWait = Long.MAX_VALUE;
            for (MailCredential credential : candidates) {
                if (isDailyQuotaExhausted(credential) || isMonthlyQuotaExhausted(credential)) {
                    continue;
                }
                long waitMs = reservePermit(credential);
                if (waitMs == 0L) {
                    return credential;
                }
                shortestWait = Math.min(shortestWait, waitMs);
            }
            if (shortestWait == Long.MAX_VALUE) {
                throw new MailCredentialLeaseException("MAIL credential quota exhausted");
            }
            sleepWithinConfiguredBound(shortestWait, started);
        }
    }

    private long reservePermit(MailCredential credential) {
        int limit = credential == null || credential.id() == null ? 0 : positiveInt(credential.rateLimit());
        long gap = nextGlobalDelayMs();
        if (limit == 0 && gap == 0) {
            return 0L;
        }
        String credentialKey = limit == 0 ? GLOBAL_KEY : "traffic:limit:mail:credential:" + credential.id();
        Long wait = redisTemplate.execute(RESERVE, List.of(GLOBAL_KEY, credentialKey),
                String.valueOf(gap), String.valueOf(limit),
                String.valueOf(credential == null ? MINUTE_WINDOW_MS : rateLimitWindowMs(credential.rateLimitUnit())),
                java.util.UUID.randomUUID().toString());
        if (wait == null || wait < 0L) {
            throw new MailCredentialLeaseException("MAIL rate limiter returned an invalid response");
        }
        return wait;
    }

    private boolean isDailyQuotaExhausted(MailCredential credential) {
        long limit = positiveLong(credential.dayLimit());
        if (limit <= 0) {
            return false;
        }
        Instant dayStart = LocalDate.now(clock)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return usageRepository.countSentSince(credential.id(), dayStart) >= limit;
    }

    private boolean isMonthlyQuotaExhausted(MailCredential credential) {
        long limit = positiveLong(credential.monthLimit());
        if (limit <= 0) {
            return false;
        }
        Instant monthStart = YearMonth.now(clock)
                .atDay(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        return usageRepository.countSentSince(credential.id(), monthStart) >= limit;
    }

    private long nextGlobalDelayMs() {
        int min = configValues.rateLimitMinMs();
        int max = configValues.rateLimitMaxMs();
        if (max <= 0) {
            return 0L;
        }
        if (max == min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void sleepWithinConfiguredBound(long waitMs, long started) {
        if (waitMs <= 0) {
            return;
        }
        int maxWaitMs = configValues.rateLimitMaxWaitMs();
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - started));
        if (maxWaitMs > 0 && waitMs > Math.max(0L, maxWaitMs - elapsedMs)) {
            throw new MailCredentialLeaseException("MAIL credential rate limit wait exceeds configured max wait");
        }
        try {
            sleeper.sleep(waitMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MailCredentialLeaseException("MAIL rate limit wait interrupted", ex);
        }
    }

    private static long rateLimitWindowMs(String unit) {
        if (unit == null) {
            return MINUTE_WINDOW_MS;
        }
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "SECOND" -> SECOND_WINDOW_MS;
            case "MINUTE" -> MINUTE_WINDOW_MS;
            default -> MINUTE_WINDOW_MS;
        };
    }

    private static int positiveInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private static long positiveLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

}
