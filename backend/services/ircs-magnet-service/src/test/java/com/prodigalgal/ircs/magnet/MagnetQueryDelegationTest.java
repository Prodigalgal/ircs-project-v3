package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MagnetQueryDelegationTest {

    private final JdbcMagnetRepository repository = mock(JdbcMagnetRepository.class);
    private final MagnetSearchExecutor searchExecutor = mock(MagnetSearchExecutor.class);
    private final MagnetQueryService service = new MagnetQueryService(
            repository, MagnetReadModelCache.disabled(), null, searchExecutor);

    @Test
    void manualSearchDelegatesToExecutorWithAdminTrigger() {
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "ADMIN_MANUAL");
        when(searchExecutor.searchNow(unifiedVideoId, "ADMIN_MANUAL")).thenReturn(expected);

        assertSame(expected, service.triggerUnifiedSearch(unifiedVideoId));

        verify(searchExecutor).searchNow(unifiedVideoId, "ADMIN_MANUAL");
    }

    @Test
    void automaticSearchDelegatesToExecutorWithScheduledTrigger() {
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "AUTO_SCHEDULED");
        when(searchExecutor.searchNow(unifiedVideoId, "AUTO_SCHEDULED")).thenReturn(expected);

        assertSame(expected, service.triggerAutomaticSearch(unifiedVideoId));

        verify(searchExecutor).searchNow(unifiedVideoId, "AUTO_SCHEDULED");
    }

    @Test
    void manualEnqueueDelegatesToExecutorWithAdminTrigger() {
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "ADMIN_MANUAL");
        when(searchExecutor.enqueue(unifiedVideoId, "ADMIN_MANUAL")).thenReturn(expected);

        assertSame(expected, service.enqueueUnifiedSearch(unifiedVideoId));

        verify(searchExecutor).enqueue(unifiedVideoId, "ADMIN_MANUAL");
    }

    @Test
    void automaticEnqueueDelegatesToExecutorWithScheduledTrigger() {
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "AUTO_SCHEDULED");
        when(searchExecutor.enqueue(unifiedVideoId, "AUTO_SCHEDULED")).thenReturn(expected);

        assertSame(expected, service.enqueueAutomaticSearch(unifiedVideoId));

        verify(searchExecutor).enqueue(unifiedVideoId, "AUTO_SCHEDULED");
    }

    @Test
    void queuedExecutionPassesRetryAllowedFalse() {
        UUID jobId = UUID.randomUUID();
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "ADMIN_MANUAL");
        when(searchExecutor.execute(jobId, unifiedVideoId, "ADMIN_MANUAL", false)).thenReturn(expected);

        assertSame(expected, service.executeQueuedSearch(jobId, unifiedVideoId, "ADMIN_MANUAL", false));

        verify(searchExecutor).execute(jobId, unifiedVideoId, "ADMIN_MANUAL", false);
    }

    @Test
    void queuedExecutionPassesRetryAllowedTrueAndLegacyOverloadDefaultsTrue() {
        UUID jobId = UUID.randomUUID();
        UUID unifiedVideoId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(unifiedVideoId, "AUTO_SCHEDULED");
        when(searchExecutor.execute(jobId, unifiedVideoId, "AUTO_SCHEDULED", true)).thenReturn(expected);

        assertSame(expected, service.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED", true));
        assertSame(expected, service.executeQueuedSearch(jobId, unifiedVideoId, "AUTO_SCHEDULED"));

        verify(searchExecutor, org.mockito.Mockito.times(2))
                .execute(jobId, unifiedVideoId, "AUTO_SCHEDULED", true);
    }

    private MagnetSearchJobSummary job(UUID unifiedVideoId, String trigger) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new MagnetSearchJobSummary(
                UUID.randomUUID(), unifiedVideoId, trigger, "PENDING", List.of(), List.of(),
                null, null, 0, 0, 0, null, null, List.of(), now, now);
    }
}
