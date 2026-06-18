package com.prodigalgal.ircs.task;

import com.prodigalgal.ircs.common.metrics.RateMetricKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.prodigalgal.ircs.contracts.task.TaskDetailDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskMasterDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageDiscoveredMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageFailedMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
class TaskProgressRedisService {

    private static final DefaultRedisScript<Long> PAGE_DISCOVERED_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('SADD', KEYS[1], ARGV[1])
            local previousStatus = redis.call('HGET', KEYS[2], 'status') or ''
            local previousMasterStatus = redis.call('HGET', KEYS[3], 'status') or ''
            local firstDiscovery = 0
            if previousStatus == '' or previousStatus == 'QUEUED' then
              firstDiscovery = 1
            end
            local detailScheduled = tonumber(ARGV[3]) or 0
            local pageStatus = 'DISCOVERED'
            if detailScheduled <= 0 then
              pageStatus = 'COMPLETED'
            end
            redis.call('HSET', KEYS[2],
              'masterTaskId', ARGV[7],
              'pageTaskId', ARGV[1],
              'pageNumber', ARGV[2],
              'detailScheduled', ARGV[3],
              'detailCompleted', '0',
              'detailSucceeded', '0',
              'detailFailed', '0',
              'status', pageStatus,
              'updatedAt', ARGV[6])
            if firstDiscovery == 1 then
              redis.call('HINCRBY', KEYS[3], 'detailScheduled', ARGV[3])
              redis.call('HINCRBY', KEYS[3], 'pageDiscovered', 1)
              if detailScheduled <= 0 then
                redis.call('HINCRBY', KEYS[3], 'pageCompleted', 1)
                redis.call('HINCRBY', KEYS[3], 'pageSucceeded', 1)
              end
            end
            local pageScheduled = tonumber(redis.call('HGET', KEYS[3], 'pageScheduled') or '0')
            local pageCompleted = tonumber(redis.call('HGET', KEYS[3], 'pageCompleted') or '0')
            local pageFailed = tonumber(redis.call('HGET', KEYS[3], 'pageFailed') or '0')
            local masterDetailScheduled = tonumber(redis.call('HGET', KEYS[3], 'detailScheduled') or '0')
            local detailCompleted = tonumber(redis.call('HGET', KEYS[3], 'detailCompleted') or '0')
            local detailFailed = tonumber(redis.call('HGET', KEYS[3], 'detailFailed') or '0')
            local masterStatus = previousMasterStatus
            if masterStatus ~= 'PAUSED'
              and masterStatus ~= 'STOPPING'
              and masterStatus ~= 'FAILED'
              and masterStatus ~= 'COMPLETED'
              and masterStatus ~= 'COMPLETED_WITH_ERRORS' then
              masterStatus = 'RUNNING'
              if pageScheduled > 0 and pageCompleted >= pageScheduled and detailCompleted >= masterDetailScheduled then
                if pageFailed > 0 or detailFailed > 0 then
                  masterStatus = 'FAILED'
                else
                  masterStatus = 'COMPLETED'
                end
              end
            end
            redis.call('HSET', KEYS[3],
              'masterTaskId', ARGV[7],
              'status', masterStatus,
              'updatedAt', ARGV[6])
            if ARGV[4] ~= '' then
              redis.call('HSET', KEYS[3], 'totalPages', ARGV[4])
            end
            if ARGV[5] ~= '' then
              redis.call('HSET', KEYS[3], 'totalItems', ARGV[5])
            end
            redis.call('ZADD', KEYS[4], ARGV[6], ARGV[7])
            if firstDiscovery == 1 then
              redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[8], '*',
                'event', 'page_discovered',
                'masterTaskId', ARGV[7],
                'pageTaskId', ARGV[1],
                'pageNumber', ARGV[2],
                'detailScheduled', ARGV[3])
              redis.call('HINCRBY', KEYS[6], ARGV[12], 1)
              if detailScheduled <= 0 then
                redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[8], '*',
                  'event', 'page_completed',
                  'masterTaskId', ARGV[7],
                  'pageTaskId', ARGV[1],
                  'pageNumber', ARGV[2],
                  'status', pageStatus)
                redis.call('HINCRBY', KEYS[6], ARGV[13], 1)
              end
              redis.call('EXPIRE', KEYS[6], ARGV[10])
              redis.call('ZADD', KEYS[7], ARGV[9], ARGV[9])
              redis.call('ZREMRANGEBYSCORE', KEYS[7], '-inf', tonumber(ARGV[9]) - tonumber(ARGV[11]))
              redis.call('EXPIRE', KEYS[7], math.ceil(tonumber(ARGV[11]) / 1000))
            end
            return firstDiscovery
            """,
            Long.class);

    private static final DefaultRedisScript<Long> PAGE_FAILED_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('SADD', KEYS[1], ARGV[1])
            local previousStatus = redis.call('HGET', KEYS[2], 'status') or ''
            local previousMasterStatus = redis.call('HGET', KEYS[3], 'status') or ''
            local firstFailure = 0
            if previousStatus ~= 'FAILED'
              and previousStatus ~= 'COMPLETED'
              and previousStatus ~= 'COMPLETED_WITH_ERRORS' then
              firstFailure = 1
            end
            redis.call('HSET', KEYS[2],
              'masterTaskId', ARGV[5],
              'pageTaskId', ARGV[1],
              'pageNumber', ARGV[2],
              'detailScheduled', '0',
              'detailCompleted', '0',
              'detailSucceeded', '0',
              'detailFailed', '0',
              'status', 'FAILED',
              'lastError', ARGV[3],
              'updatedAt', ARGV[4])
            if firstFailure == 1 then
              if previousStatus == '' or previousStatus == 'QUEUED' then
                redis.call('HINCRBY', KEYS[3], 'pageDiscovered', 1)
              end
              redis.call('HINCRBY', KEYS[3], 'pageCompleted', 1)
              redis.call('HINCRBY', KEYS[3], 'pageFailed', 1)
            end
            local pageScheduled = tonumber(redis.call('HGET', KEYS[3], 'pageScheduled') or '0')
            local pageCompleted = tonumber(redis.call('HGET', KEYS[3], 'pageCompleted') or '0')
            local masterDetailScheduled = tonumber(redis.call('HGET', KEYS[3], 'detailScheduled') or '0')
            local masterDetailCompleted = tonumber(redis.call('HGET', KEYS[3], 'detailCompleted') or '0')
            local masterStatus = previousMasterStatus
            if masterStatus ~= 'PAUSED'
              and masterStatus ~= 'STOPPING'
              and masterStatus ~= 'FAILED'
              and masterStatus ~= 'COMPLETED'
              and masterStatus ~= 'COMPLETED_WITH_ERRORS' then
              masterStatus = 'RUNNING'
              if pageScheduled > 0 and pageCompleted >= pageScheduled and masterDetailCompleted >= masterDetailScheduled then
                masterStatus = 'FAILED'
              end
            end
            redis.call('HSET', KEYS[3],
              'masterTaskId', ARGV[5],
              'status', masterStatus,
              'lastError', ARGV[3],
              'updatedAt', ARGV[4])
            redis.call('ZADD', KEYS[4], ARGV[4], ARGV[5])
            if firstFailure == 1 then
              redis.call('XADD', KEYS[5], 'MAXLEN', '~', ARGV[6], '*',
                'event', 'page_failed',
                'masterTaskId', ARGV[5],
                'pageTaskId', ARGV[1],
                'pageNumber', ARGV[2],
                'reason', ARGV[3])
              redis.call('HINCRBY', KEYS[6], ARGV[10], 1)
              redis.call('EXPIRE', KEYS[6], ARGV[8])
              redis.call('ZADD', KEYS[7], ARGV[7], ARGV[7])
              redis.call('ZREMRANGEBYSCORE', KEYS[7], '-inf', tonumber(ARGV[7]) - tonumber(ARGV[9]))
              redis.call('EXPIRE', KEYS[7], math.ceil(tonumber(ARGV[9]) / 1000))
            end
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> DETAIL_COMPLETED_SCRIPT = new DefaultRedisScript<>(
            """
            local added = redis.call('SADD', KEYS[1], ARGV[1])
            if added == 0 then
              return 0
            end
            local completed = redis.call('HINCRBY', KEYS[2], 'detailCompleted', 1)
            redis.call('HINCRBY', KEYS[3], 'detailCompleted', 1)
            local failed = tonumber(redis.call('HGET', KEYS[2], 'detailFailed') or '0')
            local previousStatus = redis.call('HGET', KEYS[2], 'status') or ''
            if ARGV[2] == 'true' then
              redis.call('HINCRBY', KEYS[2], 'detailSucceeded', 1)
              redis.call('HINCRBY', KEYS[3], 'detailSucceeded', 1)
            else
              failed = redis.call('HINCRBY', KEYS[2], 'detailFailed', 1)
              redis.call('HINCRBY', KEYS[3], 'detailFailed', 1)
              redis.call('SADD', KEYS[5], ARGV[1])
              local errorSummary = ARGV[5]
              if ARGV[7] ~= '' then
                errorSummary = ARGV[7] .. ' :: ' .. errorSummary
              end
              redis.call('HSET', KEYS[6], ARGV[1], errorSummary)
              redis.call('HSET', KEYS[2], 'lastError', ARGV[5])
              redis.call('HSET', KEYS[3], 'lastError', ARGV[5])
            end
            local scheduled = tonumber(redis.call('HGET', KEYS[2], 'detailScheduled') or '0')
            local pageStatus = 'RUNNING'
            if scheduled > 0 and completed >= scheduled then
              if failed > 0 then
                pageStatus = 'COMPLETED_WITH_ERRORS'
              else
                pageStatus = 'COMPLETED'
              end
              if previousStatus ~= 'COMPLETED' and previousStatus ~= 'COMPLETED_WITH_ERRORS' then
                redis.call('HINCRBY', KEYS[3], 'pageCompleted', 1)
                if failed <= 0 then
                  redis.call('HINCRBY', KEYS[3], 'pageSucceeded', 1)
                end
              end
            end
            local pageScheduled = tonumber(redis.call('HGET', KEYS[3], 'pageScheduled') or '0')
            local pageCompleted = tonumber(redis.call('HGET', KEYS[3], 'pageCompleted') or '0')
            local pageFailed = tonumber(redis.call('HGET', KEYS[3], 'pageFailed') or '0')
            local masterDetailScheduled = tonumber(redis.call('HGET', KEYS[3], 'detailScheduled') or '0')
            local masterDetailCompleted = tonumber(redis.call('HGET', KEYS[3], 'detailCompleted') or '0')
            local masterDetailFailed = tonumber(redis.call('HGET', KEYS[3], 'detailFailed') or '0')
            local previousMasterStatus = redis.call('HGET', KEYS[3], 'status') or ''
            local masterStatus = previousMasterStatus
            if masterStatus ~= 'PAUSED'
              and masterStatus ~= 'STOPPING'
              and masterStatus ~= 'FAILED'
              and masterStatus ~= 'COMPLETED'
              and masterStatus ~= 'COMPLETED_WITH_ERRORS' then
              masterStatus = 'RUNNING'
              if pageScheduled > 0
                and pageCompleted >= pageScheduled
                and masterDetailCompleted >= masterDetailScheduled then
                if pageFailed > 0 or masterDetailFailed > 0 then
                  masterStatus = 'FAILED'
                else
                  masterStatus = 'COMPLETED'
                end
              end
            end
            redis.call('HSET', KEYS[2],
              'masterTaskId', ARGV[4],
              'pageTaskId', ARGV[6],
              'status', pageStatus,
              'updatedAt', ARGV[3])
            redis.call('HSET', KEYS[3],
              'masterTaskId', ARGV[4],
              'status', masterStatus,
              'updatedAt', ARGV[3])
            redis.call('ZADD', KEYS[4], ARGV[3], ARGV[4])
            redis.call('XADD', KEYS[7], 'MAXLEN', '~', ARGV[8], '*',
              'event', 'detail_completed',
              'masterTaskId', ARGV[4],
              'pageTaskId', ARGV[6],
              'detailTaskId', ARGV[1],
              'sourceVid', ARGV[7],
              'successful', ARGV[2])
            redis.call('HINCRBY', KEYS[8], ARGV[12], 1)
            if ARGV[2] == 'true' then
              redis.call('HINCRBY', KEYS[8], ARGV[13], 1)
            else
              redis.call('HINCRBY', KEYS[8], ARGV[14], 1)
            end
            if scheduled > 0
              and completed >= scheduled
              and previousStatus ~= 'COMPLETED'
              and previousStatus ~= 'COMPLETED_WITH_ERRORS' then
              redis.call('XADD', KEYS[7], 'MAXLEN', '~', ARGV[8], '*',
                'event', 'page_completed',
                'masterTaskId', ARGV[4],
                'pageTaskId', ARGV[6],
                'status', pageStatus)
              redis.call('HINCRBY', KEYS[8], ARGV[15], 1)
            end
            redis.call('EXPIRE', KEYS[8], ARGV[10])
            redis.call('ZADD', KEYS[9], ARGV[9], ARGV[9])
            redis.call('ZREMRANGEBYSCORE', KEYS[9], '-inf', tonumber(ARGV[9]) - tonumber(ARGV[11]))
            redis.call('EXPIRE', KEYS[9], math.ceil(tonumber(ARGV[11]) / 1000))
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> MASTER_DONE_SCRIPT = new DefaultRedisScript<>(
            """
            local previousMasterStatus = redis.call('HGET', KEYS[1], 'status') or ''
            local nextStatus = ARGV[2]
            if previousMasterStatus == 'PAUSED'
              or previousMasterStatus == 'STOPPING'
              or previousMasterStatus == 'FAILED'
              or previousMasterStatus == 'COMPLETED'
              or previousMasterStatus == 'COMPLETED_WITH_ERRORS' then
              nextStatus = previousMasterStatus
            end
            redis.call('HSET', KEYS[1],
              'masterTaskId', ARGV[1],
              'status', nextStatus,
              'pageScheduled', ARGV[3],
              'pageCompleted', ARGV[4],
              'pageSucceeded', ARGV[5],
              'pageFailed', ARGV[6],
              'detailScheduled', ARGV[7],
              'detailCompleted', ARGV[8],
              'detailSucceeded', ARGV[9],
              'detailFailed', ARGV[10],
              'lastError', ARGV[11],
              'updatedAt', ARGV[12])
            redis.call('ZADD', KEYS[2], ARGV[12], ARGV[1])
            redis.call('XADD', KEYS[3], 'MAXLEN', '~', ARGV[13], '*',
              'event', 'master_done',
              'masterTaskId', ARGV[1],
              'status', nextStatus,
              'pageCompleted', ARGV[4],
              'detailCompleted', ARGV[8])
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> SCHEDULE_PAGE_SCRIPT = new DefaultRedisScript<>(
            """
            local previousMasterStatus = redis.call('HGET', KEYS[2], 'status') or ''
            if previousMasterStatus == 'PAUSED'
              or previousMasterStatus == 'STOPPING'
              or previousMasterStatus == 'FAILED'
              or previousMasterStatus == 'COMPLETED'
              or previousMasterStatus == 'COMPLETED_WITH_ERRORS' then
              return -1
            end
            local added = redis.call('SADD', KEYS[1], ARGV[3])
            if added == 0 then
              return 0
            end
            redis.call('HINCRBY', KEYS[2], 'pageScheduled', 1)
            redis.call('HSET', KEYS[2],
              'masterTaskId', ARGV[1],
              'status', 'RUNNING',
              'updatedAt', ARGV[4])
            redis.call('SADD', KEYS[4], ARGV[2])
            redis.call('HSET', KEYS[5],
              'masterTaskId', ARGV[1],
              'pageTaskId', ARGV[2],
              'pageNumber', ARGV[3],
              'detailScheduled', '0',
              'detailCompleted', '0',
              'detailSucceeded', '0',
              'detailFailed', '0',
              'status', 'QUEUED',
              'updatedAt', ARGV[4])
            redis.call('ZADD', KEYS[3], ARGV[4], ARGV[1])
            redis.call('XADD', KEYS[6], 'MAXLEN', '~', ARGV[5], '*',
              'event', 'page_scheduled',
              'masterTaskId', ARGV[1],
              'pageTaskId', ARGV[2],
              'pageNumber', ARGV[3])
            redis.call('HINCRBY', KEYS[7], ARGV[9], 1)
            redis.call('EXPIRE', KEYS[7], ARGV[7])
            redis.call('ZADD', KEYS[8], ARGV[6], ARGV[6])
            redis.call('ZREMRANGEBYSCORE', KEYS[8], '-inf', tonumber(ARGV[6]) - tonumber(ARGV[8]))
            redis.call('EXPIRE', KEYS[8], math.ceil(tonumber(ARGV[8]) / 1000))
            return tonumber(ARGV[3])
            """,
            Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_SCHEDULED_PAGE_SCRIPT = new DefaultRedisScript<>(
            """
            local removed = redis.call('SREM', KEYS[1], ARGV[3])
            if removed == 0 then
              return 0
            end
            local pageStatus = redis.call('HGET', KEYS[5], 'status') or ''
            if pageStatus ~= '' and pageStatus ~= 'QUEUED' then
              redis.call('SADD', KEYS[1], ARGV[3])
              return 0
            end
            redis.call('SREM', KEYS[4], ARGV[2])
            redis.call('DEL', KEYS[5])
            local scheduled = tonumber(redis.call('HGET', KEYS[2], 'pageScheduled') or '0')
            if scheduled > 0 then
              scheduled = redis.call('HINCRBY', KEYS[2], 'pageScheduled', -1)
            end
            if scheduled <= 0 then
              redis.call('HSET', KEYS[2], 'status', 'QUEUED')
            end
            redis.call('HSET', KEYS[2],
              'masterTaskId', ARGV[1],
              'updatedAt', ARGV[4])
            redis.call('ZADD', KEYS[3], ARGV[4], ARGV[1])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int eventStreamMaxLen;
    private final String rateMetricsKeyPrefix;
    private final Duration rateBucketSize;
    private final Duration rateBucketTtl;
    private final Duration rateBucketIndexRetention;

    TaskProgressRedisService(
            StringRedisTemplate redisTemplate,
            @Value("${app.task.runtime.event-stream-maxlen:50000}") int eventStreamMaxLen,
            @Value("${app.rate-metrics.key-prefix:ircs:metrics:rate}") String rateMetricsKeyPrefix,
            @Value("${app.rate-metrics.bucket-size:PT10S}") Duration rateBucketSize,
            @Value("${app.rate-metrics.bucket-ttl:PT30M}") Duration rateBucketTtl,
            @Value("${app.rate-metrics.bucket-index-retention:PT31M}") Duration rateBucketIndexRetention) {
        this.redisTemplate = redisTemplate;
        this.eventStreamMaxLen = Math.max(1000, eventStreamMaxLen);
        this.rateMetricsKeyPrefix = RateMetricKeys.normalizeKeyPrefix(rateMetricsKeyPrefix);
        this.rateBucketSize = rateBucketSize == null ? RateMetricKeys.DEFAULT_BUCKET_SIZE : rateBucketSize;
        this.rateBucketTtl = rateBucketTtl == null ? RateMetricKeys.DEFAULT_BUCKET_TTL : rateBucketTtl;
        this.rateBucketIndexRetention = rateBucketIndexRetention == null
                ? RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION
                : rateBucketIndexRetention;
    }

    long trySchedulePage(UUID masterTaskId, UUID pageTaskId, int pageNumber, Instant scheduledAt) {
        if (masterTaskId == null || pageTaskId == null || pageNumber < 1) {
            return 0;
        }
        Instant effectiveAt = scheduledAt == null ? Instant.now() : scheduledAt;
        RateBucket bucket = rateBucket(effectiveAt);
        Long result = redisTemplate.execute(
                SCHEDULE_PAGE_SCRIPT,
                List.of(
                        TaskHotKeys.masterScheduledPages(masterTaskId),
                        TaskHotKeys.masterState(masterTaskId),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.masterDiscoveredPages(masterTaskId),
                        TaskHotKeys.pageState(pageTaskId),
                        TaskHotKeys.eventStream(),
                        bucket.bucketKey(),
                        bucket.bucketIndexKey()),
                masterTaskId.toString(),
                pageTaskId.toString(),
                Integer.toString(pageNumber),
                Long.toString(effectiveAt.toEpochMilli()),
                Integer.toString(eventStreamMaxLen),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.COLLECTION_PAGE_SCHEDULED);
        return result == null ? 0 : result;
    }

    long rollbackScheduledPage(UUID masterTaskId, UUID pageTaskId, int pageNumber, Instant rolledBackAt) {
        if (masterTaskId == null || pageTaskId == null || pageNumber < 1) {
            return 0;
        }
        Instant effectiveAt = rolledBackAt == null ? Instant.now() : rolledBackAt;
        Long result = redisTemplate.execute(
                ROLLBACK_SCHEDULED_PAGE_SCRIPT,
                List.of(
                        TaskHotKeys.masterScheduledPages(masterTaskId),
                        TaskHotKeys.masterState(masterTaskId),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.masterDiscoveredPages(masterTaskId),
                        TaskHotKeys.pageState(pageTaskId)),
                masterTaskId.toString(),
                pageTaskId.toString(),
                Integer.toString(pageNumber),
                Long.toString(effectiveAt.toEpochMilli()));
        return result == null ? 0 : result;
    }

    void holdMaster(UUID masterTaskId, String status, String reason, Instant heldAt) {
        if (masterTaskId == null) {
            return;
        }
        Instant effectiveAt = heldAt == null ? Instant.now() : heldAt;
        String normalizedStatus = normalizeHoldStatus(status);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("masterTaskId", masterTaskId.toString());
        values.put("status", normalizedStatus);
        values.put("updatedAt", Long.toString(effectiveAt.toEpochMilli()));
        if (reason != null && !reason.isBlank()) {
            values.put("controlReason", safeError(reason));
        }
        redisTemplate.opsForHash().putAll(TaskHotKeys.masterState(masterTaskId), values);
        redisTemplate.opsForSet().add(TaskHotKeys.activeMasters(), masterTaskId.toString());
        redisTemplate.opsForZSet().add(TaskHotKeys.dirtyMasters(), masterTaskId.toString(), effectiveAt.toEpochMilli());
    }

    boolean allowsDispatch(UUID masterTaskId) {
        if (masterTaskId == null) {
            return false;
        }
        Object status = redisTemplate.opsForHash().get(TaskHotKeys.masterState(masterTaskId), "status");
        return !isBlockedStatus(status == null ? null : status.toString());
    }

    Optional<PageProgressState> pageProgress(UUID masterTaskId, UUID pageTaskId) {
        Map<String, String> pageState = hash(TaskHotKeys.pageState(pageTaskId));
        if (pageState.isEmpty()) {
            return Optional.empty();
        }
        Integer pageNumber = intValue(pageState, "pageNumber", null);
        if (pageNumber == null || pageNumber < 1) {
            return Optional.empty();
        }
        String status = pageState.get("status");
        long detailScheduled = longValue(pageState, "detailScheduled", 0);
        long detailCompleted = longValue(pageState, "detailCompleted", 0);
        boolean terminal = "COMPLETED".equalsIgnoreCase(status)
                || "COMPLETED_WITH_ERRORS".equalsIgnoreCase(status)
                || (detailScheduled > 0 && detailCompleted >= detailScheduled);
        if (!terminal) {
            return Optional.empty();
        }
        Map<String, String> masterState = hash(TaskHotKeys.masterState(masterTaskId));
        return Optional.of(new PageProgressState(
                pageNumber,
                intValue(masterState, "totalPages", null),
                status));
    }

    long recordPageDiscovered(TaskPageDiscoveredMessage message) {
        RateBucket bucket = rateBucket(message.discoveredAt());
        Long result = redisTemplate.execute(
                PAGE_DISCOVERED_SCRIPT,
                List.of(
                        TaskHotKeys.masterDiscoveredPages(message.masterTaskId()),
                        TaskHotKeys.pageState(message.pageTaskId()),
                        TaskHotKeys.masterState(message.masterTaskId()),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.eventStream(),
                        bucket.bucketKey(),
                        bucket.bucketIndexKey()),
                message.pageTaskId().toString(),
                Integer.toString(message.pageNumber()),
                Long.toString(message.detailScheduled()),
                optionalInt(message.totalPages()),
                optionalInt(message.totalItems()),
                Long.toString(message.discoveredAt().toEpochMilli()),
                message.masterTaskId().toString(),
                Integer.toString(eventStreamMaxLen),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.COLLECTION_PAGE_DISCOVERED,
                RateMetricKeys.COLLECTION_PAGE_COMPLETED);
        return result == null ? 0 : result;
    }

    long recordPageFailed(TaskPageFailedMessage message) {
        Instant failedAt = message.failedAt() == null ? Instant.now() : message.failedAt();
        RateBucket bucket = rateBucket(failedAt);
        Long result = redisTemplate.execute(
                PAGE_FAILED_SCRIPT,
                List.of(
                        TaskHotKeys.masterDiscoveredPages(message.masterTaskId()),
                        TaskHotKeys.pageState(message.pageTaskId()),
                        TaskHotKeys.masterState(message.masterTaskId()),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.eventStream(),
                        bucket.bucketKey(),
                        bucket.bucketIndexKey()),
                message.pageTaskId().toString(),
                Integer.toString(message.pageNumber()),
                safeError(message.errorMessage()),
                Long.toString(failedAt.toEpochMilli()),
                message.masterTaskId().toString(),
                Integer.toString(eventStreamMaxLen),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.COLLECTION_PAGE_FAILED);
        return result == null ? 0 : result;
    }

    long recordDetailDone(TaskDetailDoneMessage message) {
        return recordDetailCompleted(new TaskDetailCompletion(
                message.masterTaskId(),
                message.pageTaskId(),
                message.detailTaskId(),
                message.sourceVid(),
                message.successful(),
                message.errorMessage(),
                message.completedAt()));
    }

    long recordMasterDone(TaskMasterDoneMessage message) {
        Instant completedAt = message.completedAt() == null ? Instant.now() : message.completedAt();
        Long result = redisTemplate.execute(
                MASTER_DONE_SCRIPT,
                List.of(
                        TaskHotKeys.masterState(message.masterTaskId()),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.eventStream()),
                message.masterTaskId().toString(),
                safeStatus(message.status()),
                Long.toString(message.pageScheduled()),
                Long.toString(message.pageCompleted()),
                Long.toString(message.pageSucceeded()),
                Long.toString(message.pageFailed()),
                Long.toString(message.detailScheduled()),
                Long.toString(message.detailCompleted()),
                Long.toString(message.detailSucceeded()),
                Long.toString(message.detailFailed()),
                safeError(message.lastError()),
                Long.toString(completedAt.toEpochMilli()),
                Integer.toString(eventStreamMaxLen));
        return result == null ? 0 : result;
    }

    long recordDetailCompleted(TaskDetailCompletion completion) {
        RateBucket bucket = rateBucket(completion.completedAt());
        Long result = redisTemplate.execute(
                DETAIL_COMPLETED_SCRIPT,
                List.of(
                        TaskHotKeys.pageCompletedDetails(completion.pageTaskId()),
                        TaskHotKeys.pageState(completion.pageTaskId()),
                        TaskHotKeys.masterState(completion.masterTaskId()),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.pageFailedDetails(completion.pageTaskId()),
                        TaskHotKeys.pageFailedDetailErrors(completion.pageTaskId()),
                        TaskHotKeys.eventStream(),
                        bucket.bucketKey(),
                        bucket.bucketIndexKey()),
                completion.detailTaskId().toString(),
                Boolean.toString(completion.successful()),
                Long.toString(completion.completedAt().toEpochMilli()),
                completion.masterTaskId().toString(),
                safeError(completion.errorMessage()),
                completion.pageTaskId().toString(),
                safeSourceVid(completion.sourceVid()),
                Integer.toString(eventStreamMaxLen),
                bucket.bucketStartMillis(),
                bucket.bucketTtlSeconds(),
                bucket.bucketIndexRetentionMillis(),
                RateMetricKeys.COLLECTION_DETAIL_COMPLETED,
                RateMetricKeys.COLLECTION_DETAIL_SUCCEEDED,
                RateMetricKeys.COLLECTION_DETAIL_FAILED,
                RateMetricKeys.COLLECTION_PAGE_COMPLETED);
        return result == null ? 0 : result;
    }

    private RateBucket rateBucket(Instant eventAt) {
        Instant effectiveAt = eventAt == null ? Instant.now() : eventAt;
        long bucketStart = RateMetricKeys.bucketStartMillis(effectiveAt.toEpochMilli(), rateBucketSize);
        return new RateBucket(
                RateMetricKeys.bucketKey(rateMetricsKeyPrefix, bucketStart),
                RateMetricKeys.bucketIndexKey(rateMetricsKeyPrefix),
                Long.toString(bucketStart),
                Long.toString(RateMetricKeys.ttlSeconds(rateBucketTtl, RateMetricKeys.DEFAULT_BUCKET_TTL)),
                Long.toString(RateMetricKeys.retentionMillis(
                        rateBucketIndexRetention,
                        RateMetricKeys.DEFAULT_BUCKET_INDEX_RETENTION)));
    }

    private Map<String, String> hash(String key) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((field, value) -> {
            if (field != null && value != null) {
                result.put(field.toString(), value.toString());
            }
        });
        return result;
    }

    private long longValue(Map<String, String> values, String key, long fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Integer intValue(Map<String, String> values, String key, Integer fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String optionalInt(Integer value) {
        return value == null ? "" : value.toString();
    }

    private String safeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "";
        }
        String normalized = errorMessage.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private String safeSourceVid(String sourceVid) {
        if (sourceVid == null || sourceVid.isBlank()) {
            return "";
        }
        String normalized = sourceVid.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private String safeStatus(String status) {
        return status == null || status.isBlank() ? "COMPLETED" : status.trim().toUpperCase();
    }

    private String normalizeHoldStatus(String status) {
        String normalized = status == null || status.isBlank() ? "PAUSED" : status.trim().toUpperCase();
        return "STOPPING".equals(normalized) ? "STOPPING" : "PAUSED";
    }

    private boolean isBlockedStatus(String status) {
        String normalized = status == null || status.isBlank() ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "PAUSED", "STOPPING", "FAILED", "COMPLETED", "COMPLETED_WITH_ERRORS" -> true;
            default -> false;
        };
    }
}

record PageProgressState(
        int pageNumber,
        Integer totalPages,
        String status
) {
}

record TaskDetailCompletion(
        UUID masterTaskId,
        UUID pageTaskId,
        UUID detailTaskId,
        String sourceVid,
        boolean successful,
        String errorMessage,
        Instant completedAt
) {
}

record RateBucket(
        String bucketKey,
        String bucketIndexKey,
        String bucketStartMillis,
        String bucketTtlSeconds,
        String bucketIndexRetentionMillis
) {
}
