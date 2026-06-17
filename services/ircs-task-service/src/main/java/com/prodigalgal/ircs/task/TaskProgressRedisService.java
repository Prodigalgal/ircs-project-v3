package com.prodigalgal.ircs.task;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
class TaskProgressRedisService {

    private static final DefaultRedisScript<Long> PAGE_DISCOVERED_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('SADD', KEYS[1], ARGV[1])
            local previousStatus = redis.call('HGET', KEYS[2], 'status') or ''
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
            local masterStatus = 'RUNNING'
            if pageScheduled > 0 and pageCompleted >= pageScheduled and detailCompleted >= masterDetailScheduled then
              if pageFailed > 0 or detailFailed > 0 then
                masterStatus = 'FAILED'
              else
                masterStatus = 'COMPLETED'
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
            return firstDiscovery
            """,
            Long.class);

    private static final DefaultRedisScript<Long> PAGE_FAILED_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('SADD', KEYS[1], ARGV[1])
            local previousStatus = redis.call('HGET', KEYS[2], 'status') or ''
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
            local masterStatus = 'RUNNING'
            if pageScheduled > 0 and pageCompleted >= pageScheduled and masterDetailCompleted >= masterDetailScheduled then
              masterStatus = 'FAILED'
            end
            redis.call('HSET', KEYS[3],
              'masterTaskId', ARGV[5],
              'status', masterStatus,
              'lastError', ARGV[3],
              'updatedAt', ARGV[4])
            redis.call('ZADD', KEYS[4], ARGV[4], ARGV[5])
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
            local masterStatus = 'RUNNING'
            if pageScheduled > 0
              and pageCompleted >= pageScheduled
              and masterDetailCompleted >= masterDetailScheduled then
              if pageFailed > 0 or masterDetailFailed > 0 then
                masterStatus = 'FAILED'
              else
                masterStatus = 'COMPLETED'
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
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> MASTER_DONE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1],
              'masterTaskId', ARGV[1],
              'status', ARGV[2],
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
            return 1
            """,
            Long.class);

    private static final DefaultRedisScript<Long> SCHEDULE_PAGE_SCRIPT = new DefaultRedisScript<>(
            """
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

    TaskProgressRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    long trySchedulePage(UUID masterTaskId, UUID pageTaskId, int pageNumber, Instant scheduledAt) {
        if (masterTaskId == null || pageTaskId == null || pageNumber < 1) {
            return 0;
        }
        Instant effectiveAt = scheduledAt == null ? Instant.now() : scheduledAt;
        Long result = redisTemplate.execute(
                SCHEDULE_PAGE_SCRIPT,
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
        Long result = redisTemplate.execute(
                PAGE_DISCOVERED_SCRIPT,
                List.of(
                        TaskHotKeys.masterDiscoveredPages(message.masterTaskId()),
                        TaskHotKeys.pageState(message.pageTaskId()),
                        TaskHotKeys.masterState(message.masterTaskId()),
                        TaskHotKeys.dirtyMasters()),
                message.pageTaskId().toString(),
                Integer.toString(message.pageNumber()),
                Long.toString(message.detailScheduled()),
                optionalInt(message.totalPages()),
                optionalInt(message.totalItems()),
                Long.toString(message.discoveredAt().toEpochMilli()),
                message.masterTaskId().toString());
        return result == null ? 0 : result;
    }

    long recordPageFailed(TaskPageFailedMessage message) {
        Instant failedAt = message.failedAt() == null ? Instant.now() : message.failedAt();
        Long result = redisTemplate.execute(
                PAGE_FAILED_SCRIPT,
                List.of(
                        TaskHotKeys.masterDiscoveredPages(message.masterTaskId()),
                        TaskHotKeys.pageState(message.pageTaskId()),
                        TaskHotKeys.masterState(message.masterTaskId()),
                        TaskHotKeys.dirtyMasters()),
                message.pageTaskId().toString(),
                Integer.toString(message.pageNumber()),
                safeError(message.errorMessage()),
                Long.toString(failedAt.toEpochMilli()),
                message.masterTaskId().toString());
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
                        TaskHotKeys.dirtyMasters()),
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
                Long.toString(completedAt.toEpochMilli()));
        return result == null ? 0 : result;
    }

    long recordDetailCompleted(TaskDetailCompletion completion) {
        Long result = redisTemplate.execute(
                DETAIL_COMPLETED_SCRIPT,
                List.of(
                        TaskHotKeys.pageCompletedDetails(completion.pageTaskId()),
                        TaskHotKeys.pageState(completion.pageTaskId()),
                        TaskHotKeys.masterState(completion.masterTaskId()),
                        TaskHotKeys.dirtyMasters(),
                        TaskHotKeys.pageFailedDetails(completion.pageTaskId()),
                        TaskHotKeys.pageFailedDetailErrors(completion.pageTaskId())),
                completion.detailTaskId().toString(),
                Boolean.toString(completion.successful()),
                Long.toString(completion.completedAt().toEpochMilli()),
                completion.masterTaskId().toString(),
                safeError(completion.errorMessage()),
                completion.pageTaskId().toString(),
                safeSourceVid(completion.sourceVid()));
        return result == null ? 0 : result;
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
