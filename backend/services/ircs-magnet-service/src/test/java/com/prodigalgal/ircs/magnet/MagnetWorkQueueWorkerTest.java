package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MagnetWorkQueueWorkerTest {

    private final RuntimeWorkQueue queue = org.mockito.Mockito.mock(RuntimeWorkQueue.class);
    private final MagnetWorkLeaseStore leaseStore = org.mockito.Mockito.mock(MagnetWorkLeaseStore.class);
    private final MagnetQueryService queryService = org.mockito.Mockito.mock(MagnetQueryService.class);
    private final UUID jobId = UUID.randomUUID();
    private final UUID unifiedVideoId = UUID.randomUUID();
    private final RuntimeWorkItem item = item(jobId, unifiedVideoId);
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
        when(queue.claim(anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(item));
        when(leaseStore.renew(any(), any())).thenReturn(true);
        when(queue.complete(item)).thenReturn(true);
        when(queue.fail(any(), org.mockito.ArgumentMatchers.anyBoolean(), any(), anyString())).thenReturn(true);
    }

    @AfterEach
    void shutDownWorkerExecutor() {
        worker.shutdownExecutors();
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
    void lostClaimDoesNotPreventAnotherTaskFromFinishing() {
        UUID secondJob = UUID.randomUUID();
        UUID secondVideo = UUID.randomUUID();
        RuntimeWorkItem second = item(secondJob, secondVideo);
        when(queue.claim(anyString(), anyString(), eq(2), any())).thenReturn(List.of(item, second));
        when(leaseStore.renew(eq(item), any())).thenReturn(false);
        when(queue.complete(second)).thenReturn(true);

        assertEquals(1, worker.runOnce());

        verify(queryService, never()).executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true);
        verify(queryService).executeQueuedSearch(secondJob, secondVideo, "AUTO_SCHEDULED", true);
        verify(queue).complete(second);
        verify(queue, never()).complete(item);
    }

    @Test
    @Timeout(20)
    void fillsFreeSlotsWithVirtualThreadsAndKeepsOneDatabaseConnectionOutsideWorkerBudget() throws Exception {
        ReflectionTestUtils.setField(worker, "parallelismByDeployment", 8);
        ReflectionTestUtils.setField(worker, "databasePoolSize", 3);
        UUID secondJob = UUID.randomUUID();
        UUID secondVideo = UUID.randomUUID();
        RuntimeWorkItem second = item(secondJob, secondVideo);
        RuntimeWorkItem third = item(UUID.randomUUID(), UUID.randomUUID());
        when(queue.claim(anyString(), anyString(), eq(2), any())).thenReturn(List.of(item));
        when(queue.claim(anyString(), anyString(), eq(1), any())).thenReturn(List.of(second), List.of(third));
        when(queue.complete(second)).thenReturn(true);
        when(queue.complete(third)).thenReturn(true);

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch allowFirst = new CountDownLatch(1);
        CountDownLatch allowSecond = new CountDownLatch(1);
        when(queryService.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true))
                .thenAnswer(call -> {
                    assertTrue(Thread.currentThread().isVirtual());
                    firstStarted.countDown();
                    assertTrue(allowFirst.await(8, TimeUnit.SECONDS));
                    return null;
                });
        when(queryService.executeQueuedSearch(secondJob, secondVideo, "AUTO_SCHEDULED", true))
                .thenAnswer(call -> {
                    assertTrue(Thread.currentThread().isVirtual());
                    secondStarted.countDown();
                    assertTrue(allowSecond.await(8, TimeUnit.SECONDS));
                    return null;
                });

        try (var polling = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var firstPoll = polling.submit(worker::runOnce);
                assertTrue(firstStarted.await(4, TimeUnit.SECONDS));
                var secondPoll = polling.submit(worker::runOnce);
                assertTrue(secondStarted.await(4, TimeUnit.SECONDS));
                assertEquals(0, worker.runOnce());
                verify(queue, times(2)).claim(anyString(), anyString(), anyInt(), any());

                allowFirst.countDown();
                assertEquals(1, firstPoll.get(4, TimeUnit.SECONDS));
                assertEquals(1, worker.runOnce());
                verify(queue).complete(third);

                allowSecond.countDown();
                assertEquals(1, secondPoll.get(4, TimeUnit.SECONDS));
            } finally {
                allowFirst.countDown();
                allowSecond.countDown();
            }
        }
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

    private RuntimeWorkItem item(UUID jobId, UUID unifiedVideoId) {
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
