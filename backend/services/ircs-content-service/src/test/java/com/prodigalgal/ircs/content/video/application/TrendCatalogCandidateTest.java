package com.prodigalgal.ircs.content.video.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.content.video.domain.TrendCatalogCandidate;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrendCatalogCandidateTest {

    @ParameterizedTest
    @MethodSource("placeholderIds")
    void rejectsUnknownAndZeroAuthoritativeIds(String provider, String id) {
        assertThatThrownBy(() -> TrendCatalogCandidate.from(item(provider, id, "movie", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("INVALID_TREND_EXTERNAL_ID");
    }

    @Test
    void mediaNamespacesAndSeasonsRemainPartOfIdentity() {
        var movie = TrendCatalogCandidate.from(item("TMDB", "00042", "movie", null, null, "42"));
        var firstSeason = TrendCatalogCandidate.from(item("TMDB", "42", "tv", 1, 4, "42"));
        var secondSeason = TrendCatalogCandidate.from(item("TMDB", "42", "tv", 2, 4, "42"));
        var unknownSeason = TrendCatalogCandidate.from(item("TMDB", "42", "tv", null, null, "42"));

        assertThat(movie.externalId()).isEqualTo("42");
        assertThat(movie.identityKey()).isNotEqualTo(firstSeason.identityKey());
        assertThat(firstSeason.identityKey()).isNotEqualTo(secondSeason.identityKey());
        assertThat(unknownSeason.identityKey()).isNotIn(firstSeason.identityKey(), secondSeason.identityKey());
    }

    @Test
    void conflictingPayloadAndSignalIdsCannotBindTwoDifferentWorks() {
        assertThatThrownBy(() -> TrendCatalogCandidate.from(item("TMDB", "42", "movie", null, null, "99")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("CONFLICTING_TREND_EXTERNAL_ID");
    }

    @Test
    void movieCannotCarryATvSeasonAndSeasonEpisodeBoundsAreChecked() {
        assertThatThrownBy(() -> TrendCatalogCandidate.from(item("TMDB", "42", "movie", 2, null, "42")))
                .hasMessage("INVALID_TREND_SEASON_EPISODE");
        assertThatThrownBy(() -> TrendCatalogCandidate.from(item("TMDB", "42", "tv", -1, 1, "42")))
                .hasMessage("INVALID_TREND_SEASON_EPISODE");
        assertThatThrownBy(() -> TrendCatalogCandidate.from(item("TMDB", "42", "tv", 1, 10001, "42")))
                .hasMessage("INVALID_TREND_SEASON_EPISODE");
    }

    @Test
    void titleKeyNormalizesWidthCaseAndPunctuationWithoutDiscardingEpisodeNumbers() {
        assertThat(TrendCatalogCandidate.titleKey(" Ｔｈｅ Movie：２ ")).isEqualTo("themovie2");
        assertThat(TrendCatalogCandidate.titleKey("The Movie 2")).isNotEqualTo(TrendCatalogCandidate.titleKey("The Movie 3"));
    }

    private static Stream<Arguments> placeholderIds() {
        return Stream.of("TMDB", "Douban", "IMDb", "RottenTomatoes")
                .flatMap(provider -> Stream.of(null, "", "0", "0000", "unknown")
                        .map(id -> Arguments.of(provider, id)));
    }

    private static TrendItemPayload item(String provider, String id, String type, Integer season, Integer episode, String reportedId) {
        return new TrendItemPayload("Example", null, null, "2026", null, null, null,
                "TMDB".equals(provider) ? reportedId : null, "Douban".equals(provider) ? reportedId : null,
                "IMDb".equals(provider) ? reportedId : null, type,
                new TrendSignalPayload(provider, "popular", type, id, 1, "popular", null, null,
                        Instant.now().minusSeconds(60), null, null, season, episode, null, null,
                        "movie".equals(type) ? "movie" : "series"));
    }
}
