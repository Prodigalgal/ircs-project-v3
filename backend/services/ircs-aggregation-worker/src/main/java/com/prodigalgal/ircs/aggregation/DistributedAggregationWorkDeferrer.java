package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Local adapter to the tracked DistributedRuntimeWorkQueue storage protocol; see module docs. */
@Component
final class DistributedAggregationWorkDeferrer implements AggregationWorkDeferrer {
    private static final DefaultRedisScript<Long> DEFER = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'taskType') ~= ARGV[1]
                or redis.call('HGET', KEYS[1], 'taskId') ~= ARGV[2]
                or redis.call('HGET', KEYS[1], 'submissionId') ~= ARGV[3]
                or redis.call('HGET', KEYS[1], 'status') ~= 'PROCESSING'
                or redis.call('HGET', KEYS[1], 'ownerId') ~= ARGV[4]
                or redis.call('HGET', KEYS[1], 'attempt') ~= ARGV[5] then return 0 end
            local deadline = tonumber(redis.call('HGET', KEYS[1], 'visibleAt') or '0')
            local inflight = tonumber(redis.call('ZSCORE', KEYS[3], ARGV[2]) or '0')
            if deadline <= tonumber(ARGV[6]) or inflight <= tonumber(ARGV[6]) then return 0 end
            local cycle = tonumber(redis.call('HGET', KEYS[1], 'attemptCycleStart') or '0')
            if cycle >= tonumber(ARGV[5]) then return 0 end
            redis.call('HSET', KEYS[1], 'status', 'PENDING', 'ownerId', '',
                'visibleAt', '', 'updatedAt', ARGV[6], 'dueAt', ARGV[7], 'lastError', ARGV[8],
                'attemptCycleStart', tostring(cycle + 1))
            redis.call('HDEL', KEYS[1], 'completedAt')
            redis.call('PERSIST', KEYS[1])
            redis.call('ZREM', KEYS[3], ARGV[2])
            redis.call('ZREM', KEYS[4], ARGV[2])
            redis.call('ZADD', KEYS[2], ARGV[7], ARGV[2])
            redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[9], '*',
                'event', 'assessment_wait_scheduled', 'taskType', ARGV[1], 'taskId', ARGV[2],
                'submissionId', ARGV[3], 'reason', ARGV[8], 'dueAt', ARGV[7])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String prefix;
    private final int streamMaxLen;

    DistributedAggregationWorkDeferrer(StringRedisTemplate redis, ObjectProvider<Clock> clockProvider,
            @Value("${app.runtime-work-queue.key-prefix:ircs:work:v1}") String prefix,
            @Value("${app.runtime-work-queue.event-stream-maxlen:10000}") int streamMaxLen) {
        this.redis = redis;
        Clock supplied = clockProvider == null ? null : clockProvider.getIfAvailable();
        this.clock = supplied == null ? Clock.systemUTC() : supplied;
        String normalized = StringUtils.hasText(prefix) ? prefix.trim() : "ircs:work:v1";
        while (normalized.endsWith(":")) { normalized = normalized.substring(0, normalized.length() - 1); }
        this.prefix = normalized;
        this.streamMaxLen = Math.max(1000, streamMaxLen);
    }

    @Override
    public boolean defer(RuntimeWorkItem item, Duration delay, String reason) {
        if (!valid(item) || !AdultAssessmentDeferredException.isWaitingReason(reason)) { return false; }
        long seconds = delay == null ? 2 : Math.max(2, Math.min(604800, delay.getSeconds()));
        long now = clock.millis();
        String typePrefix = prefix + ":" + AggregationWorkTypes.RAW_VIDEO;
        Long result = redis.execute(DEFER,
                List.of(taskKey(item), typePrefix + ":pending", typePrefix + ":inflight",
                        typePrefix + ":dlq", prefix + ":events"),
                item.taskType(), item.taskId(), item.submissionId(), item.ownerId(), Integer.toString(item.attempt()),
                Long.toString(now), Long.toString(now + seconds * 1000), reason, Integer.toString(streamMaxLen));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public int failureAttempt(RuntimeWorkItem item) {
        if (!valid(item)) { return Math.max(1, item == null ? 1 : item.attempt()); }
        Object cycle = redis.opsForHash().get(taskKey(item), "attemptCycleStart");
        long start = cycle == null ? 0 : Long.parseLong(cycle.toString());
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (long) item.attempt() - start));
    }

    private boolean valid(RuntimeWorkItem item) {
        return item != null && AggregationWorkTypes.RAW_VIDEO.equals(item.taskType())
                && StringUtils.hasText(item.taskId()) && item.taskId().startsWith("raw_video:")
                && StringUtils.hasText(item.submissionId()) && StringUtils.hasText(item.ownerId())
                && item.attempt() > 0;
    }

    private String taskKey(RuntimeWorkItem item) {
        return prefix + ":" + AggregationWorkTypes.RAW_VIDEO + ":task:" + item.taskId();
    }
}
