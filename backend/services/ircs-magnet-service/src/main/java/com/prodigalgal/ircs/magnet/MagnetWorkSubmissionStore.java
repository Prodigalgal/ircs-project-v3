package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;

interface MagnetWorkSubmissionStore {
    boolean submitIfAbsent(RuntimeWorkItemRequest request);
    String status(String taskId);
}
