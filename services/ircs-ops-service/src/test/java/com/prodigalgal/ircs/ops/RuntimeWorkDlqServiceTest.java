package com.prodigalgal.ircs.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeWorkDlqServiceTest {

    @Test
    void requeueDelegatesToRuntimeWorkQueueWithoutProcessingPayload() {
        FakeRuntimeWorkQueue queue = new FakeRuntimeWorkQueue();
        RuntimeWorkDlqService service = new RuntimeWorkDlqService(queue);

        RuntimeWorkDlqActionResponse response = service.requeue(AggregationWorkTypes.RAW_VIDEO, 3, 5);

        assertThat(response.taskType()).isEqualTo(AggregationWorkTypes.RAW_VIDEO);
        assertThat(response.affected()).isEqualTo(3);
        assertThat(queue.requeueRequests).containsExactly(new RequeueRequest(AggregationWorkTypes.RAW_VIDEO, 3, 5));
    }

    @Test
    void listsKnownRuntimeDlqsWithSamplesAndConsumerCounts() {
        FakeRuntimeWorkQueue queue = new FakeRuntimeWorkQueue();
        queue.sample = List.of(item(AggregationWorkTypes.RAW_VIDEO, "task-1"));
        RuntimeWorkDlqService service = new RuntimeWorkDlqService(queue);

        List<RuntimeWorkDlqQueueResponse> responses = service.listQueues(5);

        RuntimeWorkDlqQueueResponse aggregation = responses.stream()
                .filter(item -> AggregationWorkTypes.RAW_VIDEO.equals(item.taskType()))
                .findFirst()
                .orElseThrow();
        assertThat(aggregation.dlq()).isEqualTo(7);
        assertThat(aggregation.dlqConsumers()).isEqualTo(1);
        assertThat(aggregation.samples()).hasSize(1);
    }

    private static RuntimeWorkItem item(String taskType, String taskId) {
        return new RuntimeWorkItem(
                taskType,
                taskId,
                "submission-1",
                "aggregate-1",
                "v1",
                "{\"ok\":true}",
                "PERMANENT_FAILURE",
                8,
                Instant.parse("2026-06-20T00:00:00Z"),
                Instant.parse("2026-06-20T00:01:00Z"),
                Instant.parse("2026-06-20T00:00:00Z"),
                Instant.parse("2026-06-20T00:01:00Z"),
                "",
                "boom");
    }

    private static class FakeRuntimeWorkQueue implements RuntimeWorkQueue {
        private final List<RequeueRequest> requeueRequests = new ArrayList<>();
        private List<RuntimeWorkItem> sample = List.of();

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
            return 0;
        }

        @Override
        public List<RuntimeWorkItem> sampleDlq(String taskType, int limit) {
            return sample;
        }

        @Override
        public int requeueDlq(String taskType, int limit, int maxReplayAttempts) {
            requeueRequests.add(new RequeueRequest(taskType, limit, maxReplayAttempts));
            return limit;
        }

        @Override
        public RuntimeWorkQueueCounts counts(String taskType) {
            return new RuntimeWorkQueueCounts(1, 2, 7);
        }

        @Override
        public long dlqConsumerCount(String taskType) {
            return 1;
        }
    }

    private record RequeueRequest(String taskType, int limit, int maxReplayAttempts) {
    }
}
