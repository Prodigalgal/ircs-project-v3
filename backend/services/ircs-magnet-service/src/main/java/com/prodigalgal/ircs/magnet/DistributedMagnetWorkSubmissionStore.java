package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.WorkSubmissionGate;
import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Publishes once without resetting the shared queue's ownership, retry budget or terminal state. */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
final class DistributedMagnetWorkSubmissionStore implements MagnetWorkSubmissionStore {
    private static final String DEFAULT_PREFIX = "ircs:work:v1";
    private static final DefaultRedisScript<Long> SUBMIT_IF_ABSENT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) ~= 0 then return 0 end
            if redis.call('ZSCORE', KEYS[3], ARGV[2]) or redis.call('ZSCORE', KEYS[4], ARGV[2]) then return 0 end
            local previousDue = redis.call('ZSCORE', KEYS[2], ARGV[2])
            local dueAt = previousDue or ARGV[7]
            redis.call('HSET', KEYS[1],
                'taskType', ARGV[1], 'taskId', ARGV[2], 'submissionId', ARGV[3],
                'aggregateId', ARGV[4], 'version', ARGV[5], 'payload', ARGV[6],
                'status', 'PENDING', 'attempt', '0', 'attemptCycleStart', '0', 'dlqReplayCount', '0',
                'createdAt', ARGV[7], 'updatedAt', ARGV[7], 'dueAt', dueAt,
                'visibleAt', '', 'ownerId', '', 'lastError', '')
            redis.call('ZADD', KEYS[2], dueAt, ARGV[2])
            redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[8], '*',
                'event', 'submitted', 'taskType', ARGV[1], 'taskId', ARGV[2],
                'submissionId', ARGV[3], 'aggregateId', ARGV[4], 'version', ARGV[5])
            redis.call('HINCRBY', KEYS[6], ARGV[12], 1)
            redis.call('EXPIRE', KEYS[6], ARGV[10])
            redis.call('ZADD', KEYS[7], ARGV[9], ARGV[9])
            redis.call('ZREMRANGEBYSCORE', KEYS[7], '-inf', tonumber(ARGV[9]) - tonumber(ARGV[11]))
            redis.call('EXPIRE', KEYS[7], math.ceil(tonumber(ARGV[11]) / 1000))
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectProvider<WorkSubmissionGate> submissionGateProvider;
    private final Clock clock;
    private final String keyPrefix;
    private final int eventStreamMaxLen;
    private final RuntimeConfigService runtimeConfig;
    private final String ratePrefix;
    private final Duration bucketSize;
    private final Duration bucketTtl;
    private final Duration bucketRetention;

    DistributedMagnetWorkSubmissionStore(StringRedisTemplate redis,
            ObjectProvider<WorkSubmissionGate> submissionGateProvider, ObjectProvider<Clock> clockProvider,
            @Value("${app.runtime-work-queue.key-prefix:ircs:work:v1}") String keyPrefix,
            @Value("${app.runtime-work-queue.event-stream-maxlen:10000}") int eventStreamMaxLen,
            ObjectProvider<RuntimeConfigService> runtimeConfig,
            @Value("${app.rate-metrics.key-prefix:ircs:metrics:rate}") String ratePrefix,
            @Value("${app.rate-metrics.bucket-size:PT10S}") Duration bucketSize,
            @Value("${app.rate-metrics.bucket-ttl:PT30M}") Duration bucketTtl,
            @Value("${app.rate-metrics.bucket-index-retention:PT31M}") Duration bucketRetention) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.submissionGateProvider = submissionGateProvider;
        Clock supplied = clockProvider == null ? null : clockProvider.getIfAvailable();
        this.clock = supplied == null ? Clock.systemUTC() : supplied;
        String normalized = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : DEFAULT_PREFIX;
        while (normalized.endsWith(":")) normalized = normalized.substring(0, normalized.length() - 1);
        this.keyPrefix = normalized.isBlank() ? DEFAULT_PREFIX : normalized;
        this.eventStreamMaxLen = Math.max(1000, eventStreamMaxLen);
        this.runtimeConfig = runtimeConfig == null ? null : runtimeConfig.getIfAvailable();
        this.ratePrefix = RateMetricKeys.normalizeKeyPrefix(ratePrefix);
        this.bucketSize = bucketSize;
        this.bucketTtl = bucketTtl;
        this.bucketRetention = bucketRetention;
    }

    @Override
    public boolean submitIfAbsent(RuntimeWorkItemRequest request) {
        if (request == null || !MagnetWorkTypes.SEARCH.equals(text(request.taskType()))) {
            throw new IllegalArgumentException("Only magnet.search submissions are supported");
        }
        String taskId = validateTaskId(request.taskId());
        if (!StringUtils.hasText(request.payload())) throw new IllegalArgumentException("payload is required");
        WorkSubmissionGate gate = submissionGateProvider == null ? null : submissionGateProvider.getIfAvailable();
        if (gate != null && !gate.canSubmitRuntime(MagnetWorkTypes.SEARCH)) return false;
        String typePrefix = keyPrefix + ":" + MagnetWorkTypes.SEARCH;
        String metricsPrefix = runtimeConfig == null ? ratePrefix : runtimeConfig.stringValue("app.rate-metrics.key-prefix", ratePrefix);
        long now = clock.millis();
        long bucketStart = RateMetricKeys.bucketStartMillis(now, duration("bucket-size", bucketSize));
        long ttl = RateMetricKeys.ttlSeconds(duration("bucket-ttl", bucketTtl), RateMetricKeys.DEFAULT_BUCKET_TTL);
        long retention = RateMetricKeys.retentionMillis(duration("bucket-index-retention", bucketRetention), RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION);
        Long submitted = redis.execute(SUBMIT_IF_ABSENT,
                List.of(taskKey(taskId), typePrefix + ":pending", typePrefix + ":inflight",
                        typePrefix + ":dlq", keyPrefix + ":events", RateMetricKeys.bucketKey(metricsPrefix, bucketStart),
                        RateMetricKeys.bucketIndexKey(metricsPrefix)),
                MagnetWorkTypes.SEARCH, taskId, UUID.randomUUID().toString(), text(request.aggregateId()),
                StringUtils.hasText(request.version()) ? request.version().trim() : "_", request.payload(),
                Long.toString(now), Integer.toString(eventStreamMaxLen), Long.toString(bucketStart), Long.toString(ttl),
                Long.toString(retention), RateMetricKeys.runtimeMetric(MagnetWorkTypes.SEARCH, RateMetricKeys.ACTION_SUBMITTED));
        return Long.valueOf(1L).equals(submitted);
    }

    @Override
    public String status(String taskId) {
        Object value = redis.opsForHash().get(taskKey(validateTaskId(taskId)), "status");
        return value == null ? null : value.toString();
    }

    private String taskKey(String taskId) { return keyPrefix + ":" + MagnetWorkTypes.SEARCH + ":task:" + taskId; }
    private Duration duration(String key, Duration fallback) {
        return runtimeConfig == null ? fallback : runtimeConfig.positiveDurationValue("app.rate-metrics." + key, fallback);
    }
    private static String text(String raw) { return raw == null ? "" : raw.trim(); }

    private static String validateTaskId(String raw) {
        String taskId = text(raw);
        String prefix = MagnetWorkTypes.SEARCH + ":";
        if (!taskId.startsWith(prefix)) throw new IllegalArgumentException("Invalid magnet search taskId");
        String suffix = taskId.substring(prefix.length());
        try {
            if (!UUID.fromString(suffix).toString().equals(suffix)) throw new IllegalArgumentException("Invalid magnet search taskId UUID");
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid magnet search taskId UUID", invalid);
        }
        return taskId;
    }
}
