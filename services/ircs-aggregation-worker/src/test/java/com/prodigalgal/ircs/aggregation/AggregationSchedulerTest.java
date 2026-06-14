package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditEvent;
import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AggregationSchedulerTest {

    private final AggregationService aggregationService = org.mockito.Mockito.mock(AggregationService.class);
    private final WorkerJobAuditWriter auditWriter = org.mockito.Mockito.mock(WorkerJobAuditWriter.class);
    private final AggregationConfigValues configValues = org.mockito.Mockito.mock(AggregationConfigValues.class);
    private final AggregationScheduler scheduler = new AggregationScheduler(aggregationService, auditWriter, configValues);

    @BeforeEach
    void setUp() {
        when(configValues.batchSize()).thenReturn(7);
        ReflectionTestUtils.setField(scheduler, "stuckTimeoutMinutes", 13);
    }

    @Test
    void processedBatchWritesSucceededAuditEvidence() {
        when(aggregationService.aggregateBatch(7)).thenReturn(3);

        scheduler.runBatch();

        verify(aggregationService, never()).resetStuckProcessing(13, 7);
        WorkerJobAuditEvent event = captureSingleAuditEvent();
        assertThat(event.jobType()).isEqualTo(AggregationScheduler.JOB_TYPE_SCHEDULER);
        assertThat(event.jobName()).isEqualTo(AggregationScheduler.JOB_NAME_AGGREGATION_BATCH);
        assertThat(event.status()).isEqualTo("succeeded");
        assertThat(event.duration()).isNotNull();
        assertThat(event.error()).isNull();
    }

    @Test
    void emptyBatchResetStuckPathWritesSucceededAuditEvidence() {
        when(aggregationService.aggregateBatch(7)).thenReturn(0);
        when(aggregationService.resetStuckProcessing(13, 7)).thenReturn(2);

        scheduler.runBatch();

        verify(aggregationService).resetStuckProcessing(13, 7);
        WorkerJobAuditEvent event = captureSingleAuditEvent();
        assertThat(event.jobType()).isEqualTo(AggregationScheduler.JOB_TYPE_SCHEDULER);
        assertThat(event.jobName()).isEqualTo(AggregationScheduler.JOB_NAME_RESET_STUCK);
        assertThat(event.status()).isEqualTo("succeeded");
        assertThat(event.duration()).isNotNull();
        assertThat(event.error()).isNull();
    }

    @Test
    void exceptionPathWritesFailedAuditEvidenceWithoutAbortingSchedulerLoop() {
        RuntimeException failure = new IllegalStateException("boom");
        when(aggregationService.aggregateBatch(7)).thenThrow(failure).thenReturn(1);

        assertDoesNotThrow(scheduler::runBatch);
        assertDoesNotThrow(scheduler::runBatch);

        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter, times(2)).record(captor.capture());
        WorkerJobAuditEvent failed = captor.getAllValues().get(0);
        WorkerJobAuditEvent recovered = captor.getAllValues().get(1);

        assertThat(failed.jobType()).isEqualTo(AggregationScheduler.JOB_TYPE_SCHEDULER);
        assertThat(failed.jobName()).isEqualTo(AggregationScheduler.JOB_NAME_AGGREGATION_BATCH);
        assertThat(failed.status()).isEqualTo("failed");
        assertThat(failed.duration()).isNotNull();
        assertThat(failed.error()).isSameAs(failure);

        assertThat(recovered.jobName()).isEqualTo(AggregationScheduler.JOB_NAME_AGGREGATION_BATCH);
        assertThat(recovered.status()).isEqualTo("succeeded");
        assertThat(recovered.error()).isNull();
    }

    @Test
    void runBatchUsesDbClaimInsteadOfOuterDistributedMutex() {
        when(aggregationService.aggregateBatch(7)).thenReturn(1);

        scheduler.runBatch();

        verify(aggregationService).aggregateBatch(7);
    }

    private WorkerJobAuditEvent captureSingleAuditEvent() {
        ArgumentCaptor<WorkerJobAuditEvent> captor = ArgumentCaptor.forClass(WorkerJobAuditEvent.class);
        verify(auditWriter).record(captor.capture());
        return captor.getValue();
    }
}
