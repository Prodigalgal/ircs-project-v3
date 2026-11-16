package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Redis-backed implementation of the magnet work lease contract. */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
final class DistributedMagnetWorkLeaseStore implements MagnetWorkLeaseStore {

    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'taskType') ~= ARGV[1]
                or redis.call('HGET', KEYS[1], 'taskId') ~= ARGV[2]
                or redis.call('HGET', KEYS[1], 'submissionId') ~= ARGV[3]
                or redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
                or redis.call('HGET', KEYS[1], 'ownerId') ~= ARGV[4]
                or redis.call('HGET', KEYS[1], 'attempt') ~= ARGV[5] then return 0 end
            local now = tonumber(ARGV[6])
            local visibleAt = tonumber(redis.call('HGET', KEYS[1], 'visibleAt') or '0')
            local inflightAt = tonumber(redis.call('ZSCORE', KEYS[2], ARGV[2]) or '0')
            if visibleAt <= now or inflightAt <= now then return 0 end
            local renewed = math.max(visibleAt, tonumber(ARGV[7]))
            redis.call('HSET', KEYS[1], 'visibleAt', tostring(renewed))
            redis.call('ZADD', KEYS[2], renewed, ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DEFER = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'taskType') ~= ARGV[1]
                or redis.call('HGET', KEYS[1], 'taskId') ~= ARGV[2]
                or redis.call('HGET', KEYS[1], 'submissionId') ~= ARGV[3]
                or redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
                or redis.call('HGET', KEYS[1], 'ownerId') ~= ARGV[4]
                or redis.call('HGET', KEYS[1], 'attempt') ~= ARGV[5] then return 0 end
            local now = tonumber(ARGV[6])
            local visibleAt = tonumber(redis.call('HGET', KEYS[1], 'visibleAt') or '0')
            local inflightAt = tonumber(redis.call('ZSCORE', KEYS[2], ARGV[2]) or '0')
            if visibleAt <= now or inflightAt <= now then return 0 end
            local attempt = tonumber(redis.call('HGET', KEYS[1], 'attempt') or '0')
            local cycleStart = tonumber(redis.call('HGET', KEYS[1], 'attemptCycleStart') or '0')
            local deferredAttempt = math.max(cycleStart, attempt - 1)
            local dueAt = tonumber(ARGV[7])
            redis.call('HSET', KEYS[1], 'status', 'PENDING', 'ownerId', '',
                'visibleAt', '', 'updatedAt', ARGV[6], 'dueAt', ARGV[7],
                'attempt', tostring(deferredAttempt), 'lastError', ARGV[8], 'submissionId', ARGV[10])
            redis.call('HDEL', KEYS[1], 'completedAt')
            redis.call('PERSIST', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[2])
            redis.call('ZREM', KEYS[3], ARGV[2])
            redis.call('ZADD', KEYS[4], dueAt, ARGV[2])
            redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[9], '*',
                'event', 'magnet_wait_scheduled', 'taskType', ARGV[1], 'taskId', ARGV[2],
                'submissionId', ARGV[3], 'nextSubmissionId', ARGV[10], 'reason', ARGV[8], 'dueAt', ARGV[7])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String keyPrefix;
    private final int eventStreamMaxLen;

    DistributedMagnetWorkLeaseStore(
            StringRedisTemplate redis,
            ObjectProvider<Clock> clockProvider,
            @Value("${app.runtime-work-queue.key-prefix:ircs:work:v1}") String keyPrefix,
            @Value("${app.runtime-work-queue.event-stream-maxlen:10000}") int eventStreamMaxLen) {
        this.redis = redis;
        Clock supplied = clockProvider == null ? null : clockProvider.getIfAvailable();
        this.clock = supplied == null ? Clock.systemUTC() : supplied;
        String normalized = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "ircs:work:v1";
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.keyPrefix = normalized;
        this.eventStreamMaxLen = Math.max(1000, eventStreamMaxLen);
    }

    @Override
    public boolean renew(RuntimeWorkItem item, Duration visibility) {
        if (!valid(item)) {
            return false;
        }
        long now = clock.millis();
        long visibilityMillis = visibility == null ? 600_000 : Math.max(1_000, Math.min(604_800_000, visibility.toMillis()));
        String taskKey = keyPrefix + ":" + MagnetWorkTypes.SEARCH + ":task:" + item.taskId();
        String inflightKey = keyPrefix + ":" + MagnetWorkTypes.SEARCH + ":inflight";
        Long result = redis.execute(
                RENEW,
                List.of(taskKey, inflightKey),
                item.taskType(),
                item.taskId(),
                item.submissionId(),
                item.ownerId(),
                Integer.toString(item.attempt()),
                Long.toString(now),
                Long.toString(now + visibilityMillis));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public boolean defer(RuntimeWorkItem item, Duration delay, String reason) {
        if (!valid(item) || !StringUtils.hasText(reason)) {
            return false;
        }
        long now = clock.millis();
        long delaySeconds = delay == null ? 30 : Math.max(2, Math.min(604_800, delay.getSeconds()));
        long dueAt = now + delaySeconds * 1000;
        String taskKey = keyPrefix + ":" + MagnetWorkTypes.SEARCH + ":task:" + item.taskId();
        String typePrefix = keyPrefix + ":" + MagnetWorkTypes.SEARCH;
        Long result = redis.execute(
                DEFER,
                List.of(taskKey, typePrefix + ":inflight", typePrefix + ":dlq",
                        typePrefix + ":pending", keyPrefix + ":events"),
                item.taskType(),
                item.taskId(),
                item.submissionId(),
                item.ownerId(),
                Integer.toString(item.attempt()),
                Long.toString(now),
                Long.toString(dueAt),
                reason,
                Integer.toString(eventStreamMaxLen),
                UUID.randomUUID().toString());
        return Long.valueOf(1).equals(result);
    }

    private boolean valid(RuntimeWorkItem item) {
        return item != null
                && MagnetWorkTypes.SEARCH.equals(item.taskType())
                && StringUtils.hasText(item.taskId())
                && item.taskId().startsWith(MagnetWorkTypes.SEARCH + ":")
                && StringUtils.hasText(item.submissionId())
                && StringUtils.hasText(item.ownerId())
                && item.attempt() > 0;
    }
}
