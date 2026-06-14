package com.prodigalgal.ircs.storage.image;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoverImageR2PromoteService {

    private final CoverImageAdminRepository repository;
    private final R2ObjectStorage r2ObjectStorage;

    @Transactional
    public CoverImageR2PromoteResult promote(UUID imageId) {
        if (imageId == null) {
            return CoverImageR2PromoteResult.skipped("image id is null");
        }
        if (!r2ObjectStorage.isActive()) {
            log.debug("Cover image R2 promote skipped because R2 is inactive: {}", imageId);
            return CoverImageR2PromoteResult.skipped("r2 inactive");
        }
        boolean scheduled = repository.scheduleR2UploadIfEligible(imageId);
        if (scheduled) {
            log.info("Cover image R2 upload scheduled: {}", imageId);
            return CoverImageR2PromoteResult.scheduledResult();
        }
        return CoverImageR2PromoteResult.skipped("image not eligible or already scheduled");
    }

    public record CoverImageR2PromoteResult(boolean scheduled, String reason) {

        static CoverImageR2PromoteResult scheduledResult() {
            return new CoverImageR2PromoteResult(true, null);
        }

        static CoverImageR2PromoteResult skipped(String reason) {
            return new CoverImageR2PromoteResult(false, reason);
        }
    }
}
