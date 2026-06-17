package com.prodigalgal.ircs.common.work;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
public class RedisRuntimeWorkQueue implements RuntimeWorkQueue {

    private static final String EMPTY_VERSION = "_";
    private static final Duration DEFAULT_COMPLETED_TTL = Duration.ofDays(7);

    private static final DefaultRedisScript<Long> SUBMIT_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', ARGV[1],
                'taskType', ARGV[2],
                'taskId', ARGV[3],
                'submissionId', ARGV[4],
                'aggregateId', ARGV[5],
                'version', ARGV[6],
                'payload', ARGV[7],
                'status', 'PENDING',
                'attempt', '0',
                'createdAt', ARGV[8],
                'updatedAt', ARGV[8],
                'dueAt', ARGV[9],
                'visibleAt', '',
                'ownerId', '',
                'lastError', '')
            redis.call('PERSIST', ARGV[1])
            redis.call('ZADD', KEYS[1], ARGV[9], ARGV[3])
            redis.call('ZREM', KEYS[2], ARGV[3])
            redis.call('ZREM', KEYS[3], ARGV[3])
            redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[10], '*',
                'event', 'submitted',
                'taskType', ARGV[2],
                'taskId', ARGV[3],
                'submissionId', ARGV[4],
                'aggregateId', ARGV[5],
                'version', ARGV[6])
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
            return ids
            """,
            List.class);

    private static final DefaultRedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('HGET', ARGV[2], 'submissionId') ~= ARGV[3] then
              return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('ZREM', KEYS[3], ARGV[1])
            redis.call('HSET', ARGV[2],
                'status', 'SUCCESS',
                'ownerId', '',
                'updatedAt', ARGV[4],
                'completedAt', ARGV[4])
            redis.call('EXPIRE', ARGV[2], ARGV[5])
            redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[6], '*',
                'event', 'completed',
                'taskType', ARGV[7],
                'taskId', ARGV[1],
                'submissionId', ARGV[3])
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> FAIL_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('HGET', ARGV[2], 'submissionId') ~= ARGV[3] then
              return 0
            end
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            if ARGV[7] == 'true' then
              redis.call('HSET', ARGV[2],
                  'status', 'PENDING',
                  'ownerId', '',
                  'updatedAt', ARGV[4],
                  'dueAt', ARGV[5],
                  'visibleAt', '',
                  'lastError', ARGV[6])
              redis.call('ZADD', KEYS[1], ARGV[5], ARGV[1])
              redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[8], '*',
                  'event', 'retry_scheduled',
                  'taskType', ARGV[9],
                  'taskId', ARGV[1],
                  'submissionId', ARGV[3],
                  'reason', ARGV[6])
            else
              redis.call('HSET', ARGV[2],
                  'status', 'PERMANENT_FAILURE',
                  'ownerId', '',
                  'updatedAt', ARGV[4],
                  'visibleAt', '',
                  'lastError', ARGV[6])
              redis.call('ZADD', KEYS[3], ARGV[4], ARGV[1])
              redis.call('XADD', KEYS[4], 'MAXLEN', '~', ARGV[8], '*',
                  'event', 'failed',
                  'taskType', ARGV[9],
                  'taskId', ARGV[1],
                  'submissionId', ARGV[3],
                  'reason', ARGV[6])
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
                  'dueAt', ARGV[1],
                  'visibleAt', '')
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

    @Autowired
    public RedisRuntimeWorkQueue(
            StringRedisTemplate redisTemplate,
            @Value("${app.runtime-work-queue.key-prefix:ircs:work:v1}") String keyPrefix,
            @Value("${app.runtime-work-queue.event-stream-maxlen:10000}") int eventStreamMaxLen) {
        this(redisTemplate, Clock.systemUTC(), keyPrefix, eventStreamMaxLen);
    }

    RedisRuntimeWorkQueue(
            StringRedisTemplate redisTemplate,
            Clock clock,
            String keyPrefix,
            int eventStreamMaxLen) {
        if (redisTemplate == null) {
            throw new IllegalArgumentException("redisTemplate is required");
        }
        this.redisTemplate = redisTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.keyPrefix = normalizeKeyPrefix(keyPrefix);
        this.eventStreamMaxLen = Math.max(1000, eventStreamMaxLen);
    }

    @Override
    public void submit(RuntimeWorkItemRequest request) {
        submit(request, Duration.ZERO);
    }

    @Override
    public void submit(RuntimeWorkItemRequest request, Duration delay) {
        validate(request);
        long now = nowMillis();
        long dueAt = now + Math.max(0L, delay == null ? 0L : delay.toMillis());
        String taskType = request.taskType().trim();
        String taskId = request.taskId().trim();
        redisTemplate.execute(
                SUBMIT_SCRIPT,
                List.of(pendingKey(taskType), inflightKey(taskType), dlqKey(taskType), eventStreamKey()),
                taskKey(taskType, taskId),
                taskType,
                taskId,
                UUID.randomUUID().toString(),
                valueOrEmpty(request.aggregateId()),
                normalizeVersion(request.version()),
                valueOrEmpty(request.payload()),
                Long.toString(now),
                Long.toString(dueAt),
                Integer.toString(eventStreamMaxLen));
    }

    @Override
    public void submitAfterCommit(RuntimeWorkItemRequest request) {
        submitAfterCommit(request, Duration.ZERO);
    }

    @Override
    public void submitAfterCommit(RuntimeWorkItemRequest request, Duration delay) {
        runAfterCommit(() -> submit(request, delay));
    }

    @Override
    public List<RuntimeWorkItem> claim(
            String taskType,
            String ownerId,
            int limit,
            Duration visibilityTimeout) {
        String normalizedTaskType = normalizeTaskType(taskType);
        if (limit <= 0) {
            return List.of();
        }
        String owner = StringUtils.hasText(ownerId) ? ownerId.trim() : "worker";
        long now = nowMillis();
        long visibleAt = now + normalizePositive(visibilityTimeout, Duration.ofMinutes(10)).toMillis();
        List<String> ids = stringList(redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(pendingKey(normalizedTaskType), inflightKey(normalizedTaskType), eventStreamKey()),
                Long.toString(now),
                Long.toString(visibleAt),
                Integer.toString(limit),
                taskKeyPrefix(normalizedTaskType),
                owner,
                Integer.toString(eventStreamMaxLen),
                normalizedTaskType));
        return readItems(normalizedTaskType, ids);
    }

    @Override
    public boolean complete(RuntimeWorkItem item) {
        if (item == null || !StringUtils.hasText(item.taskId()) || !StringUtils.hasText(item.submissionId())) {
            return false;
        }
        String taskType = normalizeTaskType(item.taskType());
        Long value = redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(pendingKey(taskType), inflightKey(taskType), dlqKey(taskType), eventStreamKey()),
                item.taskId(),
                taskKey(taskType, item.taskId()),
                item.submissionId(),
                Long.toString(nowMillis()),
                Long.toString(DEFAULT_COMPLETED_TTL.toSeconds()),
                Integer.toString(eventStreamMaxLen),
                taskType);
        return Long.valueOf(1L).equals(value);
    }

    @Override
    public boolean fail(RuntimeWorkItem item, boolean retryable, Duration retryDelay, String reason) {
        if (item == null || !StringUtils.hasText(item.taskId()) || !StringUtils.hasText(item.submissionId())) {
            return false;
        }
        String taskType = normalizeTaskType(item.taskType());
        long now = nowMillis();
        long dueAt = now + Math.max(0L, retryDelay == null ? 0L : retryDelay.toMillis());
        Long value = redisTemplate.execute(
                FAIL_SCRIPT,
                List.of(pendingKey(taskType), inflightKey(taskType), dlqKey(taskType), eventStreamKey()),
                item.taskId(),
                taskKey(taskType, item.taskId()),
                item.submissionId(),
                Long.toString(now),
                Long.toString(dueAt),
                safeReason(reason),
                Boolean.toString(retryable),
                Integer.toString(eventStreamMaxLen),
                taskType);
        return Long.valueOf(1L).equals(value);
    }

    @Override
    public int requeueExpired(String taskType, int limit) {
        String normalizedTaskType = normalizeTaskType(taskType);
        if (limit <= 0) {
            return 0;
        }
        List<String> ids = stringList(redisTemplate.execute(
                REQUEUE_EXPIRED_SCRIPT,
                List.of(inflightKey(normalizedTaskType), pendingKey(normalizedTaskType), eventStreamKey()),
                Long.toString(nowMillis()),
                Integer.toString(limit),
                taskKeyPrefix(normalizedTaskType),
                Integer.toString(eventStreamMaxLen),
                normalizedTaskType));
        return ids.size();
    }

    @Override
    public RuntimeWorkQueueCounts counts(String taskType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        return new RuntimeWorkQueueCounts(
                zCard(pendingKey(normalizedTaskType)),
                zCard(inflightKey(normalizedTaskType)),
                zCard(dlqKey(normalizedTaskType)));
    }

    private List<RuntimeWorkItem> readItems(String taskType, List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RuntimeWorkItem> items = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(taskKey(taskType, id));
            if (!fields.isEmpty()) {
                items.add(toItem(taskType, id, fields));
            }
        }
        return items;
    }

    private RuntimeWorkItem toItem(String taskType, String taskId, Map<Object, Object> fields) {
        return new RuntimeWorkItem(
                taskType,
                taskId,
                value(fields, "submissionId"),
                value(fields, "aggregateId"),
                value(fields, "version"),
                value(fields, "payload"),
                value(fields, "status"),
                intValue(fields, "attempt"),
                instant(fields, "createdAt"),
                instant(fields, "updatedAt"),
                instant(fields, "dueAt"),
                instant(fields, "visibleAt"),
                value(fields, "ownerId"),
                value(fields, "lastError"));
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

    private static void validate(RuntimeWorkItemRequest request) {
        if (request == null
                || !StringUtils.hasText(request.taskType())
                || !StringUtils.hasText(request.taskId())) {
            throw new IllegalArgumentException("taskType and taskId are required");
        }
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

    private String eventStreamKey() {
        return keyPrefix + ":events";
    }

    private long zCard(String key) {
        Long value = redisTemplate.opsForZSet().zCard(key);
        return value == null ? 0L : value;
    }

    private long nowMillis() {
        return Instant.now(clock).toEpochMilli();
    }

    private static String normalizeTaskType(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("taskType is required");
        }
        return taskType.trim();
    }

    private static String normalizeKeyPrefix(String keyPrefix) {
        if (!StringUtils.hasText(keyPrefix)) {
            return "ircs:work:v1";
        }
        String normalized = keyPrefix.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeVersion(String version) {
        return StringUtils.hasText(version) ? version.trim() : EMPTY_VERSION;
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

    private static String valueOrEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static String value(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : value.toString();
    }

    private static int intValue(Map<Object, Object> fields, String key) {
        String value = value(fields, key);
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Instant instant(Map<Object, Object> fields, String key) {
        String value = value(fields, key);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
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
}
