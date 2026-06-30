package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.audit.WorkerJobAuditWriter;
import com.prodigalgal.ircs.common.lease.ClusterLease;
import com.prodigalgal.ircs.common.lease.ClusterLeaseService;
import com.prodigalgal.ircs.common.pipeline.PipelineValkeyQueue;
import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class PendingNormalizationWatchdogTest {

    @Mock
    private RawVideoNormalizationRepository repository;

    @Mock
    private PipelineValkeyQueue pipelineQueue;

    @Mock
    private NormalizationConfigValues configValues;

    @Mock
    private ClusterLeaseService clusterLeaseService;

    @Mock
    private WorkerJobAuditWriter auditWriter;

    private ClusterLease lease;
    private PendingNormalizationWatchdog watchdog;

    @BeforeEach
    void setUp() {
        lease = new ClusterLease(
                PendingNormalizationWatchdog.LEASE_NAME,
                "worker-a",
                "lease-token",
                Instant.now(),
                Instant.now().plusSeconds(30));
        watchdog = new PendingNormalizationWatchdog(
                repository,
                pipelineQueue,
                configValues,
                provider(clusterLeaseService),
                auditWriter,
                "ircs-normalization-worker",
                "worker-a");
    }

    @Test
    void submitsPendingRowsWhenNoOpenPipelineTaskExists() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(configValues.pendingWatchdogEnabled()).thenReturn(true);
        when(configValues.pendingWatchdogLeaseTtl()).thenReturn(Duration.ofSeconds(30));
        when(configValues.pendingWatchdogMinPendingAge()).thenReturn(Duration.ofMinutes(5));
        when(configValues.pendingWatchdogBatchSize()).thenReturn(50);
        when(clusterLeaseService.tryAcquire(
                        PendingNormalizationWatchdog.LEASE_NAME,
                        "worker-a",
                        Duration.ofSeconds(30)))
                .thenReturn(Optional.of(lease));
        when(repository.findPendingNormalizationQueueItems(any(Instant.class), org.mockito.ArgumentMatchers.eq(50)))
                .thenReturn(List.of(
                        new RawVideoNormalizationRepository.RawVideoQueueItem(first, "hash-1"),
                        new RawVideoNormalizationRepository.RawVideoQueueItem(second, "hash-2")));
        when(pipelineQueue.hasOpenTask(any(PipelineCommand.class))).thenReturn(false);

        int submitted = watchdog.reconcilePending();

        assertEquals(2, submitted);
        ArgumentCaptor<PipelineCommand> commandCaptor = ArgumentCaptor.forClass(PipelineCommand.class);
        verify(pipelineQueue, org.mockito.Mockito.times(2)).submit(commandCaptor.capture());
        assertEquals(List.of(first, second), commandCaptor.getAllValues().stream()
                .map(PipelineCommand::rawVideoId)
                .toList());
        verify(clusterLeaseService).release(lease);
        verify(auditWriter).record(any());
    }

    @Test
    void skipsRowsThatAlreadyHavePendingOrInflightTask() {
        UUID rawVideoId = UUID.randomUUID();
        when(configValues.pendingWatchdogEnabled()).thenReturn(true);
        when(configValues.pendingWatchdogLeaseTtl()).thenReturn(Duration.ofSeconds(30));
        when(configValues.pendingWatchdogMinPendingAge()).thenReturn(Duration.ofMinutes(5));
        when(configValues.pendingWatchdogBatchSize()).thenReturn(50);
        when(clusterLeaseService.tryAcquire(
                        PendingNormalizationWatchdog.LEASE_NAME,
                        "worker-a",
                        Duration.ofSeconds(30)))
                .thenReturn(Optional.of(lease));
        when(repository.findPendingNormalizationQueueItems(any(Instant.class), org.mockito.ArgumentMatchers.eq(50)))
                .thenReturn(List.of(new RawVideoNormalizationRepository.RawVideoQueueItem(rawVideoId, "hash-1")));
        when(pipelineQueue.hasOpenTask(any(PipelineCommand.class))).thenReturn(true);

        int submitted = watchdog.reconcilePending();

        assertEquals(0, submitted);
        verify(pipelineQueue, never()).submit(any(PipelineCommand.class));
        verify(auditWriter, never()).record(any());
    }

    @Test
    void disabledWatchdogDoesNotAcquireLeaseOrQueryDatabase() {
        when(configValues.pendingWatchdogEnabled()).thenReturn(false);

        int submitted = watchdog.reconcilePending();

        assertEquals(0, submitted);
        verify(clusterLeaseService, never()).tryAcquire(any(), any(), any());
        verify(repository, never()).findPendingNormalizationQueueItems(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void heldLeaseSkipsThisRound() {
        when(configValues.pendingWatchdogEnabled()).thenReturn(true);
        when(configValues.pendingWatchdogLeaseTtl()).thenReturn(Duration.ofSeconds(30));
        when(clusterLeaseService.tryAcquire(
                        PendingNormalizationWatchdog.LEASE_NAME,
                        "worker-a",
                        Duration.ofSeconds(30)))
                .thenReturn(Optional.empty());

        int submitted = watchdog.reconcilePending();

        assertEquals(0, submitted);
        verify(repository, never()).findPendingNormalizationQueueItems(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private static ObjectProvider<ClusterLeaseService> provider(ClusterLeaseService leaseService) {
        return new ObjectProvider<>() {
            @Override
            public ClusterLeaseService getObject() {
                return leaseService;
            }
        };
    }
}
