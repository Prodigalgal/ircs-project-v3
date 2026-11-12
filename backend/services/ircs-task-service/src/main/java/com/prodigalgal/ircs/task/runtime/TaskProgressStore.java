package com.prodigalgal.ircs.task.runtime;

import com.prodigalgal.ircs.contracts.task.TaskDetailDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskMasterDoneMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageDiscoveredMessage;
import com.prodigalgal.ircs.contracts.task.TaskPageFailedMessage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic progress operations required by task orchestration. */
public interface TaskProgressStore {

    long trySchedulePage(UUID masterTaskId, UUID pageTaskId, int pageNumber, Instant scheduledAt);

    long rollbackScheduledPage(UUID masterTaskId, UUID pageTaskId, int pageNumber, Instant rolledBackAt);

    void holdMaster(UUID masterTaskId, String status, String reason, Instant heldAt);

    boolean allowsDispatch(UUID masterTaskId);

    Optional<PageProgressState> pageProgress(UUID masterTaskId, UUID pageTaskId);

    Optional<MasterProgressState> masterProgress(UUID masterTaskId);

    long recordPageDiscovered(TaskPageDiscoveredMessage message);

    long recordPageFailed(TaskPageFailedMessage message);

    long recordDetailDone(TaskDetailDoneMessage message);

    long recordMasterDone(TaskMasterDoneMessage message);

    long recordDetailCompleted(TaskDetailCompletion completion);
}
