package com.prodigalgal.ircs.task;

import java.util.Locale;

enum TaskRuntimeStatus {
    QUEUED,
    RUNNING,
    DISCOVERED,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    PAUSED,
    STOPPING;

    String value() {
        return name();
    }

    static String normalizeValue(String status, TaskRuntimeStatus fallback) {
        if (status == null || status.isBlank()) {
            return fallback.value();
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    static String normalizeHoldStatus(String status) {
        return STOPPING == parse(status) ? STOPPING.value() : PAUSED.value();
    }

    static boolean isBlockedForDispatch(String status) {
        TaskRuntimeStatus parsed = parse(status);
        return parsed == PAUSED
                || parsed == STOPPING
                || parsed == FAILED
                || parsed == COMPLETED_WITH_ERRORS;
    }

    static boolean allowsPageExpansion(String status) {
        TaskRuntimeStatus parsed = parse(status);
        return parsed == QUEUED || parsed == RUNNING;
    }

    static boolean isPageTerminal(String status) {
        TaskRuntimeStatus parsed = parse(status);
        return parsed == COMPLETED || parsed == COMPLETED_WITH_ERRORS;
    }

    static boolean isCompleted(String status) {
        return parse(status) == COMPLETED;
    }

    static boolean isErrorLike(String status) {
        TaskRuntimeStatus parsed = parse(status);
        return parsed == FAILED || parsed == COMPLETED_WITH_ERRORS;
    }

    private static TaskRuntimeStatus parse(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
