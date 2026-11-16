package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueue;
import com.prodigalgal.ircs.common.work.RuntimeWorkQueueCounts;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MagnetWorkPublisherTest {

    @org.junit.jupiter.api.AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void reportsPendingPlusInflightBacklog() {
        RuntimeWorkQueue queue = mock(RuntimeWorkQueue.class);
        when(queue.counts(MagnetWorkTypes.SEARCH)).thenReturn(new RuntimeWorkQueueCounts(7, 3, 2));
        MagnetWorkPublisher publisher = new MagnetWorkPublisher(queue, new ObjectMapper(), mock(MagnetWorkSubmissionStore.class));

        assertEquals(10, publisher.backlogDepth());
    }

    @Test
    void failsClosedWhenBacklogCountsAreUnavailable() {
        RuntimeWorkQueue queue = mock(RuntimeWorkQueue.class);
        when(queue.counts(MagnetWorkTypes.SEARCH)).thenReturn(null);
        MagnetWorkPublisher publisher = new MagnetWorkPublisher(queue, new ObjectMapper(), mock(MagnetWorkSubmissionStore.class));

        assertThrows(IllegalStateException.class, publisher::backlogDepth);
    }

    @Test
    void submitsOnlyAfterActiveTransactionCommits() {
        RuntimeWorkQueue queue = mock(RuntimeWorkQueue.class);
        MagnetWorkSubmissionStore submissions = mock(MagnetWorkSubmissionStore.class);
        MagnetWorkPublisher publisher = new MagnetWorkPublisher(queue, new ObjectMapper(), submissions);
        var job = job();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.enqueue(job);
        verify(submissions, never()).submitIfAbsent(org.mockito.ArgumentMatchers.any());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(submissions).submitIfAbsent(org.mockito.ArgumentMatchers.argThat(request ->
                request.taskId().equals(MagnetWorkTypes.searchTaskId(job.id()))
                        && request.payload().contains(job.id().toString())
                        && request.payload().contains(job.unifiedVideoId().toString())
                        && request.payload().contains("manual"))));
    }

    @Test
    void rollbackDoesNotSubmitWork() {
        RuntimeWorkQueue queue = mock(RuntimeWorkQueue.class);
        MagnetWorkSubmissionStore submissions = mock(MagnetWorkSubmissionStore.class);
        MagnetWorkPublisher publisher = new MagnetWorkPublisher(queue, new ObjectMapper(), submissions);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        publisher.enqueue(job());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(submissions, never()).submitIfAbsent(org.mockito.ArgumentMatchers.any());
    }

    private static MagnetSearchJobSummary job() {
        return new MagnetSearchJobSummary(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "manual", "PENDING",
                java.util.List.of(), java.util.List.of(), null, null, 0, 0, 0, null, null, java.util.List.of(), null, null);
    }
}
