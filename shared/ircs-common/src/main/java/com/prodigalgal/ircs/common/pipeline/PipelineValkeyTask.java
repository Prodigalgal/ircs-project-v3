package com.prodigalgal.ircs.common.pipeline;

import java.util.Map;
import java.util.UUID;
import org.springframework.util.StringUtils;

public record PipelineValkeyTask(
        String taskType,
        String taskId,
        UUID rawVideoId,
        String pipelineVersion,
        String payload,
        int attempt,
        String ownerId) {

    static PipelineValkeyTask fromHash(String taskType, String taskId, Map<Object, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalStateException("Pipeline task payload is missing: taskType=" + taskType + ", taskId=" + taskId);
        }
        String rawVideoId = value(fields, "rawVideoId");
        if (!StringUtils.hasText(rawVideoId)) {
            throw new IllegalStateException("Pipeline task rawVideoId is missing: taskType=" + taskType + ", taskId=" + taskId);
        }
        return new PipelineValkeyTask(
                valueOrDefault(fields, "taskType", taskType),
                taskId,
                UUID.fromString(rawVideoId),
                value(fields, "pipelineVersion"),
                value(fields, "payload"),
                parseInt(value(fields, "attempt")),
                value(fields, "ownerId"));
    }

    private static String value(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : value.toString();
    }

    private static String valueOrDefault(Map<Object, Object> fields, String key, String defaultValue) {
        String value = value(fields, key);
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static int parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
