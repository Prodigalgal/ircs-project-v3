package com.prodigalgal.ircs.search.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPayload;
import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SearchSyncMessage;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchSyncWorkQueueWorkerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchSyncProcessor syncProcessor = org.mockito.Mockito.mock(SearchSyncProcessor.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);

    @Test
    void processesClaimedRuntimeWorkAndCompletesIt() throws Exception {
        FakeRuntimeWorkQueue queue = new FakeRuntimeWorkQueue();
        UUID id = UUID.randomUUID();
        RuntimeWorkItem item = item(id, 1, SyncOperation.INDEX);
        queue.rawItems.add(item);
        SearchSyncWorkQueueWorker worker = worker(queue);

        int processed = worker.runOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(queue.completed).containsExactly(item);
        verify(syncProcessor).process(org.mockito.ArgumentMatchers.argThat(message ->
                id.equals(message.getEntityId())
                        && message.getEntityType() == SearchEntityType.RAW_VIDEO
                        && message.getOperation() == SyncOperation.INDEX));
        assertThat(worker.state().lastProcessed()).isEqualTo(1);
        assertThat(worker.state().consecutiveFailures()).isZero();
    }

    @Test
    void sendsFailedRuntimeWorkToDlqAfterMaxRetries() throws Exception {
        FakeRuntimeWorkQueue queue = new FakeRuntimeWorkQueue();
        UUID id = UUID.randomUUID();
        RuntimeWorkItem item = item(id, 5, SyncOperation.INDEX);
        queue.rawItems.add(item);
        doThrow(new RuntimeException("es down"))
                .when(syncProcessor)
                .process(org.mockito.ArgumentMatchers.any(SearchSyncMessage.class));
        SearchSyncWorkQueueWorker worker = worker(queue);

        int processed = worker.runOnce();

        assertThat(processed).isZero();
        assertThat(queue.failures).hasSize(1);
        assertThat(queue.failures.getFirst().retryable()).isFalse();
        assertThat(queue.failures.getFirst().item()).isEqualTo(item);
        assertThat(worker.state().lastFailed()).isEqualTo(1);
        assertThat(worker.state().consecutiveFailures()).isEqualTo(1);
    }

    private SearchSyncWorkQueueWorker worker(FakeRuntimeWorkQueue queue) throws Exception {
        SearchSyncWorkQueueWorker worker = new SearchSyncWorkQueueWorker(
                queue,
                objectMapper,
                syncProcessor,
                auditWriter,
                new SearchSyncWorkQueueMetrics(new SimpleMeterRegistry()));
        set(worker, "batchSize", 10);
        set(worker, "maxRetries", 5);
        set(worker, "visibilitySeconds", 60L);
        set(worker, "maxBackoffSeconds", 300L);
        set(worker, "workerId", "test-worker");
        set(worker, "applicationName", "ircs-search-service");
        return worker;
    }

    private RuntimeWorkItem item(UUID id, int attempt, SyncOperation operation) throws Exception {
        SearchSyncWorkPayload payload = new SearchSyncWorkPayload(
                id,
                SearchEntityType.RAW_VIDEO,
                operation,
                "test",
                "trace-1");
        return new RuntimeWorkItem(
                SearchSyncWorkTypes.RAW,
                "raw_video:" + id,
                UUID.randomUUID().toString(),
                id.toString(),
                operation.name(),
                objectMapper.writeValueAsString(payload),
                "PROCESSING",
                attempt,
                Instant.now().minusSeconds(5),
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(60),
                "test-worker",
                null);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeRuntimeWorkQueue implements RuntimeWorkQueue {
        private final List<RuntimeWorkItem> rawItems = new ArrayList<>();
        private final List<RuntimeWorkItem> completed = new ArrayList<>();
        private final List<Failure> failures = new ArrayList<>();

        @Override
        public void submit(RuntimeWorkItemRequest request) {
        }

        @Override
        public void submit(RuntimeWorkItemRequest request, Duration delay) {
        }

        @Override
        public void submitAfterCommit(RuntimeWorkItemRequest request) {
        }

        @Override
        public void submitAfterCommit(RuntimeWorkItemRequest request, Duration delay) {
        }

        @Override
        public List<RuntimeWorkItem> claim(String taskType, String ownerId, int limit, Duration visibilityTimeout) {
            if (!SearchSyncWorkTypes.RAW.equals(taskType)) {
                return List.of();
            }
            return List.copyOf(rawItems);
        }

        @Override
        public boolean complete(RuntimeWorkItem item) {
            completed.add(item);
            return true;
        }

        @Override
        public boolean fail(RuntimeWorkItem item, boolean retryable, Duration retryDelay, String reason) {
            failures.add(new Failure(item, retryable, retryDelay, reason));
            return true;
        }

        @Override
        public int requeueExpired(String taskType, int limit) {
            return 0;
        }

        @Override
        public RuntimeWorkQueueCounts counts(String taskType) {
            return new RuntimeWorkQueueCounts(rawItems.size(), 0, failures.size());
        }
    }

    private record Failure(RuntimeWorkItem item, boolean retryable, Duration retryDelay, String reason) {
    }
}
