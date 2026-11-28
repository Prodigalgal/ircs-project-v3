package com.prodigalgal.ircs.magnet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MagnetLinkSummary(
        UUID id,
        UUID unifiedVideoId,
        String providerCode,
        String infoHash,
        String magnetUri,
        /** Number of distinct Tracker URLs carried by the magnetUri in this response. */
        int trackerCount,
        String title,
        Long sizeBytes,
        String sizeLabel,
        Instant publishedAt,
        Integer seeders,
        Integer leechers,
        String quality,
        String resolution,
        String matchedExternalIdType,
        String matchedExternalIdValue,
        Integer matchScore,
        String status,
        String sourceUrl,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {
    MagnetLinkSummary withMagnetUri(String uri) {
        if (Objects.equals(magnetUri, uri)) {
            return this;
        }
        return new MagnetLinkSummary(id, unifiedVideoId, providerCode, infoHash, uri,
                MagnetUriUtils.trackersOf(uri).size(), title, sizeBytes, sizeLabel, publishedAt,
                seeders, leechers, quality, resolution, matchedExternalIdType, matchedExternalIdValue,
                matchScore, status, sourceUrl, tags, createdAt, updatedAt);
    }
}
