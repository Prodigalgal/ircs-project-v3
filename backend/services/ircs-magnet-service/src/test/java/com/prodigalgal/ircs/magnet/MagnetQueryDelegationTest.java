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
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "ADMIN_MANUAL");
        when(searchExecutor.searchNow(catalogEntryId, "ADMIN_MANUAL")).thenReturn(expected);

        assertSame(expected, service.triggerUnifiedSearch(catalogEntryId));

        verify(searchExecutor).searchNow(catalogEntryId, "ADMIN_MANUAL");
    }

    @Test
    void automaticSearchDelegatesToExecutorWithScheduledTrigger() {
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "AUTO_SCHEDULED");
        when(searchExecutor.searchNow(catalogEntryId, "AUTO_SCHEDULED")).thenReturn(expected);

        assertSame(expected, service.triggerAutomaticSearch(catalogEntryId));

        verify(searchExecutor).searchNow(catalogEntryId, "AUTO_SCHEDULED");
    }

    @Test
    void manualEnqueueDelegatesToExecutorWithAdminTrigger() {
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "ADMIN_MANUAL");
        when(searchExecutor.enqueue(catalogEntryId, "ADMIN_MANUAL")).thenReturn(expected);

        assertSame(expected, service.enqueueUnifiedSearch(catalogEntryId));

        verify(searchExecutor).enqueue(catalogEntryId, "ADMIN_MANUAL");
    }

    @Test
    void automaticEnqueueDelegatesToExecutorWithScheduledTrigger() {
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "AUTO_SCHEDULED");
        when(searchExecutor.enqueue(catalogEntryId, "AUTO_SCHEDULED")).thenReturn(expected);

        assertSame(expected, service.enqueueAutomaticSearch(catalogEntryId));

        verify(searchExecutor).enqueue(catalogEntryId, "AUTO_SCHEDULED");
    }

    @Test
    void queuedExecutionPassesRetryAllowedFalse() {
        UUID jobId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "ADMIN_MANUAL");
        when(searchExecutor.execute(jobId, catalogEntryId, "ADMIN_MANUAL", false)).thenReturn(expected);

        assertSame(expected, service.executeQueuedSearch(jobId, catalogEntryId, "ADMIN_MANUAL", false));

        verify(searchExecutor).execute(jobId, catalogEntryId, "ADMIN_MANUAL", false);
    }

    @Test
    void queuedExecutionPassesRetryAllowedTrueAndLegacyOverloadDefaultsTrue() {
        UUID jobId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        MagnetSearchJobSummary expected = job(catalogEntryId, "AUTO_SCHEDULED");
        when(searchExecutor.execute(jobId, catalogEntryId, "AUTO_SCHEDULED", true)).thenReturn(expected);

        assertSame(expected, service.executeQueuedSearch(jobId, catalogEntryId, "AUTO_SCHEDULED", true));
        assertSame(expected, service.executeQueuedSearch(jobId, catalogEntryId, "AUTO_SCHEDULED"));

        verify(searchExecutor, org.mockito.Mockito.times(2))
                .execute(jobId, catalogEntryId, "AUTO_SCHEDULED", true);
    }

    private MagnetSearchJobSummary job(UUID catalogEntryId, String trigger) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new MagnetSearchJobSummary(
                UUID.randomUUID(), catalogEntryId, trigger, "PENDING", List.of(), List.of(),
                null, null, 0, 0, 0, null, null, List.of(), now, now);
    }
}
