package com.prodigalgal.ircs.metadata.result.domain;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RawVideoMetadataRecord {
    private UUID id;
    private String doubanId;
    private String tmdbId;
    private String imdbId;
    private String rottenTomatoesId;
    private String description;
    private BigDecimal score;
    private String year;
    private String aliasTitle;
    private String rawMetadata;
    private Set<String> lockedFields;
    private String enrichmentStatus;
    private Integer enrichmentRetryCount;
    private String aggregationStatus;
    private String dataHash;
    private Set<String> actorNames;
    private Set<String> directorNames;
    private Set<String> areaCodes;
    private Set<String> languageCodes;
    private Set<String> genreCodes;

    public RawVideoMetadataRecord(UUID id, String doubanId, String tmdbId, String imdbId,
            String rottenTomatoesId, String description, BigDecimal score, String year, String aliasTitle,
            String rawMetadata, Set<String> lockedFields, String enrichmentStatus, Integer enrichmentRetryCount,
            String aggregationStatus, String dataHash) {
        this(id, doubanId, tmdbId, imdbId, rottenTomatoesId, description, score, year, aliasTitle,
                rawMetadata, lockedFields, enrichmentStatus, enrichmentRetryCount, aggregationStatus, dataHash,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    public boolean isFieldLocked(String fieldName) {
        return lockedFields != null && lockedFields.contains(fieldName);
    }

    public boolean hasAuthoritativeId() {
        return AuthoritativeIds.normalizeDouban(doubanId) != null
                || AuthoritativeIds.normalizeTmdb(tmdbId) != null
                || AuthoritativeIds.normalizeImdb(imdbId) != null
                || AuthoritativeIds.normalizeRottenTomatoes(rottenTomatoesId) != null;
    }

    public boolean hasProviderId(ProviderType providerType) {
        if (providerType == null) {
            return false;
        }
        return switch (providerType) {
            case DOUBAN -> AuthoritativeIds.normalizeDouban(doubanId) != null;
            case TMDB -> AuthoritativeIds.normalizeTmdb(tmdbId) != null;
            case ROTTEN_TOMATOES -> AuthoritativeIds.normalizeRottenTomatoes(rottenTomatoesId) != null;
            default -> false;
        };
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
