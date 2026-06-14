package com.prodigalgal.ircs.ops;

import java.util.Arrays;
import java.util.Optional;

enum MaintenanceLlmCleaningTask {
    LANGUAGE("llm-sanitize", "language"),
    AREA("llm-area-clean", "area"),
    GENRE("llm-genre-clean", "genre"),
    CATEGORY("llm-category-clean", "category");

    private final String taskName;
    private final String ownerKind;

    MaintenanceLlmCleaningTask(String taskName, String ownerKind) {
        this.taskName = taskName;
        this.ownerKind = ownerKind;
    }

    String taskName() {
        return taskName;
    }

    String ownerKind() {
        return ownerKind;
    }

    static Optional<MaintenanceLlmCleaningTask> fromTaskName(String taskName) {
        return Arrays.stream(values())
                .filter(task -> task.taskName.equals(taskName))
                .findFirst();
    }
}
