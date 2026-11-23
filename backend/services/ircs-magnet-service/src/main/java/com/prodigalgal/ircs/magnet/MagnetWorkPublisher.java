package com.prodigalgal.ircs.magnet;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnBean(RuntimeWorkQueue.class)
@Slf4j
class MagnetWorkPublisher {

    private final RuntimeWorkQueue workQueue;
    private final ObjectMapper objectMapper;
    private final MagnetWorkSubmissionStore submissions;

    MagnetWorkPublisher(RuntimeWorkQueue workQueue, ObjectMapper objectMapper, MagnetWorkSubmissionStore submissions) {
        this.workQueue = workQueue;
        this.objectMapper = objectMapper;
        this.submissions = submissions;
    }

    void enqueue(MagnetSearchJobSummary job) {
        if (job == null || job.id() == null || job.catalogEntryId() == null) {
            return;
        }
        MagnetSearchWorkPayload payload = new MagnetSearchWorkPayload(
                job.id(),
                job.catalogEntryId(),
                job.triggerType());
        RuntimeWorkItemRequest request = new RuntimeWorkItemRequest(
                MagnetWorkTypes.SEARCH,
                MagnetWorkTypes.searchTaskId(job.id()),
                job.catalogEntryId().toString(),
                job.triggerType(),
                serialize(payload));
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { submissions.submitIfAbsent(request); }
            });
        } else {
            submissions.submitIfAbsent(request);
        }
    }

    long backlogDepth() {
        RuntimeWorkQueueCounts counts = workQueue.counts(MagnetWorkTypes.SEARCH);
        if (counts == null) {
            throw new IllegalStateException("Magnet work queue counts are unavailable");
        }
        long pending = Math.max(0, counts.pending());
        long inflight = Math.max(0, counts.inflight());
        return pending > Long.MAX_VALUE - inflight ? Long.MAX_VALUE : pending + inflight;
    }

    private String serialize(MagnetSearchWorkPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize magnet search work", ex);
        }
    }
}
