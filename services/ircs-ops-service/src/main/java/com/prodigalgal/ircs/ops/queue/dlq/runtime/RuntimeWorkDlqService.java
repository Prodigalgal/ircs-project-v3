package com.prodigalgal.ircs.ops.queue.dlq.runtime;

import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.ops.queue.domain.RuntimeWorkQueueCatalog;
import com.prodigalgal.ircs.ops.queue.domain.RuntimeWorkQueueDescriptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RuntimeWorkDlqService {

    private static final int DEFAULT_SAMPLE_LIMIT = 5;
    private static final int MAX_SAMPLE_LIMIT = 50;
    private static final int MAX_ACTION_LIMIT = 100;
    private static final int DEFAULT_MAX_REPLAY_ATTEMPTS = 3;
    private static final int PAYLOAD_PREVIEW_LIMIT = 500;

    private final RuntimeWorkQueue workQueue;

    List<RuntimeWorkDlqQueueResponse> listQueues(int sampleLimit) {
        int safeSampleLimit = safeSampleLimit(sampleLimit);
        return RuntimeWorkQueueCatalog.descriptors().stream()
                .map(descriptor -> queueResponse(descriptor, safeSampleLimit))
                .toList();
    }

    RuntimeWorkDlqActionResponse requeue(String taskType, int limit, int maxReplayAttempts) {
        RuntimeWorkQueueDescriptor descriptor = descriptor(taskType);
        int safeLimit = safeActionLimit(limit);
        int safeMaxReplayAttempts = Math.max(1, maxReplayAttempts <= 0
                ? DEFAULT_MAX_REPLAY_ATTEMPTS
                : maxReplayAttempts);
        int affected = workQueue.requeueDlq(descriptor.taskType(), safeLimit, safeMaxReplayAttempts);
        return new RuntimeWorkDlqActionResponse(
                descriptor.taskType(),
                "requeue",
                safeLimit,
                affected,
                safeMaxReplayAttempts,
                queueResponse(descriptor, DEFAULT_SAMPLE_LIMIT));
    }

    public int requeueOne(String taskType, int maxReplayAttempts) {
        RuntimeWorkQueueDescriptor descriptor = descriptor(taskType);
        return workQueue.requeueDlq(
                descriptor.taskType(),
                1,
                Math.max(1, maxReplayAttempts <= 0 ? DEFAULT_MAX_REPLAY_ATTEMPTS : maxReplayAttempts));
    }

    void heartbeatDlqConsumers(String ownerId, java.time.Duration ttl) {
        if (!StringUtils.hasText(ownerId)) {
            return;
        }
        RuntimeWorkQueueCatalog.descriptors()
                .forEach(descriptor -> workQueue.heartbeatDlqConsumer(descriptor.taskType(), ownerId, ttl));
    }

    private RuntimeWorkDlqQueueResponse queueResponse(RuntimeWorkQueueDescriptor descriptor, int sampleLimit) {
        RuntimeWorkQueueCounts counts = workQueue.counts(descriptor.taskType());
        List<RuntimeWorkDlqItemResponse> samples = sampleLimit <= 0
                ? List.of()
                : workQueue.sampleDlq(descriptor.taskType(), sampleLimit).stream()
                        .map(this::itemResponse)
                        .toList();
        return new RuntimeWorkDlqQueueResponse(
                descriptor.key(),
                descriptor.label(),
                descriptor.taskType(),
                counts.pending(),
                counts.inflight(),
                counts.dlq(),
                workQueue.dlqConsumerCount(descriptor.taskType()),
                samples);
    }

    private RuntimeWorkDlqItemResponse itemResponse(RuntimeWorkItem item) {
        return new RuntimeWorkDlqItemResponse(
                item.taskType(),
                item.taskId(),
                item.submissionId(),
                item.aggregateId(),
                item.version(),
                item.status(),
                item.attempt(),
                item.createdAt(),
                item.updatedAt(),
                item.ownerId(),
                item.lastError(),
                preview(item.payload()));
    }

    private RuntimeWorkQueueDescriptor descriptor(String taskType) {
        return RuntimeWorkQueueCatalog.findByTaskType(taskType)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported runtime work taskType: " + taskType));
    }

    private static int safeSampleLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        return Math.min(limit, MAX_SAMPLE_LIMIT);
    }

    private static int safeActionLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return Math.min(limit, MAX_ACTION_LIMIT);
    }

    private static String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= PAYLOAD_PREVIEW_LIMIT
                ? trimmed
                : trimmed.substring(0, PAYLOAD_PREVIEW_LIMIT);
    }
}
