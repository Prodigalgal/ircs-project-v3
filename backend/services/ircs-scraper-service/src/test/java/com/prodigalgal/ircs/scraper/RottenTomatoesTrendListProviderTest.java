package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RottenTomatoesTrendListProviderTest {

    private static final Instant AT = Instant.parse("2026-11-16T10:00:00Z");
    private final ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);
    private final TrendProviderHttpClient http = Mockito.mock(TrendProviderHttpClient.class);
    private final RottenTomatoesTrendListProvider provider = new RottenTomatoesTrendListProvider(config, http);

    @BeforeEach
    void configure() {
        when(config.rottenTomatoesEnabled()).thenReturn(true);
        when(config.maxProviderItems()).thenReturn(100);
    }

    @Test
    void popularOrderIsNotAConvertedPercentageRatingAndStreamingIsNotOriginalYear() {
        var board = provider.parseBoard("movie", """
                <media-info-tile><poster-tile><rt-img src="https://images.example/poster.jpg"></rt-img></poster-tile>
                  <a data-qa="discovery-media-list-item-caption" href="/m/example_movie">
                    <rt-text slot="criticsScore">99%</rt-text><rt-text slot="audienceScore">95%</rt-text>
                    <rt-text data-qa="discovery-media-list-item-title">Example Movie</rt-text>
                    <rt-text data-qa="discovery-media-list-item-start-date">Streaming Nov 10, 2026</rt-text>
                  </a>
                </media-info-tile>
                """, AT);

        var movie = board.items().getFirst();
        assertThat(board.status()).isEqualTo("SUCCESS");
        assertThat(movie.signal().position()).isEqualTo(1);
        assertThat(movie.signal().eventDate()).isEqualTo(LocalDate.parse("2026-11-10"));
        assertThat(movie.signal().eventType()).isEqualTo("DIGITAL_RELEASE");
        assertThat(movie.score()).isNull();
        assertThat(movie.year()).isNull();
        assertThat(movie.publishedAt()).isNull();
        assertThat(movie.signal().externalId()).isEqualTo("example_movie");
        assertThat(movie.posterUrl()).isEqualTo("https://images.example/poster.jpg");
    }

    @Test
    void yearlessLatestEpisodeIsNotAssumedToBeThisYearAndSeasonIsExplicit() {
        var item = provider.parseBoard("tv", """
                <a data-qa="discovery-media-list-item-caption" href="/tv/example_show/s03">
                  <rt-text data-qa="discovery-media-list-item-title">Example Show</rt-text>
                  <rt-text data-qa="discovery-media-list-item-start-date">Latest Episode: Nov 11</rt-text>
                </a>
                """, AT).items().getFirst();

        assertThat(item.signal().eventDate()).isNull();
        assertThat(item.signal().eventType()).isNull();
        assertThat(item.signal().season()).isEqualTo(3);
        assertThat(item.signal().episode()).isNull();
        assertThat(item.signal().externalId()).isEqualTo("example_show");
        assertThat(item.signal().sourceUrl()).isEqualTo("https://www.rottentomatoes.com/tv/example_show/s03");
    }

    @Test
    void futureStreamingEventDoesNotBecomeRecentlyUpdated() {
        var item = provider.parseBoard("movie", """
                <a data-qa="discovery-media-list-item-caption" href="/m/future_movie">
                  <rt-text data-qa="discovery-media-list-item-title">Future</rt-text>
                  <rt-text data-qa="discovery-media-list-item-start-date">Streaming Nov 17, 2026</rt-text>
                </a>
                """, AT).items().getFirst();

        assertThat(item.signal().eventDate()).isNull();
    }

    @Test
    void refusesChallengePageOffHostLinksAndCrossMediaLinks() {
        assertThatThrownBy(() -> provider.parseBoard("movie", "<h1>Verify access</h1>", AT))
                .hasMessage("UNRECOGNIZED_BROWSE_PAGE");
        assertThatThrownBy(() -> provider.parseBoard("movie", """
                <a data-qa="discovery-media-list-item-caption" href="https://evil.example/m/bait">
                  <span data-qa="discovery-media-list-item-title">Bait</span></a>
                <a data-qa="discovery-media-list-item-caption" href="/tv/not_a_movie">
                  <span data-qa="discovery-media-list-item-title">TV show</span></a>
                """, AT)).hasMessage("UNRECOGNIZED_BROWSE_PAGE");
    }

    @Test
    void failingOnePublicBoardPreservesTheOtherSuccessfulSample() {
        when(http.get(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("movies_at_home")) {
                throw new TrendProviderFetchException("HTTP_403");
            }
            return """
                    <a data-qa="discovery-media-list-item-caption" href="/tv/example_show">
                      <span data-qa="discovery-media-list-item-title">Example Show</span>
                    </a>
                    """;
        });

        var boards = provider.fetchBoardsAt(AT);

        assertThat(boards.getFirst().status()).isEqualTo("FAILED");
        assertThat(boards.getFirst().errorCode()).isEqualTo("HTTP_403");
        assertThat(boards.get(1).status()).isEqualTo("SUCCESS");
        assertThat(boards.get(1).items()).hasSize(1);
    }
}
