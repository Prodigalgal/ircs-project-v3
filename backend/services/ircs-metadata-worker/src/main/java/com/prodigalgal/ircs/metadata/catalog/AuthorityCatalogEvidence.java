package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import java.time.LocalDate;
import java.util.List;

record AuthorityCatalogEvidence(String mediaType, long tmdbId, int seasonNumber, LocalDate releasedOn,
        String categoryCode, EnrichedMetadataDTO metadata, List<Season> seasons, List<Episode> episodes,
        List<CreditSet> creditSets) {

    record Season(int number, long tmdbId, String title, LocalDate releasedOn) { }

    record Episode(int number, long tmdbId, String title, LocalDate airedOn) { }

    enum CreditKind { CAST, GUEST_CAST, CREW }

    record CreditSet(Integer seasonNumber, Integer episodeNumber, CreditKind kind, boolean complete, List<Credit> credits) { }

    record Credit(String tmdbPersonId, String tmdbCreditId, String creditedName, String originalName, String department,
            String job, String characterName, Integer billingOrder, int sourceOrder) { }
}
