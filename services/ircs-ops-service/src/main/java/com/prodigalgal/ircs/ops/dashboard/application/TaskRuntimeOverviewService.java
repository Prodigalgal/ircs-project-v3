package com.prodigalgal.ircs.ops.dashboard.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.contracts.task.TaskMasterSnapshot;
import com.prodigalgal.ircs.contracts.task.TaskRuntimeHotKeys;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewItemResponse;
import com.prodigalgal.ircs.ops.dashboard.dto.TaskRuntimeOverviewResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
class TaskRuntimeOverviewService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RuntimeConfigService runtimeConfig;
    private final int fallbackMaxLimit;

    TaskRuntimeOverviewService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RuntimeConfigService runtimeConfig
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.runtimeConfig = runtimeConfig;
        this.fallbackMaxLimit = DEFAULT_MAX_LIMIT;
    }

    TaskRuntimeOverviewResponse currentOverview(int limit) {
        int safeLimit = sanitizeLimit(limit);
        List<UUID> activeIds = activeMasterIds();
        List<TaskRuntimeOverviewItemResponse> items = activeIds.stream()
                .limit(safeLimit)
                .map(this::item)
                .sorted(Comparator
                        .comparingInt((TaskRuntimeOverviewItemResponse item) -> attentionRank(item.attentionLevel()))
                        .thenComparing(
                                item -> item.updatedAt() == null ? Instant.EPOCH : item.updatedAt(),
                                Comparator.reverseOrder())
                        .thenComparing(item -> item.masterTaskId().toString()))
                .toList();

        return new TaskRuntimeOverviewResponse(
                Instant.now(),
                safeLimit,
                activeIds.size(),
                items.size(),
                dirtyMasterCount(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::pageScheduled).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::pageDiscovered).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::pageCompleted).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::pageFailed).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::detailScheduled).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::detailCompleted).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::detailSucceeded).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::detailFailed).sum(),
                items.stream().mapToLong(TaskRuntimeOverviewItemResponse::detailBacklog).sum(),
                countsBy(items, TaskRuntimeOverviewItemResponse::status),
                countsBy(items, TaskRuntimeOverviewItemResponse::attentionLevel),
                items);
    }

    private TaskRuntimeOverviewItemResponse item(UUID masterTaskId) {
        Optional<TaskMasterSnapshot> snapshot = snapshot(masterTaskId);
        Map<String, String> state = hash(TaskRuntimeHotKeys.masterState(masterTaskId));
        TaskMasterSnapshot value = snapshot.orElse(null);
        long detailScheduled = longValue(state, "detailScheduled", value == null ? 0 : value.detailScheduled());
        long detailCompleted = longValue(state, "detailCompleted", value == null ? 0 : value.detailCompleted());
        long detailFailed = longValue(state, "detailFailed", value == null ? 0 : value.detailFailed());
        String status = firstNonBlank(state.get("status"), value == null ? null : value.status(), "UNKNOWN");
        String lastError = firstNonBlank(state.get("lastError"), value == null ? null : value.lastError());
        long detailBacklog = Math.max(0, detailScheduled - detailCompleted);

        return new TaskRuntimeOverviewItemResponse(
                masterTaskId,
                value == null ? null : value.dataSourceId(),
                firstNonBlank(value == null ? null : value.taskName(), state.get("taskName")),
                status,
                value != null && value.resume(),
                value == null ? 1 : value.startPage(),
                value == null ? null : value.endPage(),
                longValue(state, "pageScheduled", value == null ? 0 : value.pageScheduled()),
                longValue(state, "pageDiscovered", 0),
                longValue(state, "pageCompleted", value == null ? 0 : value.pageCompleted()),
                longValue(state, "pageFailed", value == null ? 0 : value.pageFailed()),
                detailScheduled,
                detailCompleted,
                longValue(state, "detailSucceeded", value == null ? 0 : value.detailSucceeded()),
                detailFailed,
                detailBacklog,
                percent(detailCompleted, detailScheduled),
                attentionLevel(status, detailFailed, detailBacklog, lastError),
                lastError,
                firstNonBlank(state.get("correlationId"), value == null ? null : value.correlationId(), masterTaskId.toString()),
                value == null ? null : value.queuedAt(),
                instantFromEpoch(state, "updatedAt").orElse(value == null ? null : value.updatedAt()),
                snapshot.isPresent(),
                !state.isEmpty());
    }

    private List<UUID> activeMasterIds() {
        Set<String> members;
        try {
            members = redisTemplate.opsForSet().members(TaskRuntimeHotKeys.activeMasters());
        } catch (RuntimeException ex) {
            return List.of();
        }
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (String member : members) {
            try {
                ids.add(UUID.fromString(member));
            } catch (RuntimeException ignored) {
                // Corrupt active set members are ignored; Redis remains inspectable via raw tools.
            }
        }
        ids.sort(Comparator.comparing(UUID::toString));
        return ids;
    }

    private Optional<TaskMasterSnapshot> snapshot(UUID masterTaskId) {
        String json;
        try {
            json = redisTemplate.opsForValue().get(TaskRuntimeHotKeys.masterSnapshot(masterTaskId));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, TaskMasterSnapshot.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Map<String, String> hash(String key) {
        Map<Object, Object> raw;
        try {
            raw = redisTemplate.opsForHash().entries(key);
        } catch (RuntimeException ex) {
            return Map.of();
        }
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

    private long dirtyMasterCount() {
        try {
            Long count = redisTemplate.opsForZSet().zCard(TaskRuntimeHotKeys.dirtyMasters());
            return count == null ? 0 : count;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int sanitizeLimit(int limit) {
        int maxLimit = maxLimit();
        if (limit <= 0) {
            return Math.min(DEFAULT_LIMIT, maxLimit);
        }
        return Math.min(limit, maxLimit);
    }

    private int maxLimit() {
        return runtimeConfig == null
                ? fallbackMaxLimit
                : runtimeConfig.boundedIntValue(
                        "app.ops.task-runtime.overview.max-limit",
                        fallbackMaxLimit,
                        1,
                        1000);
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

    private Optional<Instant> instantFromEpoch(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private int percent(long done, long total) {
        if (total <= 0) {
            return 0;
        }
        return safeInt(Math.min(100, Math.round((done * 100.0d) / total)));
    }

    private String attentionLevel(String status, long failed, long backlog, String lastError) {
        if (failed > 0 || "FAILED".equalsIgnoreCase(status) || "COMPLETED_WITH_ERRORS".equalsIgnoreCase(status)
                || (lastError != null && !lastError.isBlank())) {
            return "ERROR";
        }
        if (backlog > 0 && "RUNNING".equalsIgnoreCase(status)) {
            return "RUNNING";
        }
        if (backlog > 0 || "QUEUED".equalsIgnoreCase(status) || "DISCOVERED".equalsIgnoreCase(status)) {
            return "WAITING";
        }
        return "OK";
    }

    private int attentionRank(String attentionLevel) {
        return switch (attentionLevel == null ? "" : attentionLevel) {
            case "ERROR" -> 0;
            case "RUNNING" -> 1;
            case "WAITING" -> 2;
            default -> 3;
        };
    }

    private Map<String, Long> countsBy(
            List<TaskRuntimeOverviewItemResponse> items,
            java.util.function.Function<TaskRuntimeOverviewItemResponse, String> classifier
    ) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskRuntimeOverviewItemResponse item : items) {
            String key = firstNonBlank(classifier.apply(item), "UNKNOWN");
            counts.merge(key, 1L, Long::sum);
        }
        return counts;
    }

    private int safeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) value);
    }
}
