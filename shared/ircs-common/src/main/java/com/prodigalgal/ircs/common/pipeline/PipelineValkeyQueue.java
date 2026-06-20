package com.prodigalgal.ircs.common.pipeline;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import com.prodigalgal.ircs.common.work.WorkSubmissionGate;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import com.prodigalgal.ircs.contracts.pipeline.PipelineStep;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class PipelineValkeyQueue {

    private static final String EMPTY_VERSION = "_";
    private static final Duration DEFAULT_COMPLETED_TTL = Duration.ofDays(7);

    private static final DefaultRedisScript<Long> SUBMIT_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', ARGV[1],
                'taskType', ARGV[2],
                'taskId', ARGV[3],
                'rawVideoId', ARGV[4],
                'pipelineVersion', ARGV[5],
                'payload', ARGV[6],
                'status', 'PENDING',
                'attempt', '0',
                'createdAt', ARGV[7],
                'updatedAt', ARGV[7],
                'dueAt', ARGV[8],
                'ownerId', '')
            redis.call('PERSIST', ARGV[1])
            redis.call('ZADD', KEYS[1], ARGV[8], ARGV[3])
            redis.call('ZREM', KEYS[2], ARGV[3])
            redis.call('ZREM', KEYS[3], ARGV[3])
            redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[9], '*',
                'event', 'submitted',
                'taskType', ARGV[2],
                'taskId', ARGV[3],
                'rawVideoId', ARGV[4],
                'pipelineVersion', ARGV[5])
            redis.call('HINCRBY', KEYS[5], ARGV[13], 1)
            redis.call('EXPIRE', KEYS[5], ARGV[11])
            redis.call('ZADD', KEYS[6], ARGV[10], ARGV[10])
            redis.call('ZREMRANGEBYSCORE', KEYS[6], '-inf', tonumber(ARGV[10]) - tonumber(ARGV[12]))
            redis.call('EXPIRE', KEYS[6], math.ceil(tonumber(ARGV[12]) / 1000))
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<List> CLAIM_SCRIPT = new DefaultRedisScript<>(
            """
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[3])
            for _, id in ipairs(ids) do
              local taskKey = ARGV[4] .. id
              redis.call('ZREM', KEYS[1], id)
              redis.call('ZADD', KEYS[2], ARGV[2], id)
              redis.call('HINCRBY', taskKey, 'attempt', 1)
              redis.call('HSET', taskKey,
                  'status', 'PROCESSING',
                  'ownerId', ARGV[5],
                  'updatedAt', ARGV[1],
                  'visibleAt', ARGV[2])
              redis.call('XADD', KEYS[3], 'MAXLEN', '~', ARGV[6], '*',
                  'event', 'claimed',
                  'taskType', ARGV[7],
                  'taskId', id,
                  'ownerId', ARGV[5])
            end
            if #ids > 0 then
              redis.call('HINCRBY', KEYS[4], ARGV[11], #ids)
              redis.call('EXPIRE', KEYS[4], ARGV[9])
              redis.call('ZADD', KEYS[5], ARGV[8], ARGV[8])
              redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', tonumber(ARGV[8]) - tonumber(ARGV[10]))
              redis.call('EXPIRE', KEYS[5], math.ceil(tonumber(ARGV[10]) / 1000))
            end
            return ids
            """,
            List.class);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('ZREM', KEYS[3], ARGV[1])
            redis.call('HSET', ARGV[2],
                'status', 'SUCCESS',
                'ownerId', '',
                'updatedAt', ARGV[3],
                'completedAt', ARGV[3])
            redis.call('EXPIRE', ARGV[2], ARGV[4])
            redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[5], '*',
                'event', 'completed',
                'taskType', ARGV[6],
                'taskId', ARGV[1])
            redis.call('HINCRBY', KEYS[5], ARGV[10], 1)
            redis.call('EXPIRE', KEYS[5], ARGV[8])
            redis.call('ZADD', KEYS[6], ARGV[7], ARGV[7])
            redis.call('ZREMRANGEBYSCORE', KEYS[6], '-inf', tonumber(ARGV[7]) - tonumber(ARGV[9]))
            redis.call('EXPIRE', KEYS[6], math.ceil(tonumber(ARGV[9]) / 1000))
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> FAIL_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            if ARGV[6] == 'true' then
              redis.call('HSET', ARGV[2],
                  'status', 'PENDING',
                  'ownerId', '',
                  'updatedAt', ARGV[3],
                  'dueAt', ARGV[4],
                  'lastError', ARGV[5])
              redis.call('ZADD', KEYS[1], ARGV[4], ARGV[1])
              redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[7], '*',
                  'event', 'retry_scheduled',
                  'taskType', ARGV[8],
                  'taskId', ARGV[1],
                  'reason', ARGV[5])
            else
              redis.call('HSET', ARGV[2],
                  'status', 'PERMANENT_FAILURE',
                  'ownerId', '',
                  'updatedAt', ARGV[3],
                  'lastError', ARGV[5])
              redis.call('ZADD', KEYS[3], ARGV[3], ARGV[1])
              redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[7], '*',
                  'event', 'failed',
                  'taskType', ARGV[8],
                  'taskId', ARGV[1],
                  'reason', ARGV[5])
              redis.call('HINCRBY', KEYS[5], ARGV[12], 1)
              redis.call('EXPIRE', KEYS[5], ARGV[10])
              redis.call('ZADD', KEYS[6], ARGV[9], ARGV[9])
              redis.call('ZREMRANGEBYSCORE', KEYS[6], '-inf', tonumber(ARGV[9]) - tonumber(ARGV[11]))
              redis.call('EXPIRE', KEYS[6], math.ceil(tonumber(ARGV[11]) / 1000))
            end
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<List> REQUEUE_EXPIRED_SCRIPT = new DefaultRedisScript<>(
            """
            local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            for _, id in ipairs(ids) do
              local taskKey = ARGV[3] .. id
              redis.call('ZREM', KEYS[1], id)
              redis.call('ZADD', KEYS[2], ARGV[1], id)
              redis.call('HSET', taskKey,
                  'status', 'PENDING',
                  'ownerId', '',
                  'updatedAt', ARGV[1],
                  'dueAt', ARGV[1])
              redis.call('XADD', KEYS[3], 'MAXLEN', '~', ARGV[4], '*',
                  'event', 'visibility_expired',
                  'taskType', ARGV[5],
                  'taskId', id)
            end
            return ids
            """,
            List.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;
    private final String keyPrefix;
    private final int eventStreamMaxLen;
    private final String rateMetricsKeyPrefix;
    private final Duration rateBucketSize;
    private final Duration rateBucketTtl;
    private final Duration rateBucketIndexRetention;
    private final RuntimeConfigService runtimeConfig;
    private final ObjectProvider<WorkSubmissionGate> submissionGateProvider;

    @Autowired
    public PipelineValkeyQueue(
            StringRedisTemplate redisTemplate,
            ObjectProvider<WorkSubmissionGate> submissionGateProvider,
            ObjectProvider<RuntimeConfigService> runtimeConfigProvider,
            @Value("${app.pipeline.valkey.key-prefix:ircs:pipeline:v2}") String keyPrefix,
            @Value("${app.pipeline.valkey.event-stream-maxlen:10000}") int eventStreamMaxLen,
            @Value("${app.rate-metrics.key-prefix:ircs:metrics:rate}") String rateMetricsKeyPrefix,
            @Value("${app.rate-metrics.bucket-size:PT10S}") Duration rateBucketSize,
            @Value("${app.rate-metrics.bucket-ttl:PT30M}") Duration rateBucketTtl,
            @Value("${app.rate-metrics.bucket-index-retention:PT31M}") Duration rateBucketIndexRetention) {
        this(redisTemplate, Clock.systemUTC(), keyPrefix, eventStreamMaxLen,
                rateMetricsKeyPrefix, rateBucketSize, rateBucketTtl, rateBucketIndexRetention,
                submissionGateProvider, runtimeConfigProvider == null ? null : runtimeConfigProvider.getIfAvailable());
    }

    PipelineValkeyQueue(
            StringRedisTemplate redisTemplate,
            Clock clock,
            String keyPrefix,
            int eventStreamMaxLen,
            String rateMetricsKeyPrefix,
            Duration rateBucketSize,
            Duration rateBucketTtl,
            Duration rateBucketIndexRetention) {
        this(redisTemplate, clock, keyPrefix, eventStreamMaxLen,
                rateMetricsKeyPrefix, rateBucketSize, rateBucketTtl, rateBucketIndexRetention, null, null);
    }

    PipelineValkeyQueue(
            StringRedisTemplate redisTemplate,
            Clock clock,
            String keyPrefix,
            int eventStreamMaxLen,
            String rateMetricsKeyPrefix,
            Duration rateBucketSize,
            Duration rateBucketTtl,
            Duration rateBucketIndexRetention,
            ObjectProvider<WorkSubmissionGate> submissionGateProvider) {
        this(redisTemplate, clock, keyPrefix, eventStreamMaxLen,
                rateMetricsKeyPrefix, rateBucketSize, rateBucketTtl, rateBucketIndexRetention,
                submissionGateProvider, null);
    }

    PipelineValkeyQueue(
            StringRedisTemplate redisTemplate,
            Clock clock,
            String keyPrefix,
            int eventStreamMaxLen,
            String rateMetricsKeyPrefix,
            Duration rateBucketSize,
            Duration rateBucketTtl,
            Duration rateBucketIndexRetention,
            ObjectProvider<WorkSubmissionGate> submissionGateProvider,
            RuntimeConfigService runtimeConfig) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate is required");
        }
        this.redisTemplate = redisTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.keyPrefix = normalizeKeyPrefix(keyPrefix);
        this.eventStreamMaxLen = Math.max(1000, eventStreamMaxLen);
        this.rateMetricsKeyPrefix = RateMetricKeys.normalizeKeyPrefix(rateMetricsKeyPrefix);
        this.rateBucketSize = rateBucketSize == null ? RateMetricKeys.DEFAULT_BUCKET_SIZE : rateBucketSize;
        this.rateBucketTtl = rateBucketTtl == null ? RateMetricKeys.DEFAULT_BUCKET_TTL : rateBucketTtl;
        this.rateBucketIndexRetention = rateBucketIndexRetention == null
                ? RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION
                : rateBucketIndexRetention;
        this.runtimeConfig = runtimeConfig;
        this.submissionGateProvider = submissionGateProvider;
    }

    public void submit(PipelineCommand command) {
        submit(command, Duration.ZERO);
    }

    public void submit(PipelineCommand command, Duration delay) {
        if (command == null) {
            throw new IllegalArgumentException("pipeline command is required");
        }
        submit(
                taskType(command.step()),
                commandTaskId(command),
                command.rawVideoId(),
                command.pipelineVersion(),
                "",
                delay);
    }

    public void submitAfterCommit(PipelineCommand command) {
        submitAfterCommit(command, Duration.ZERO);
    }

    public void submitAfterCommit(PipelineCommand command, Duration delay) {
        runAfterCommit(() -> submit(command, delay));
    }

    public void submit(
            String taskType,
            String taskId,
            UUID rawVideoId,
            String pipelineVersion,
            String payload,
            Duration delay) {
        validateTask(taskType, taskId, rawVideoId);
        String normalizedTaskType = taskType.trim();
        if (!canSubmit(normalizedTaskType)) {
            return;
        }
        long now = nowMillis();
        long dueAt = now + Math.max(0L, delay == null ? 0L : delay.toMillis());
        RateBucket bucket = rateBucket(now);
        redisTemplate.execute(
                SUBMIT_SCRIPT,
                List.of(pendingKey(normalizedTaskType), inflightKey(normalizedTaskType), dlqKey(normalizedTaskType), eventStreamKey(),
                        bucket.bucketKey(), bucket.bucketIndexKey()),
                taskKey(normalizedTaskType, taskId),
                normalizedTaskType,
                taskId,
                rawVideoId.toString(),
                normalizeVersion(pipelineVersion),
                StringUtils.hasText(payload) ? payload : "",
                Long.toString(now),
                Long.toString(dueAt),
                Integer.toString(eventStreamMaxLen),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.pipelineMetric(normalizedTaskType, RateMetricKeys.ACTION_SUBMITTED));
    }

    public void submitAfterCommit(
            String taskType,
            String taskId,
            UUID rawVideoId,
            String pipelineVersion,
            String payload,
            Duration delay) {
        runAfterCommit(() -> submit(taskType, taskId, rawVideoId, pipelineVersion, payload, delay));
    }

    public List<PipelineValkeyTask> claim(
            String taskType,
            String ownerId,
            int limit,
            Duration visibilityTimeout) {
        validateTaskType(taskType);
        if (limit <= 0) {
            return List.of();
        }
        String owner = StringUtils.hasText(ownerId) ? ownerId.trim() : "worker";
        long now = nowMillis();
        long visibleAt = now + normalizePositive(visibilityTimeout, Duration.ofMinutes(10)).toMillis();
        RateBucket bucket = rateBucket(now);
        List<String> ids = stringList(redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(pendingKey(taskType), inflightKey(taskType), eventStreamKey(),
                        bucket.bucketKey(), bucket.bucketIndexKey()),
                Long.toString(now),
                Long.toString(visibleAt),
                Integer.toString(limit),
                taskKeyPrefix(taskType),
                owner,
                Integer.toString(eventStreamMaxLen),
                taskType,
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.pipelineMetric(taskType, RateMetricKeys.ACTION_CLAIMED)));
        return readTasks(taskType, ids);
    }

    public void complete(PipelineValkeyTask task) {
        if (task == null) {
            return;
        }
        long now = nowMillis();
        RateBucket bucket = rateBucket(now);
        redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(pendingKey(task.taskType()), inflightKey(task.taskType()), dlqKey(task.taskType()), eventStreamKey(),
                        bucket.bucketKey(), bucket.bucketIndexKey()),
                task.taskId(),
                taskKey(task.taskType(), task.taskId()),
                Long.toString(now),
                Long.toString(DEFAULT_COMPLETED_TTL.toSeconds()),
                Integer.toString(eventStreamMaxLen),
                task.taskType(),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.pipelineMetric(task.taskType(), RateMetricKeys.ACTION_COMPLETED));
    }

    public void fail(PipelineValkeyTask task, boolean retryable, Duration retryDelay, String reason) {
        if (task == null) {
            return;
        }
        long now = nowMillis();
        long dueAt = now + Math.max(0L, retryDelay == null ? 0L : retryDelay.toMillis());
        RateBucket bucket = rateBucket(now);
        redisTemplate.execute(
                FAIL_SCRIPT,
                List.of(pendingKey(task.taskType()), inflightKey(task.taskType()), dlqKey(task.taskType()), eventStreamKey(),
                        bucket.bucketKey(), bucket.bucketIndexKey()),
                task.taskId(),
                taskKey(task.taskType(), task.taskId()),
                Long.toString(now),
                Long.toString(dueAt),
                safeReason(reason),
                Boolean.toString(retryable),
                Integer.toString(eventStreamMaxLen),
                task.taskType(),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.pipelineMetric(task.taskType(), RateMetricKeys.ACTION_FAILED));
    }

    public int requeueExpired(String taskType, int limit) {
        validateTaskType(taskType);
        if (limit <= 0) {
            return 0;
        }
        List<String> ids = stringList(redisTemplate.execute(
                REQUEUE_EXPIRED_SCRIPT,
                List.of(inflightKey(taskType), pendingKey(taskType), eventStreamKey()),
                Long.toString(nowMillis()),
                Integer.toString(limit),
                taskKeyPrefix(taskType),
                Integer.toString(eventStreamMaxLen),
                taskType));
        return ids.size();
    }

    public void heartbeatConsumer(String taskType, String ownerId, Duration ttl) {
        validateTaskType(taskType);
        if (!StringUtils.hasText(ownerId)) {
            return;
        }
        Duration effectiveTtl = normalizePositive(ttl, Duration.ofMinutes(2));
        String key = consumerKey(taskType);
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, nowMillis());
        redisTemplate.opsForZSet().add(key, ownerId.trim(), nowMillis() + effectiveTtl.toMillis());
        redisTemplate.expire(key, effectiveTtl.plusMinutes(5));
    }

    public long consumerCount(String taskType) {
        validateTaskType(taskType);
        String key = consumerKey(taskType);
        redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, nowMillis());
        return zCard(key);
    }

    public PipelineValkeyTaskCounts counts(String taskType) {
        validateTaskType(taskType);
        return new PipelineValkeyTaskCounts(
                zCard(pendingKey(taskType)),
                zCard(inflightKey(taskType)),
                zCard(dlqKey(taskType)));
    }

    public static String commandTaskId(PipelineCommand command) {
        return taskType(command.step()) + ":" + command.rawVideoId() + ":" + normalizeVersion(command.pipelineVersion());
    }

    public static String taskType(PipelineStep step) {
        return switch (step) {
            case NORMALIZE_VIDEO -> PipelineTaskTypes.NORMALIZE_VIDEO;
            case ENRICH_METADATA -> PipelineTaskTypes.ENRICH_METADATA;
        };
    }

    public static String providerTaskId(String providerType, UUID rawVideoId, String pipelineVersion) {
        if (!StringUtils.hasText(providerType) || rawVideoId == null) {
            throw new IllegalArgumentException("providerType and rawVideoId are required");
        }
        return PipelineTaskTypes.METADATA_PROVIDER + ":" + providerType.trim() + ":" + rawVideoId + ":" + normalizeVersion(pipelineVersion);
    }

    private List<PipelineValkeyTask> readTasks(String taskType, List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<PipelineValkeyTask> tasks = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(taskKey(taskType, id));
            if (!fields.isEmpty()) {
                tasks.add(PipelineValkeyTask.fromHash(taskType, id, fields));
            }
        }
        return tasks;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .map(item -> item == null ? null : item.toString())
                .filter(StringUtils::hasText)
                .toList();
    }

    private static void validateTask(String taskType, String taskId, UUID rawVideoId) {
        validateTaskType(taskType);
        if (!StringUtils.hasText(taskId) || rawVideoId == null) {
            throw new IllegalArgumentException("taskId and rawVideoId are required");
        }
    }

    private static void validateTaskType(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("taskType is required");
        }
    }

    private boolean canSubmit(String taskType) {
        WorkSubmissionGate gate = submissionGateProvider == null ? null : submissionGateProvider.getIfAvailable();
        return gate == null || gate.canSubmitPipeline(taskType);
    }

    private long nowMillis() {
        return Instant.now(clock).toEpochMilli();
    }

    private RateBucket rateBucket(long eventMillis) {
        Duration bucketSize = rateBucketSize();
        String keyPrefix = rateMetricsKeyPrefix();
        long bucketStart = RateMetricKeys.bucketStartMillis(eventMillis, bucketSize);
        return new RateBucket(
                RateMetricKeys.bucketKey(keyPrefix, bucketStart),
                RateMetricKeys.bucketIndexKey(keyPrefix),
                Long.toString(bucketStart),
                Long.toString(RateMetricKeys.ttlSeconds(rateBucketTtl(), RateMetricKeys.DEFAULT_BUCKET_TTL)),
                Long.toString(RateMetricKeys.retentionMillis(
                        rateBucketIndexRetention(),
                        RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION)));
    }

    private String rateMetricsKeyPrefix() {
        return runtimeConfig == null
                ? rateMetricsKeyPrefix
                : runtimeConfig.stringValue("app.rate-metrics.key-prefix", rateMetricsKeyPrefix);
    }

    private Duration rateBucketSize() {
        return runtimeConfig == null
                ? rateBucketSize
                : runtimeConfig.positiveDurationValue("app.rate-metrics.bucket-size", rateBucketSize);
    }

    private Duration rateBucketTtl() {
        return runtimeConfig == null
                ? rateBucketTtl
                : runtimeConfig.positiveDurationValue("app.rate-metrics.bucket-ttl", rateBucketTtl);
    }

    private Duration rateBucketIndexRetention() {
        return runtimeConfig == null
                ? rateBucketIndexRetention
                : runtimeConfig.positiveDurationValue(
                        "app.rate-metrics.bucket-index-retention",
                        rateBucketIndexRetention);
    }

    private String pendingKey(String taskType) {
        return keyPrefix + ":" + taskType + ":pending";
    }

    private String inflightKey(String taskType) {
        return keyPrefix + ":" + taskType + ":inflight";
    }

    private String dlqKey(String taskType) {
        return keyPrefix + ":" + taskType + ":dlq";
    }

    private String taskKey(String taskType, String taskId) {
        return taskKeyPrefix(taskType) + taskId;
    }

    private String taskKeyPrefix(String taskType) {
        return keyPrefix + ":" + taskType + ":task:";
    }

    private String consumerKey(String taskType) {
        return keyPrefix + ":" + taskType + ":consumers";
    }

    private String eventStreamKey() {
        return keyPrefix + ":events";
    }

    private long zCard(String key) {
        Long value = redisTemplate.opsForZSet().zCard(key);
        return value == null ? 0L : value;
    }

    private static String normalizeKeyPrefix(String keyPrefix) {
        if (!StringUtils.hasText(keyPrefix)) {
            return "ircs:pipeline:v2";
        }
        String normalized = keyPrefix.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeVersion(String pipelineVersion) {
        return StringUtils.hasText(pipelineVersion) ? pipelineVersion.trim() : EMPTY_VERSION;
    }

    private static Duration normalizePositive(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private static String safeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private record RateBucket(
            String bucketKey,
            String bucketIndexKey,
            String bucketStartMillis,
            String bucketTtlSeconds,
            String bucketIndexRetentionMillis) {
    }
}
