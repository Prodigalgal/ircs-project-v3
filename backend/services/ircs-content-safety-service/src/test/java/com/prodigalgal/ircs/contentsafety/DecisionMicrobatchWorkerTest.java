package com.prodigalgal.ircs.contentsafety;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DecisionMicrobatchWorkerTest {
    @Test
    void capacityWaitKeepsJobsQueuedWithoutClaimOrCredentialAttempt() {
        var queue = mock(DecisionQueueStore.class); var quota = mock(DecisionQuotaStore.class);
        var google = mock(GoogleDecisionNsfwClient.class); var settings = new DecisionSettings();
        var properties = new ContentSafetyProperties(); properties.adult().model().setEnabled(true);
        properties.adult().model().setProvider(DecisionSettings.PROVIDER);
        var job = new DecisionQueueStore.Job("test", GoogleDecisionNsfwClientTest.item("ordinary documentary"), 0);
        when(queue.snapshot()).thenReturn(new DecisionQueueStore.Snapshot(1, 0, 1));
        when(queue.ready()).thenReturn(List.of(job)); when(google.requestBody(anyList())).thenReturn("{}");
        when(quota.reserve(anyLong())).thenThrow(new DecisionUnavailableException("DAILY_LIMIT", 3600));
        var worker = new DecisionMicrobatchWorker(queue, quota, google, settings, properties, new SimpleMeterRegistry());
        try { worker.tick(); }
        finally { worker.close(); }
        verify(queue).deferPending(List.of(job), 3600, "DAILY_LIMIT");
        verify(queue, never()).claim(anyList());
        verify(queue, never()).markSent(any());
        verify(google, never()).classifyReserved(anyList(), any());
    }

    @Test
    void tokenWaitForOlderLargeItemDoesNotStarveSmallerEligibleItem() {
        var queue = mock(DecisionQueueStore.class); var quota = mock(DecisionQuotaStore.class);
        var google = mock(GoogleDecisionNsfwClient.class); var settings = new DecisionSettings();
        var properties = new ContentSafetyProperties(); properties.adult().model().setEnabled(true);
        properties.adult().model().setProvider(DecisionSettings.PROVIDER);
        var large = new DecisionQueueStore.Job("large", GoogleDecisionNsfwClientTest.item("large"), 0);
        var small = new DecisionQueueStore.Job("small", GoogleDecisionNsfwClientTest.item("small"), 1);
        when(queue.snapshot()).thenReturn(new DecisionQueueStore.Snapshot(2, 0, 1));
        when(queue.ready()).thenReturn(List.of(large, small));
        when(google.requestBody(anyList())).thenAnswer(call -> {
            List<com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem> items = call.getArgument(0);
            return items.stream().anyMatch(item -> item.id().equals(large.item().id())) ? "x".repeat(6000) : "{}";
        });
        when(quota.reserve(anyLong())).thenAnswer(call -> {
            long estimate = call.getArgument(0);
            if (estimate > 8000) { throw new DecisionUnavailableException("TOKEN_LIMITED", 60); }
            return new DecisionQuotaStore.Reservation("reserved", "2026-11-13", estimate);
        });
        when(queue.claim(anyList())).thenAnswer(call -> new DecisionQueueStore.Claim("owner", call.getArgument(0)));
        when(queue.markSent(any())).thenReturn(true);
        when(google.classifyReserved(anyList(), any())).thenReturn(Map.of(small.item().id(),
                new com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult(
                        DecisionSettings.MODEL, "v1beta", true, 0.01, "SAFE", Map.of())));
        var worker = new DecisionMicrobatchWorker(queue, quota, google, settings, properties, new SimpleMeterRegistry());
        try { worker.tick(); }
        finally { worker.close(); }
        var claimed = org.mockito.ArgumentCaptor.forClass(DecisionQueueStore.Claim.class);
        verify(queue).markSent(claimed.capture());
        assertThat(claimed.getValue().jobs()).containsExactly(small);
        verify(queue, never()).deferPending(anyList(), anyLong(), any());
        verify(google).classifyReserved(eq(List.of(small.item())), any());
    }
}
