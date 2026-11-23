package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorityCatalogBindingPolicyTest {

    private final AuthorityCatalogBindingPolicy policy = new AuthorityCatalogBindingPolicy();

    @Test
    void bindsOnlyAnExactNormalizedTitleWithCompatibleYearAndMovieSeason() {
        UUID projectionId = UUID.randomUUID();
        SourceRecordAggregationRecord raw = raw("Film Title", "2020", "movie", null, null, null);

        AuthorityCatalogBindingDecision decision = policy.decide(raw, "movie", List.of(
                projection(projectionId, "movie", "Film Title", 2020, 1, -1, null, null)));

        assertThat(decision.status()).isEqualTo("BOUND");
        assertThat(decision.catalogEntryId()).isEqualTo(projectionId);
    }

    @Test
    void refusesTitleOnlyOrFarYearCandidatesAndRequiresReviewForAmbiguousAuthority() {
        SourceRecordAggregationRecord raw = raw("Film Title", "2020", "movie", null, null, null);

        assertThat(policy.decide(raw, "movie", List.of(
                projection(UUID.randomUUID(), "movie", "Film Title", 2024, 1, -1, null, null))).status())
                .isEqualTo("WAITING_AUTHORITY");
        assertThat(policy.decide(raw, "movie", List.of(
                projection(UUID.randomUUID(), "movie", "Film Title", 2020, 1, -1, null, null),
                projection(UUID.randomUUID(), "movie", "Film Title", 2020, 1, -1, null, null))).status())
                .isEqualTo("MERGE_AMBIGUOUS");
    }

    @Test
    void permitsOneYearDifferenceOnlyWhenAnExternalIdAndSeasonCorroborate() {
        SourceRecordAggregationRecord raw = raw("Film Title", "2020", "movie", null, "123", null);

        assertThat(policy.decide(raw, "movie", List.of(
                projection(UUID.randomUUID(), "movie", "Film Title", 2021, 1, -1, null, null))).status())
                .isEqualTo("WAITING_AUTHORITY");
        UUID verified = UUID.randomUUID();
        AuthorityCatalogProjection corroborated = projection(verified, "movie", "Film Title", 2021, 1, -1, 123L, null);
        assertThat(policy.decide(raw, "movie", List.of(corroborated)).catalogEntryId()).isEqualTo(verified);
    }

    @Test
    void rejectsContradictingExternalIdsAndRequiresSeasonEvidenceForMultiSeasonTv() {
        SourceRecordAggregationRecord movie = raw("Film Title", "2020", "movie", null, "123", "tt0001");
        assertThat(policy.decide(movie, "movie", List.of(
                projection(UUID.randomUUID(), "movie", "Film Title", 2020, 1, -1, 456L, "tt0001"))).status())
                .isEqualTo("IDENTITY_CONFLICT");

        SourceRecordAggregationRecord series = raw("Series Title", "2020", "tv", null, null, null);
        UUID seriesWork = UUID.randomUUID();
        assertThat(policy.decide(series, "tv", List.of(
                projection(seriesWork, UUID.randomUUID(), "tv", "Series Title", 2020, 3, 1, null, null),
                projection(seriesWork, UUID.randomUUID(), "tv", "Series Title", 2020, 3, 2, null, null),
                projection(seriesWork, UUID.randomUUID(), "tv", "Series Title", 2020, 3, 3, null, null))).status())
                .isEqualTo("WAITING_AUTHORITY");
    }

    @Test
    void acceptsOnlyExplicitSpecialSeasonZeroAndUsesAliasAsExactEvidence() {
        UUID special = UUID.randomUUID();
        UUID seriesWork = UUID.randomUUID();
        SourceRecordAggregationRecord specialRaw = raw("Series Title", "2020", "tv", 0, null, null);
        AuthorityCatalogBindingDecision specialDecision = policy.decide(specialRaw, "tv", List.of(
                projection(seriesWork, UUID.randomUUID(), "tv", "Series Title", 2020, 1, 1, null, null),
                projection(seriesWork, special, "tv", "Series Title", 2020, 1, 0, null, null)));
        assertThat(specialDecision.status()).isEqualTo("BOUND");
        assertThat(specialDecision.catalogEntryId()).isEqualTo(special);

        UUID aliasProjection = UUID.randomUUID();
        SourceRecordAggregationRecord aliasRaw = new SourceRecordAggregationRecord(UUID.randomUUID(), "Source Title",
                "Canonical Alias", null, "2020", null, null, null, null, null, null, null, "movie", "movie",
                null, null, null, null, "READY", "SUCCESS", "PENDING");
        assertThat(policy.decide(aliasRaw, "movie", List.of(
                projection(aliasProjection, "movie", "Canonical Alias", 2020, 1, -1, null, null)))
                .catalogEntryId()).isEqualTo(aliasProjection);
    }

    @Test
    void documentaryWithoutSeasonBindsOnlyWhenVerifiedMovieOrTvProjectionIsUnique() {
        SourceRecordAggregationRecord raw = raw("Documentary Title", "2020", "documentary", null, null, null);
        UUID movieProjection = UUID.randomUUID();
        AuthorityCatalogProjection movie = projection(movieProjection, "movie", "Documentary Title",
                2020, 1, -1, null, null);
        assertThat(policy.decide(raw, Set.of("movie", "tv"), List.of(movie)).catalogEntryId())
                .isEqualTo(movieProjection);

        AuthorityCatalogProjection tv = projection(UUID.randomUUID(), "tv", "Documentary Title",
                2020, 1, 1, null, null);
        assertThat(policy.decide(raw, Set.of("movie", "tv"), List.of(movie, tv)).status())
                .isEqualTo("MERGE_AMBIGUOUS");
    }

    @Test
    void identifierRecallDoesNotBindWhenNormalizedTitleDoesNotMatch() {
        SourceRecordAggregationRecord raw = raw("Another Film", "2020", "movie", null, "603", null);
        AuthorityCatalogProjection candidate = projection(UUID.randomUUID(), "movie", "The Matrix",
                2020, 1, -1, 603L, null);

        assertThat(policy.decide(raw, "movie", List.of(candidate)).status()).isEqualTo("WAITING_AUTHORITY");
    }

    private SourceRecordAggregationRecord raw(
            String title, String year, String category, Integer season, String tmdb, String imdb) {
        return new SourceRecordAggregationRecord(UUID.randomUUID(), title, null, null, year,
                null, null, null, null, null, null, season, category, category,
                null, tmdb, imdb, null, "READY", "SUCCESS", "PENDING");
    }

    private AuthorityCatalogProjection projection(
            UUID catalogEntryId,
            String mediaType,
            String title,
            Integer releaseYear,
            Integer regularSeasonCount,
            int season,
            Long tmdbId,
            String imdbId) {
        return projection(UUID.randomUUID(), catalogEntryId, mediaType, title, releaseYear,
                regularSeasonCount, season, tmdbId, imdbId);
    }

    private AuthorityCatalogProjection projection(
            UUID workId,
            UUID catalogEntryId,
            String mediaType,
            String title,
            Integer releaseYear,
            Integer regularSeasonCount,
            int season,
            Long tmdbId,
            String imdbId) {
        String key = AuthorityCatalogBindingPolicy.normalizeAuthorityTitleKey(title);
        return new AuthorityCatalogProjection(workId, mediaType, tmdbId, imdbId,
                key, null, releaseYear, null, regularSeasonCount, season, null, catalogEntryId);
    }
}
