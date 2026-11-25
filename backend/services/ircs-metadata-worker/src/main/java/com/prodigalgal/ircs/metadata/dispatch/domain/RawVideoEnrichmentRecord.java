package com.prodigalgal.ircs.metadata.dispatch.domain;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RawVideoEnrichmentRecord {
    private UUID id;
    private String title;
    private String aliasTitle;
    private String subtitle;
    private Integer season;
    private String year;
    private String categorySlug;
    private String doubanId;
    private String tmdbId;
    private String imdbId;
    private String rottenTomatoesId;
    private String enrichmentStatus;
    private Integer enrichmentRetryCount;
    private String dataHash;
    private boolean coreMetadataIncomplete;

    public boolean hasAuthoritativeId() {
        return AuthoritativeIds.normalizeDouban(doubanId) != null
                || AuthoritativeIds.normalizeTmdb(tmdbId) != null
                || AuthoritativeIds.normalizeImdb(imdbId) != null
                || AuthoritativeIds.normalizeRottenTomatoes(rottenTomatoesId) != null;
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
