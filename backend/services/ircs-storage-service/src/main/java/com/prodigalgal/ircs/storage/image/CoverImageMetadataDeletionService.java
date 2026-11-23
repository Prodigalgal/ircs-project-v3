package com.prodigalgal.ircs.storage.image;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoverImageMetadataDeletionService {

    private final CoverImageDeletionRepository coverImageRepository;

    public void delete(UUID imageId) {
        // Cover rows identify source references and may share the same content-addressed object.
        int deleted = coverImageRepository.deleteById(imageId);
        if (deleted > 0) {
            log.info("Deleted cover image metadata: id={}", imageId);
        }
    }
}
