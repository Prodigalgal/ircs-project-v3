package com.prodigalgal.ircs.storage.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoverImageR2PromoteServiceTest {

    private final CoverImageAdminRepository repository = org.mockito.Mockito.mock(CoverImageAdminRepository.class);
    private final R2ObjectStorage r2ObjectStorage = org.mockito.Mockito.mock(R2ObjectStorage.class);
    private final CoverImageR2PromoteService service = new CoverImageR2PromoteService(repository, r2ObjectStorage);

    @Test
    void skipsWithoutMutatingWhenR2Inactive() {
        UUID imageId = UUID.randomUUID();
        when(r2ObjectStorage.isActive()).thenReturn(false);

        CoverImageR2PromoteService.CoverImageR2PromoteResult result = service.promote(imageId);

        assertFalse(result.scheduled());
        assertEquals("r2 inactive", result.reason());
        verify(repository, never()).scheduleR2UploadIfEligible(imageId);
    }

    @Test
    void schedulesEligibleCoverWhenR2Active() {
        UUID imageId = UUID.randomUUID();
        when(r2ObjectStorage.isActive()).thenReturn(true);
        when(repository.scheduleR2UploadIfEligible(imageId)).thenReturn(true);

        CoverImageR2PromoteService.CoverImageR2PromoteResult result = service.promote(imageId);

        assertTrue(result.scheduled());
        verify(repository).scheduleR2UploadIfEligible(imageId);
    }

    @Test
    void skipsWhenCoverIsNotEligibleOrAlreadyScheduled() {
        UUID imageId = UUID.randomUUID();
        when(r2ObjectStorage.isActive()).thenReturn(true);
        when(repository.scheduleR2UploadIfEligible(imageId)).thenReturn(false);

        CoverImageR2PromoteService.CoverImageR2PromoteResult result = service.promote(imageId);

        assertFalse(result.scheduled());
        assertEquals("image not eligible or already scheduled", result.reason());
    }
}
