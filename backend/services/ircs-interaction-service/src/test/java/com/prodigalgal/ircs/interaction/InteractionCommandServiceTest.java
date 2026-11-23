package com.prodigalgal.ircs.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InteractionCommandServiceTest {

    private final JdbcInteractionRepository repository = org.mockito.Mockito.mock(JdbcInteractionRepository.class);
    private final InteractionCommandService service = new InteractionCommandService(repository);

    @Test
    void toggleFavoriteDeletesExistingFavorite() {
        UUID memberId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.catalogEntryExists(catalogEntryId)).thenReturn(true);
        when(repository.deleteFavorite(memberId, catalogEntryId)).thenReturn(1);

        boolean favorited = service.toggleFavorite(memberId, catalogEntryId);

        assertFalse(favorited);
        verify(repository).deleteFavorite(memberId, catalogEntryId);
        verify(repository, never()).insertFavorite(any(), eq(memberId), eq(catalogEntryId), any());
    }

    @Test
    void toggleFavoriteInsertsWhenFavoriteDoesNotExist() {
        UUID memberId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.catalogEntryExists(catalogEntryId)).thenReturn(true);
        when(repository.deleteFavorite(memberId, catalogEntryId)).thenReturn(0);

        boolean favorited = service.toggleFavorite(memberId, catalogEntryId);

        assertTrue(favorited);
        verify(repository).insertFavorite(any(UUID.class), eq(memberId), eq(catalogEntryId), any(Instant.class));
    }

    @Test
    void toggleFavoriteRejectsMissingCatalogEntry() {
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.catalogEntryExists(catalogEntryId)).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.toggleFavorite(UUID.randomUUID(), catalogEntryId));

        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void favoriteStatusReadsRepository() {
        UUID memberId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.favoriteExists(memberId, catalogEntryId)).thenReturn(true);

        assertTrue(service.favoriteStatus(memberId, catalogEntryId));
    }

    @Test
    void reportProgressUpsertsNormalizedProgress() {
        UUID memberId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        UUID videoId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        when(repository.catalogEntryExists(catalogEntryId)).thenReturn(true);
        ProgressReportRequest request =
                new ProgressReportRequest(catalogEntryId, videoId, episodeId, " 第1集 ", -10, -1);

        service.reportProgress(memberId, request);

        verify(repository).upsertWatchHistory(
                any(UUID.class),
                eq(memberId),
                eq(catalogEntryId),
                eq(videoId),
                eq(episodeId),
                eq("第1集"),
                eq(0),
                eq(0),
                any(Instant.class));
    }

    @Test
    void reportProgressRejectsBlankEpisodeName() {
        UUID catalogEntryId = UUID.randomUUID();
        when(repository.catalogEntryExists(catalogEntryId)).thenReturn(true);
        ProgressReportRequest request = new ProgressReportRequest(catalogEntryId, null, null, " ", 1, 10);

        ApiException exception =
                assertThrows(ApiException.class, () -> service.reportProgress(UUID.randomUUID(), request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
    }

    @Test
    void deleteHistoryRecordRejectsMissingId() {
        ApiException exception =
                assertThrows(ApiException.class, () -> service.deleteHistoryRecord(UUID.randomUUID(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
    }
}

