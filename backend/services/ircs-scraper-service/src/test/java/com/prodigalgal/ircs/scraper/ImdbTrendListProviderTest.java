package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import java.time.Instant;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class ImdbTrendListProviderTest {

    private static final String CHART = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"ItemList","name":"Most Popular Movies",
             "itemListElement":[
               {"@type":"Movie","url":"https://www.imdb.com/title/tt32558705/",
                "name":"The Hunger Games: Sunrise on the Reaping",
                "image":"https://m.media-amazon.com/images/poster-one.jpg",
                "aggregateRating":{"@type":"AggregateRating","ratingValue":7.4}},
               {"@type":"TVSeries","url":"https://www.imdb.com/title/tt0903747/",
                "name":"Breaking Bad",
                "image":"https://m.media-amazon.com/images/poster-two.jpg",
                "aggregateRating":{"@type":"AggregateRating","ratingValue":9.5}}
             ]}
            </script>
            </head><body></body></html>
            """;

    private final ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);

    @Test
    void readsTheChartsStructuredListingInRankOrder() {
        when(config.imdbEnabled()).thenReturn(true);
        when(config.maxProviderItems()).thenReturn(100);
        var provider = new ImdbTrendListProvider(config, Mockito.mock(TrendProviderHttpClient.class), new ObjectMapper());

        TrendBoardPayload board = provider.parseBoard("movie", CHART, Instant.parse("2026-11-26T08:00:00Z"));

        assertThat(board.status()).isEqualTo("SUCCESS");
        assertThat(board.items()).hasSize(2);
        var first = board.items().getFirst();
        assertThat(first.title()).isEqualTo("The Hunger Games: Sunrise on the Reaping");
        assertThat(first.imdbId()).isEqualTo("tt32558705");
        assertThat(first.posterUrl()).isEqualTo("https://m.media-amazon.com/images/poster-one.jpg");
        assertThat(first.score()).isEqualByComparingTo("7.4");
        assertThat(first.signal().position()).isEqualTo(1);
        assertThat(first.signal().sourceUrl()).isEqualTo("https://www.imdb.com/title/tt32558705/");
        assertThat(board.items().get(1).signal().position()).isEqualTo(2);
    }

    @Test
    void anAnswerThatIsNotAChartIsReportedAsTheWafChallenge() {
        when(config.imdbEnabled()).thenReturn(true);
        var provider = new ImdbTrendListProvider(config, Mockito.mock(TrendProviderHttpClient.class), new ObjectMapper());

        assertThatThrownBy(() -> provider.parseBoard("movie", "<html>challenge</html>", Instant.now()))
                .isInstanceOf(TrendProviderFetchException.class)
                .hasMessage(ImdbTrendListProvider.WAF_CHALLENGE);
    }

    @Test
    void disabledSourceDoesNotCallTheSite() {
        when(config.imdbEnabled()).thenReturn(false);
        var http = Mockito.mock(TrendProviderHttpClient.class);
        var provider = new ImdbTrendListProvider(config, http, new ObjectMapper());

        List<TrendBoardPayload> boards = provider.fetchBoardsAt(Instant.now());

        assertThat(boards).hasSize(2).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("DISABLED");
            assertThat(board.errorCode()).isEqualTo("SOURCE_DISABLED");
        });
        Mockito.verifyNoInteractions(http);
    }

    @Test
    void usesTheOfficialPopularTvMeterChartAndKeepsFailedBoardsVisible() {
        when(config.imdbEnabled()).thenReturn(true);
        when(config.maxProviderItems()).thenReturn(100);
        var http = Mockito.mock(TrendProviderHttpClient.class);
        when(http.get(Mockito.anyString())).thenReturn(CHART).thenReturn("<html>challenge</html>");
        var provider = new ImdbTrendListProvider(config, http, new ObjectMapper());

        List<TrendBoardPayload> boards = provider.fetchBoardsAt(Instant.parse("2026-11-26T08:00:00Z"));

        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        Mockito.verify(http, Mockito.times(2)).get(urls.capture());
        assertThat(urls.getAllValues()).containsExactly(
                "https://www.imdb.com/chart/moviemeter/",
                "https://www.imdb.com/chart/tvmeter");
        assertThat(boards).hasSize(2);
        assertThat(boards.get(0).status()).isEqualTo("SUCCESS");
        assertThat(boards.get(1).status()).isEqualTo("FAILED");
        assertThat(boards.get(1).errorCode()).isEqualTo(ImdbTrendListProvider.WAF_CHALLENGE);
        assertThat(boards.get(1).items()).isEmpty();
    }
}
