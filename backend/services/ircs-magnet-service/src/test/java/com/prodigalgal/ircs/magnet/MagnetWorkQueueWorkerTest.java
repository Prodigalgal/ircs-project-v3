package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MagnetWorkQueueWorkerTest {

    private final RuntimeWorkQueue queue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
    private final MagnetWorkLeaseStore leaseStore = org.mockito.Mockito.mock(MagnetWorkLeaseStore.class);
    private final MagnetQueryService queryService = org.mockito.Mockito.mock(MagnetQueryService.class);
    private final UUID jobId = UUID.randomUUID();
    private final UUID unifiedVideoId = UUID.randomUUID();
    private final RuntimeWorkItem item = item();
    private final MagnetWorkQueueWorker worker = new MagnetWorkQueueWorker(
            queue, new ObjectMapper(), queryService, leaseStore, emptyRuntimeConfigProvider());

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(worker, "enabledByDeployment", true);
        ReflectionTestUtils.setField(worker, "batchSizeByDeployment", 5);
        ReflectionTestUtils.setField(worker, "visibilitySecondsByDeployment", 30);
        ReflectionTestUtils.setField(worker, "maxRetriesByDeployment", 3);
        ReflectionTestUtils.setField(worker, "maxBackoffSecondsByDeployment", 30);
        ReflectionTestUtils.setField(worker, "workerId", "magnet-worker-test");
        ReflectionTestUtils.setField(worker, "applicationName", "ircs-magnet-service");
        when(queue.claim(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn(List.of(item));
        when(leaseStore.renew(any(), any())).thenReturn(true);
        when(queue.complete(item)).thenReturn(true);
        when(queue.fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString())).thenReturn(true);
    }

    @AfterEach
    void shutDownWorkerExecutor() {
        worker.shutdownTriggerExecutor();
    }

    @Test
    void executesWithRetryAllowanceAndCompletesOnlyWhileClaimIsOwned() {
        int processed = worker.runOnce();

        assertEquals(1, processed);
        verify(queryService).executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true);
        verify(queue).complete(item);
        verify(queue, never()).fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString());
    }

    @Test
    void lostClaimStopsBeforeBusinessExecutionAndQueueTransitions() {
        when(leaseStore.renew(any(), any())).thenReturn(false);

        assertEquals(0, worker.runOnce());

        verify(queryService, never()).executeQueuedSearch(any(), any(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(queue, never()).complete(any());
        verify(queue, never()).fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString());
    }

    @Test
    void unexpectedFailureRetriesOnlyWhileConfiguredAttemptsRemain() {
        when(queryService.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true))
                .thenThrow(new IllegalStateException("transient"));

        assertEquals(0, worker.runOnce());

        verify(queue).fail(item, true, Duration.ofSeconds(2), "MAGNET_EXECUTION_FAILURE:IllegalStateException");
        verify(queue, never()).complete(item);
    }

    @Test
    void jobBusyDefersQueueWorkWithoutConsumingFailureAttempt() {
        when(queryService.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true))
                .thenThrow(new MagnetSearchJobCoordinator.MagnetJobBusyException(
                        "MAGNET_SEARCH_JOB_ALREADY_RUNNING"));
        when(leaseStore.defer(item, Duration.ofSeconds(30), "MAGNET_SEARCH_JOB_ALREADY_RUNNING"))
                .thenReturn(true);

        assertEquals(0, worker.runOnce());

        verify(leaseStore).defer(item, Duration.ofSeconds(30), "MAGNET_SEARCH_JOB_ALREADY_RUNNING");
        verify(queue, never()).fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString());
        verify(queue, never()).complete(item);
    }

    @Test
    void lostBusinessLeaseDefersQueueWorkWithoutRepeatedlyFailingJob() {
        when(queryService.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true))
                .thenThrow(new MagnetSearchJobCoordinator.MagnetLeaseLostException("MAGNET_SEARCH_JOB_LEASE_LOST"));
        when(leaseStore.defer(item, Duration.ofSeconds(30), "MAGNET_SEARCH_JOB_LEASE_LOST"))
                .thenReturn(true);

        assertEquals(0, worker.runOnce());

        verify(leaseStore).defer(item, Duration.ofSeconds(30), "MAGNET_SEARCH_JOB_LEASE_LOST");
        verify(queue, never()).fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString());
        verify(queue, never()).complete(item);
    }

    private RuntimeWorkItem item() {
        Instant now = Instant.now();
        MagnetSearchWorkPayload payload = new MagnetSearchWorkPayload(jobId, unifiedVideoId, "AUTO_SCHEDULED");
        String serialized;
        try {
            serialized = new ObjectMapper().writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return new RuntimeWorkItem(
                MagnetWorkTypes.SEARCH,
                MagnetWorkTypes.searchTaskId(jobId),
                UUID.randomUUID().toString(),
                unifiedVideoId.toString(),
                null,
                serialized,
                "PROCESSING",
                1,
                now,
                now,
                now,
                now.plusSeconds(60),
                "magnet-worker-test",
                null);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<com.prodigalgal.ircs.common.config.RuntimeConfigService> emptyRuntimeConfigProvider() {
        ObjectProvider<com.prodigalgal.ircs.common.config.RuntimeConfigService> provider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
