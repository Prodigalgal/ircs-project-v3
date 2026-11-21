package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AggregationReviewRecoveryServiceTest {
    private final AggregationEvaluationService evaluation = mock(AggregationEvaluationService.class);
    private final AggregationWorkDeferrer queue = mock(AggregationWorkDeferrer.class);
    private final AggregationReviewRecoveryService service = new AggregationReviewRecoveryService(evaluation, queue);
    private final UUID rawId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final AggregationWorkDeferrer.ReviewedWork reviewed =
            new AggregationWorkDeferrer.ReviewedWork(rawId, "submission-1", "updated-1", "IDENTITY_CONFLICT");

    @BeforeEach
    void setUp() {
        when(queue.findReviewedWork(rawId)).thenReturn(Optional.of(reviewed));
    }

    @Test
    void dryRunReportsReadyWithoutRequeueAndExecutionRequeuesApprovedIdentityOnce() {
        when(evaluation.evaluate(List.of(rawId))).thenReturn(response(decision("MATCH", targetId, null, List.of())));
        when(queue.requeueReviewedWork(reviewed)).thenReturn(true);

        var preview = service.recover(List.of(rawId, rawId), false);
        assertThat(preview.execute()).isFalse();
        assertThat(preview.decisions()).containsExactly(
                new AggregationReviewRecoveryService.RecoveryDecision(rawId, targetId, "READY", "IDENTITY_CONFLICT"));
        verify(queue, never()).requeueReviewedWork(reviewed);
        verify(evaluation).evaluate(List.of(rawId));

        var executed = service.recover(List.of(rawId), true);
        assertThat(executed.execute()).isTrue();
        assertThat(executed.decisions()).containsExactly(
                new AggregationReviewRecoveryService.RecoveryDecision(rawId, targetId, "REQUEUED", "IDENTITY_CONFLICT"));
        verify(queue).requeueReviewedWork(reviewed);
    }

    @Test
    void stillAmbiguousOrChangedBindingIsNotRequeued() {
        UUID otherId = UUID.randomUUID();
        when(queue.findReviewedWork(otherId)).thenReturn(Optional.of(new AggregationWorkDeferrer.ReviewedWork(
                otherId, "submission-2", "updated-2", "MERGE_AMBIGUOUS")));
        when(evaluation.evaluate(List.of(rawId, otherId))).thenReturn(response(
                decision("AMBIGUOUS", targetId, null, List.of()),
                decision(otherId, "MATCH", targetId, UUID.randomUUID(), List.of())));

        var result = service.recover(List.of(rawId, otherId), true);

        assertThat(result.decisions()).extracting(AggregationReviewRecoveryService.RecoveryDecision::status)
                .containsExactly("STILL_REQUIRES_REVIEW", "STILL_REQUIRES_REVIEW");
        verify(queue, never()).requeueReviewedWork(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compatiblePendingBindingCanResumeWithoutChangingItsWork() {
        when(evaluation.evaluate(List.of(rawId))).thenReturn(response(
                decision("EXISTING_BINDING_COMPATIBLE", targetId, targetId, List.of())));
        when(queue.requeueReviewedWork(reviewed)).thenReturn(true);

        assertThat(service.recover(List.of(rawId), true).decisions()).containsExactly(
                new AggregationReviewRecoveryService.RecoveryDecision(rawId, targetId, "REQUEUED", "IDENTITY_CONFLICT"));
        verify(queue).requeueReviewedWork(reviewed);
    }

    @Test
    void invalidAndEmptyRequestsAreRejected() {
        assertThatThrownBy(() -> service.recover(List.of(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recover(java.util.Collections.singletonList(null), false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(evaluation, never()).evaluate(org.mockito.ArgumentMatchers.anyList());
    }

    private AggregationEvaluationService.EvaluationResponse response(
            AggregationEvaluationService.EvaluationDecision... decisions) {
        return new AggregationEvaluationService.EvaluationResponse(
                "SNAPSHOT_ONLY", Instant.parse("2026-11-21T00:00:00Z"), false, "test", List.of(decisions));
    }

    private AggregationEvaluationService.EvaluationDecision decision(
            String status, UUID target, UUID original, List<UUID> victims) {
        return decision(rawId, status, target, original, victims);
    }

    private AggregationEvaluationService.EvaluationDecision decision(
            UUID id, String status, UUID target, UUID original, List<UUID> victims) {
        return new AggregationEvaluationService.EvaluationDecision(id, original, target, victims, status, "review");
    }
}
