package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NormalizeVideoWorkQueueWorkerTest {

    @Test
    void drainsFullBatchInWavesWhileReservingOneDatabaseConnection() {
        var queue = mock(RuntimeWorkQueue.class);
        var service = mock(RawVideoNormalizationService.class);
        var items = new ConcurrentLinkedQueue<RuntimeWorkItem>();
        for (int i = 0; i < 5; i++) items.add(item(UUID.randomUUID().toString()));
        var largestClaim = new AtomicInteger();
        when(queue.claim(anyString(), anyString(), anyInt(), any())).thenAnswer(invocation -> {
            int capacity = invocation.getArgument(2);
            largestClaim.accumulateAndGet(capacity, Math::max);
            var result = new ArrayList<RuntimeWorkItem>();
            while (result.size() < capacity && !items.isEmpty()) result.add(items.remove());
            return result;
        });
        when(service.normalize(any(), any())).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().isVirtual()).isTrue();
            return RawVideoNormalizationService.NormalizationResult.ready(invocation.getArgument(0));
        });
        var worker = worker(queue, service);
        try {
            assertThat(worker.runOnce()).isEqualTo(5);
            assertThat(largestClaim.get()).isEqualTo(1);
            org.mockito.Mockito.verify(queue, org.mockito.Mockito.times(5)).complete(any());
        } finally {
            worker.shutdown();
        }
    }

    @Test
    void permanentNormalizationFailureIsDeadLetteredInsteadOfReportedAsSuccess() {
        var queue = mock(RuntimeWorkQueue.class);
        var service = mock(RawVideoNormalizationService.class);
        var task = item(UUID.randomUUID().toString());
        when(queue.claim(anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(task), List.of());
        when(service.normalize(any(), any())).thenReturn(new RawVideoNormalizationService.NormalizationResult(
                UUID.fromString(task.aggregateId()), "PERMANENT_FAILURE", "INVALID_RAW_METADATA"));
        var worker = worker(queue, service);
        try {
            worker.runOnce();
            org.mockito.Mockito.verify(queue).fail(task, false, Duration.ZERO, "INVALID_RAW_METADATA");
            org.mockito.Mockito.verify(queue, org.mockito.Mockito.never()).complete(any());
        } finally {
            worker.shutdown();
        }
    }

    private NormalizeVideoWorkQueueWorker worker(RuntimeWorkQueue queue, RawVideoNormalizationService service) {
        return new NormalizeVideoWorkQueueWorker(queue, service, mock(NormalizationConfigValues.class),
                WorkerJobAuditWriter.noop(), "test", "worker", 5, 8, 2, Duration.ofMinutes(10));
    }

    private RuntimeWorkItem item(String aggregateId) {
        Instant now = Instant.now();
        return new RuntimeWorkItem("pipeline.normalize-video", UUID.randomUUID().toString(), "submission",
                aggregateId, "v1", "", "PROCESSING", 1, now, now, now, now.plusSeconds(600), "worker", "");
    }
}
