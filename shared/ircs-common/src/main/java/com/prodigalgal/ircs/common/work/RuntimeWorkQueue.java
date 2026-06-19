package com.prodigalgal.ircs.common.work;

import java.time.Duration;
import java.util.List;

public interface RuntimeWorkQueue {

    void submit(RuntimeWorkItemRequest request);

    void submit(RuntimeWorkItemRequest request, Duration delay);

    void submitAfterCommit(RuntimeWorkItemRequest request);

    void submitAfterCommit(RuntimeWorkItemRequest request, Duration delay);

    List<RuntimeWorkItem> claim(String taskType, String ownerId, int limit, Duration visibilityTimeout);

    boolean complete(RuntimeWorkItem item);

    boolean fail(RuntimeWorkItem item, boolean retryable, Duration retryDelay, String reason);

    int requeueExpired(String taskType, int limit);

    RuntimeWorkQueueCounts counts(String taskType);

    default void heartbeatConsumer(String taskType, String ownerId, Duration ttl) {
    }

    default long consumerCount(String taskType) {
        return 0L;
    }

    default long expiredInflightCount(String taskType) {
        return 0L;
    }
}
