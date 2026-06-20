package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.search.SearchSyncWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class RuntimeWorkExpiredInflightReaperTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RuntimeWorkQueue> runtimeWorkQueueProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<com.prodigalgal.ircs.common.lease.ClusterLeaseService> leaseServiceProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);

    @Test
    void requeuesExpiredInflightAcrossRuntimeWorkTypes() throws Exception {
        FakeRuntimeWorkQueue queue = new FakeRuntimeWorkQueue();
        when(runtimeWorkQueueProvider.getIfAvailable()).thenReturn(queue);
        when(leaseServiceProvider.getIfUnique()).thenReturn(null);
        RuntimeWorkExpiredInflightReaper reaper =
                new RuntimeWorkExpiredInflightReaper(runtimeWorkQueueProvider, leaseServiceProvider);
        set(reaper, "enabled", true);
        set(reaper, "batchSize", 50);
        set(reaper, "leaseTtl", Duration.ofSeconds(30));
        set(reaper, "configuredTaskTypes", "");

        int requeued = reaper.runOnce();

        assertThat(requeued).isEqualTo(2);
        assertThat(queue.requeueAttempts).contains(SearchSyncWorkTypes.RAW, SearchSyncWorkTypes.UNIFIED);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeRuntimeWorkQueue implements RuntimeWorkQueue {
        private final List<String> requeueAttempts = new ArrayList<>();

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
            return List.of();
        }

        @Override
        public boolean complete(RuntimeWorkItem item) {
            return true;
        }

        @Override
        public boolean fail(RuntimeWorkItem item, boolean retryable, Duration retryDelay, String reason) {
            return true;
        }

        @Override
        public int requeueExpired(String taskType, int limit) {
            requeueAttempts.add(taskType);
            return SearchSyncWorkTypes.UNIFIED.equals(taskType) ? 2 : 0;
        }

        @Override
        public RuntimeWorkQueueCounts counts(String taskType) {
            return new RuntimeWorkQueueCounts(0, 0, 0);
        }
    }
}
