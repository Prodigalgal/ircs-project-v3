package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import java.time.LocalDate;
import java.util.List;

record AuthorityCatalogEvidence(String mediaType, long tmdbId, int seasonNumber, LocalDate releasedOn,
        String categoryCode, EnrichedMetadataDTO metadata, List<Season> seasons, List<Episode> episodes) {

    record Season(int number, long tmdbId, String title, LocalDate releasedOn) { }

    record Episode(int number, long tmdbId, String title, LocalDate airedOn) { }
}
