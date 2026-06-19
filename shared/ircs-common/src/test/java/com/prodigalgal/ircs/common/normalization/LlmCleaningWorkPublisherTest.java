package com.prodigalgal.ircs.common.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkItemRequest;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LlmCleaningWorkPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void skipsSubmitWhenGateIsDisabled() {
        CapturingRuntimeWorkQueue queue = new CapturingRuntimeWorkQueue();
        LlmCleaningWorkPublisher publisher = publisher(queue, () -> false, true);

        publisher.enqueue("actor", UUID.randomUUID(), " raw ", "test");

        assertThat(queue.requests).isEmpty();
    }

    @Test
    void submitsWhenGateIsEnabled() throws Exception {
        CapturingRuntimeWorkQueue queue = new CapturingRuntimeWorkQueue();
        UUID rawId = UUID.randomUUID();
        LlmCleaningWorkPublisher publisher = publisher(queue, () -> true, false);

        publisher.enqueue("actor", rawId, " raw ", "test");

        RuntimeWorkItemRequest request = queue.requests.getFirst();
        assertThat(request.taskType()).isEqualTo(LlmCleaningWorkTypes.RAW_TERM);
        assertThat(request.taskId()).isEqualTo("actor:" + rawId);
        LlmCleaningWorkPayload payload = objectMapper.readValue(request.payload(), LlmCleaningWorkPayload.class);
        assertThat(payload.kind()).isEqualTo("actor");
        assertThat(payload.rawId()).isEqualTo(rawId);
        assertThat(payload.rawValue()).isEqualTo("raw");
    }

    @Test
    void fallsBackToDeploymentSwitchWhenNoGateExists() {
        CapturingRuntimeWorkQueue queue = new CapturingRuntimeWorkQueue();
        LlmCleaningWorkPublisher publisher = publisher(queue, null, false);

        publisher.enqueue("director", UUID.randomUUID(), " raw ", "test");

        assertThat(queue.requests).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private LlmCleaningWorkPublisher publisher(
            RuntimeWorkQueue queue,
            LlmCleaningWorkGate gate,
            boolean enabledByDeployment) {
        ObjectProvider<LlmCleaningWorkGate> gateProvider = org.mockito.Mockito.mock(ObjectProvider.class);
        when(gateProvider.getIfAvailable()).thenReturn(gate);
        return new LlmCleaningWorkPublisher(
                queue,
                objectMapper,
                gateProvider,
                "test-service",
                enabledByDeployment);
    }

    private static class CapturingRuntimeWorkQueue implements RuntimeWorkQueue {
        private final List<RuntimeWorkItemRequest> requests = new ArrayList<>();

        @Override
        public void submit(RuntimeWorkItemRequest request) {
            requests.add(request);
        }

        @Override
        public void submit(RuntimeWorkItemRequest request, Duration delay) {
            requests.add(request);
        }

        @Override
        public void submitAfterCommit(RuntimeWorkItemRequest request) {
            requests.add(request);
        }

        @Override
        public void submitAfterCommit(RuntimeWorkItemRequest request, Duration delay) {
            requests.add(request);
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
        public RuntimeWorkQueueCounts counts(String taskType) {
            return new RuntimeWorkQueueCounts(0, 0, 0);
        }
    }
}
