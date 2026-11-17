package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class TmdbTrendListProviderTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-11-16T10:00:00Z");
    private final ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);
    private final TrendProviderHttpClient http = Mockito.mock(TrendProviderHttpClient.class);
    private final TmdbCredentialResolver credentials = Mockito.mock(TmdbCredentialResolver.class);
    private final TmdbTrendListProvider provider = new TmdbTrendListProvider(config, http, new ObjectMapper(), credentials);

    @BeforeEach
    void configure() {
        when(config.tmdbEnabled()).thenReturn(true);
        when(config.maxProviderItems()).thenReturn(100);
        when(config.tmdbEpisodeDetailBudget()).thenReturn(1);
        when(credentials.resolveApiKey()).thenReturn(Optional.of("fixture-credential"));
        when(http.get(anyString())).thenReturn("{\"results\":[]}");
    }

    @Test
    void keepsMediaNamespacesRanksWindowsAndMissingRatingSeparate() {
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("/trending/movie/")) {
                return """
                        {"results":[
                          {"id":1,"title":"Adult excluded","adult":true},
                          {"id":42,"title":"电影","original_title":"Movie","release_date":"2026-11-15",
                           "vote_average":0,"vote_count":0,"popularity":91.25,"genre_ids":[16]}
                        ]}
                        """;
            }
            if (url.contains("/trending/tv/")) {
                return """
                        {"results":[{"id":42,"name":"剧集","first_air_date":"2018-01-01",
                        "vote_average":8.2,"vote_count":100,"popularity":25,"genre_ids":[18]}]}
                        """;
            }
            return "{\"results\":[]}";
        });

        List<TrendBoardPayload> boards = provider.fetchBoardsAt(OBSERVED_AT);

        assertThat(boards).hasSize(6).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("SUCCESS");
            assertThat(board.collectedAt()).isEqualTo(OBSERVED_AT);
        });
        var movie = board(boards, "trending-day", "movie").items().getFirst();
        var tv = board(boards, "trending-week", "tv").items().getFirst();
        assertThat(movie.tmdbId()).isEqualTo(tv.tmdbId());
        assertThat(movie.signal().mediaType()).isEqualTo("movie");
        assertThat(tv.signal().mediaType()).isEqualTo("tv");
        assertThat(movie.signal().position()).isEqualTo(2);
        assertThat(movie.signal().window()).isEqualTo("day");
        assertThat(tv.signal().window()).isEqualTo("week");
        assertThat(movie.score()).isNull();
        assertThat(movie.signal().popularity()).isEqualByComparingTo("91.25");
        assertThat(movie.signal().voteCount()).isZero();
        assertThat(movie.signal().categoryCode()).isEqualTo("anime");
        assertThat(tv.signal().eventDate()).isNull();
        assertThat(tv.signal().season()).isNull();
        assertThat(tv.publishedAt()).isEqualTo(LocalDate.parse("2018-01-01"));
    }

    @Test
    void derivesNewEpisodeFromVerifiedDetailsAndReusesLookupAcrossBoards() {
        AtomicInteger details = new AtomicInteger();
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("/tv/42?")) {
                details.incrementAndGet();
                return """
                        {"id":42,"last_episode_to_air":{"air_date":"2026-11-16","season_number":3,"episode_number":12}}
                        """;
            }
            if (url.contains("/airing_today") || url.contains("/trending/tv/")) {
                return "{\"results\":[{\"id\":42,\"name\":\"剧集\",\"first_air_date\":\"2018-01-01\"}]}";
            }
            return "{\"results\":[]}";
        });

        var boards = provider.fetchBoardsAt(OBSERVED_AT);
        var aired = board(boards, "airing-today", "tv").items().getFirst();

        assertThat(aired.signal().eventDate()).isEqualTo(LocalDate.parse("2026-11-16"));
        assertThat(aired.signal().eventType()).isEqualTo("EPISODE_AIR");
        assertThat(aired.signal().season()).isEqualTo(3);
        assertThat(aired.signal().episode()).isEqualTo(12);
        assertThat(aired.signal().position()).isNull();
        assertThat(aired.year()).isEqualTo("2018");
        assertThat(board(boards, "trending-day", "tv").items().getFirst().signal().season()).isEqualTo(3);
        assertThat(details).hasValue(1);
    }

    @Test
    void missingDetailsDoNotTurnOriginalPremiereIntoAnEpisodeOrEraseOtherBoards() {
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("/airing_today")) {
                return "{\"results\":[{\"id\":42,\"name\":\"Old show\",\"first_air_date\":\"2009-01-01\"}]}";
            }
            if (url.contains("/tv/42?")) {
                throw new TrendProviderFetchException("HTTP_503");
            }
            if (url.contains("/trending/movie/day")) {
                return "{\"results\":[{\"id\":50,\"title\":\"Still available\"}]}";
            }
            return "{\"results\":[]}";
        });

        var boards = provider.fetchBoardsAt(OBSERVED_AT);

        assertThat(board(boards, "airing-today", "tv").status()).isEqualTo("FAILED");
        assertThat(board(boards, "airing-today", "tv").items()).isEmpty();
        assertThat(board(boards, "trending-day", "movie").status()).isEqualTo("SUCCESS");
    }

    @Test
    void capsDetailRequestsAndRejectsFutureEpisodes() {
        AtomicInteger details = new AtomicInteger();
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("/airing_today")) {
                return """
                        {"results":[{"id":41,"name":"Future"},{"id":42,"name":"No budget"}]}
                        """;
            }
            if (url.contains("/tv/41?")) {
                details.incrementAndGet();
                return "{\"id\":41,\"last_episode_to_air\":{\"air_date\":\"2026-11-17\",\"season_number\":1,\"episode_number\":2}}";
            }
            if (url.contains("/tv/42?")) {
                throw new AssertionError("detail budget exceeded");
            }
            return "{\"results\":[]}";
        });

        var boards = provider.fetchBoardsAt(OBSERVED_AT);

        assertThat(board(boards, "airing-today", "tv").items()).isEmpty();
        assertThat(details).hasValue(1);
    }

    @Test
    void rejectsEpisodeEvidenceReturnedForAnotherSeriesId() {
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("/airing_today")) {
                return "{\"results\":[{\"id\":42,\"name\":\"Requested show\"}]}";
            }
            if (url.contains("/tv/42?")) {
                return "{\"id\":99,\"last_episode_to_air\":{\"air_date\":\"2026-11-16\",\"season_number\":1,\"episode_number\":2}}";
            }
            return "{\"results\":[]}";
        });

        var board = board(provider.fetchBoardsAt(OBSERVED_AT), "airing-today", "tv");

        assertThat(board.status()).isEqualTo("FAILED");
        assertThat(board.items()).isEmpty();
    }

    @Test
    void rejectsInvalidIdAndErrorPayloadWithoutReportingAnEmptySuccess() {
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            return url.contains("/trending/movie/day")
                    ? "{\"results\":[{\"id\":0,\"title\":\"No identity\"}]}"
                    : "{\"success\":false,\"status_message\":\"upstream rejected fixture-credential\"}";
        });

        var boards = provider.fetchBoardsAt(OBSERVED_AT);

        assertThat(boards).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("FAILED");
            assertThat(board.errorCode()).doesNotContain("fixture-credential");
        });
    }

    @Test
    void missingCredentialsAreReportedWithoutNetworkCalls() {
        when(credentials.resolveApiKey()).thenReturn(Optional.empty());

        assertThat(provider.fetchBoardsAt(OBSERVED_AT)).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("UNAVAILABLE");
            assertThat(board.errorCode()).isEqualTo("CREDENTIAL_UNAVAILABLE");
        });
        verify(http, never()).get(anyString());
    }

    private static TrendBoardPayload board(List<TrendBoardPayload> boards, String name, String mediaType) {
        return boards.stream().filter(board -> name.equals(board.board()) && mediaType.equals(board.mediaType()))
                .findFirst().orElseThrow();
    }
}
