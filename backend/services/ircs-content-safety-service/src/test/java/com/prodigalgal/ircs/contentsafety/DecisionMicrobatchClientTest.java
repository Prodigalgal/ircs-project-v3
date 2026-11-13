package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionMicrobatchClientTest {
    @Test
    void separateHttpCallsEnqueueAndResultsAreMappedToCurrentCallerIdentity() {
        var queue = mock(DecisionQueueStore.class);
        var settings = new DecisionSettings(); var properties = new ContentSafetyProperties();
        var client = new DecisionMicrobatchClient(queue, settings, properties);
        var first = GoogleDecisionNsfwClientTest.item("ordinary documentary");
        var sameEvidence = GoogleDecisionNsfwClientTest.item("ordinary documentary");
        when(queue.submit(anyString(), any())).thenReturn(new DecisionQueueStore.Lookup(null, 2, 0, false));
        assertThat(client.classify(List.of(first)).get(first.id()).raw()).containsEntry("reason", "QUEUE_PENDING");
        assertThat(client.classify(List.of(sameEvidence)).get(sameEvidence.id()).available()).isFalse();
        assertThat(client.key(first)).isEqualTo(client.key(sameEvidence));
        var safe = new com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult(
                DecisionSettings.MODEL, "v1beta", true, 0.01, "SAFE", Map.of());
        when(queue.submit(anyString(), any())).thenReturn(new DecisionQueueStore.Lookup(safe, 86400, 0, false));
        assertThat(client.classify(List.of(sameEvidence))).containsEntry(sameEvidence.id(), safe).doesNotContainKey(first.id());
    }

    @Test
    void fullEvidencePolicyAndThresholdsFenceCachedPredictions() {
        var settings = new DecisionSettings(); settings.setMaxTextLength(10);
        var properties = new ContentSafetyProperties();
        var client = new DecisionMicrobatchClient(mock(DecisionQueueStore.class), settings, properties);
        var first = GoogleDecisionNsfwClientTest.item("a".repeat(20)+"ordinary");
        assertThat(client.key(first)).isNotEqualTo(client.key(GoogleDecisionNsfwClientTest.item("a".repeat(20)+"pornography")));
        String before = client.key(first);
        properties.adult().model().setAdultThreshold(0.9);
        assertThat(client.key(first)).isNotEqualTo(before);
    }

    @Test
    void backpressureAndStoreOutageStayPendingInsteadOfSafe() {
        var queue = mock(DecisionQueueStore.class);
        var client = new DecisionMicrobatchClient(queue, new DecisionSettings(), new ContentSafetyProperties());
        var item = GoogleDecisionNsfwClientTest.item("ordinary documentary");
        when(queue.submit(anyString(), any())).thenReturn(new DecisionQueueStore.Lookup(null, 12, 0, true));
        var result = client.classify(List.of(item)).get(item.id());
        assertThat(result.available()).isFalse();
        assertThat(result.raw()).containsEntry("reason", "QUEUE_FULL").containsEntry("retryAfterSeconds", 12L);
        when(queue.submit(anyString(), any())).thenThrow(new DecisionUnavailableException("QUEUE_UNAVAILABLE", 2));
        assertThat(client.classify(List.of(item)).get(item.id()).raw())
                .containsEntry("reason", "QUEUE_PENDING").containsEntry("upstreamReason", "QUEUE_UNAVAILABLE");
    }
}
