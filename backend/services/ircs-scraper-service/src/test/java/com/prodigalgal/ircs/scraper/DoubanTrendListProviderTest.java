package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class DoubanTrendListProviderTest {

    private static final Instant AT = Instant.parse("2026-11-16T10:00:00Z");
    private final ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);
    private final TrendProviderHttpClient http = Mockito.mock(TrendProviderHttpClient.class);
    private final DoubanTrendListProvider provider = new DoubanTrendListProvider(config, http, new ObjectMapper());

    @BeforeEach
    void configure() {
        when(config.doubanEnabled()).thenReturn(true);
        when(config.maxProviderItems()).thenReturn(100);
    }

    @Test
    void challengePageDoesNotClearMovieBoardAndTvCanStillSucceed() {
        when(http.get(anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).contains("/chart")
                ? "<html><h1>访问验证</h1><form>captcha</form></html>"
                : "{\"subjects\":[{\"id\":\"123\",\"title\":\"剧集\",\"rate\":\"\",\"cover\":\"https://img.example/poster.jpg\"}]}" );

        var boards = provider.fetchBoardsAt(AT);

        assertThat(boards.getFirst().status()).isEqualTo("FAILED");
        assertThat(boards.getFirst().errorCode()).isEqualTo("UNRECOGNIZED_CHART_PAGE");
        assertThat(boards.get(1).status()).isEqualTo("SUCCESS");
        var tv = boards.get(1).items().getFirst();
        assertThat(tv.signal().position()).isEqualTo(1);
        assertThat(tv.score()).isNull();
        assertThat(tv.signal().eventDate()).isNull();
        assertThat(tv.signal().observedAt()).isEqualTo(AT);
    }

    @Test
    void retainsActualChartPositionRatingAndYearWithoutInventingReleaseDate() {
        when(http.get(anyString())).thenAnswer(invocation -> invocation.<String>getArgument(0).contains("/chart")
                ? """
                    <div class="indent">
                      <table><a class="nbg" href="https://movie.douban.com/subject/0/" title="Invalid"></a></table>
                      <table><a class="nbg" href="https://movie.douban.com/subject/12345/" title="电影"></a>
                        <p class="pl">2026 / 中国大陆 / 剧情</p><span class="rating_nums">8.5</span>
                        <img src="https://img.example/s_ratio_poster/example.jpg">
                      </table>
                    </div>
                    """ : "{\"subjects\":[]}" );

        var movie = provider.fetchBoardsAt(AT).getFirst().items().getFirst();

        assertThat(movie.doubanId()).isEqualTo("12345");
        assertThat(movie.signal().position()).isEqualTo(2);
        assertThat(movie.year()).isEqualTo("2026");
        assertThat(movie.publishedAt()).isNull();
        assertThat(movie.signal().eventDate()).isNull();
        assertThat(movie.score()).isEqualByComparingTo("8.5");
        assertThat(movie.posterUrl()).contains("l_ratio_poster");
    }

    @Test
    void treatsMalformedJsonAndMissingSubjectStructureAsFailure() {
        when(http.get(anyString())).thenReturn("{\"error\":\"access blocked\"}");

        assertThat(provider.fetchBoardsAt(AT)).allSatisfy(board -> {
            assertThat(board.status()).isEqualTo("FAILED");
            assertThat(board.items()).isEmpty();
        });
    }
}
