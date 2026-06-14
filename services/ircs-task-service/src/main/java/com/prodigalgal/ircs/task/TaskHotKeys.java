package com.prodigalgal.ircs.task;

import com.prodigalgal.ircs.contracts.task.TaskRuntimeHotKeys;
import java.util.UUID;

final class TaskHotKeys {

    static String masterSnapshot(UUID masterTaskId) {
        return TaskRuntimeHotKeys.masterSnapshot(masterTaskId);
    }

    static String masterState(UUID masterTaskId) {
        return TaskRuntimeHotKeys.masterState(masterTaskId);
    }

    static String pageState(UUID pageTaskId) {
        return TaskRuntimeHotKeys.pageState(pageTaskId);
    }

    static String pageCompletedDetails(UUID pageTaskId) {
        return TaskRuntimeHotKeys.pageCompletedDetails(pageTaskId);
    }

    static String pageFailedDetails(UUID pageTaskId) {
        return TaskRuntimeHotKeys.pageFailedDetails(pageTaskId);
    }

    static String pageFailedDetailErrors(UUID pageTaskId) {
        return TaskRuntimeHotKeys.pageFailedDetailErrors(pageTaskId);
    }

    static String masterDiscoveredPages(UUID masterTaskId) {
        return TaskRuntimeHotKeys.masterDiscoveredPages(masterTaskId);
    }

    static String masterScheduledPages(UUID masterTaskId) {
        return TaskRuntimeHotKeys.masterScheduledPages(masterTaskId);
    }

    static String activeMasters() {
        return TaskRuntimeHotKeys.activeMasters();
    }

    static String sourceMasters(UUID dataSourceId) {
        return TaskRuntimeHotKeys.sourceMasters(dataSourceId);
    }

    static String dirtyMasters() {
        return TaskRuntimeHotKeys.dirtyMasters();
    }

    private TaskHotKeys() {
    }
}
