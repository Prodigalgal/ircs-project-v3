package com.prodigalgal.ircs.metadata.provider.imdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataSourceEvidence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ImdbMetadataProviderTest {
    private static final String SUGGESTIONS = """
            {"d":[{"id":"nm0000209","l":"A person"},
                  {"id":"tt0111161","l":"The Shawshank Redemption","qid":"movie","y":1994,
                   "s":"A preview credit",
                   "i":{"imageUrl":"https://m.media-amazon.com/images/example.jpg"}}]}
            """;
    /**
     * datePublished is a release event date, not the original year: IMDb serves 2026-08-28 for a 1994
     * film. The page still displays the real year in its release-info anchor and its title tag.
     */
    private static final String TITLE_PAGE = """
            <html><head>
            <title>The Shawshank Redemption (1994) - IMDb</title>
            <link rel="canonical" href="https://www.imdb.com/title/tt0111161/">
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Movie",
             "url":"https://www.imdb.com/title/tt0111161/",
             "name":"The Shawshank Redemption",
             "image":"https://m.media-amazon.com/images/public-fixture.jpg",
             "description":"The selected film overview.",
             "genre":["Drama"],
             "datePublished":"2026-08-28",
             "aggregateRating":{"ratingValue":9.3},
             "actor":[{"name":"Tim Robbins"}],
             "director":{"name":"Frank Darabont"}}
            </script>
            </head><body>
            <a href="/title/tt0111161/releaseinfo?ref_=tt_ov_rdat">1994</a>
            </body></html>
            """;

    private final ImdbHttpClient http = mock(ImdbHttpClient.class);
    private final ImdbProviderProperties properties = new ImdbProviderProperties();
    private final ImdbMetadataProvider provider = new ImdbMetadataProvider(http, properties, new ObjectMapper());

    @Test
    void searchesByAliasAndCollectsVerifiedTitlePageDetails() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161")).thenReturn(Optional.of(TITLE_PAGE));

        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();

        assertThat(result.getImdbId()).isEqualTo("tt0111161");
        assertThat(result.getImageSource()).isEqualTo(ProviderType.IMDB);
        assertThat(result.getDescription()).isEqualTo("The selected film overview.");
        assertThat(result.getActorNames()).containsExactly("Tim Robbins");
        assertThat(result.getDirectorNames()).containsExactly("Frank Darabont");
        assertThat(result.getGenreNames()).containsExactly("Drama");
        assertThat(result.getScore()).isEqualByComparingTo(new BigDecimal("9.3"));
        assertThat(result.getYear()).isEqualTo("1994");
        assertThat(result.getTitle()).isEqualTo("The Shawshank Redemption");
    }

    @Test
    void theOriginalYearComesFromThePageNotFromTheReleaseEventDate() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161")).thenReturn(Optional.of(TITLE_PAGE));

        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();

        // 2026-08-28 is what datePublished claims; the release-info anchor's 1994 is the work year.
        assertThat(result.getYear()).isEqualTo("1994");
        assertThat(result.getPublishedAt()).isEqualTo(LocalDate.of(2026, 8, 28));
    }

    @Test
    void fallbackYearComesFromTheTitleTagWhenTheAnchorIsAbsent() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161"))
                .thenReturn(Optional.of(TITLE_PAGE.replace("href=\"/title/tt0111161/releaseinfo?ref_=tt_ov_rdat\">1994", "href=\"/releaseinfo\">n/a")));

        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow().getYear()).isEqualTo("1994");
    }

    @Test
    void readsVerifiedKnownIdWithoutTitleSearch() {
        var input = film();
        input.setImdbId("tt0111161");
        when(http.titlePage("tt0111161")).thenReturn(Optional.of(TITLE_PAGE));

        assertThat(provider.enrich(input, MetadataSourceEvidence.NONE)).isPresent();
        verify(http, never()).suggestion(any());
    }

    @Test
    void keepsVerifiedSuggestionWhenTheMatchedTitlePageIsNotFound() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161")).thenThrow(ImdbHttpClient.detailsUnavailable());

        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();

        assertThat(result.getImdbId()).isEqualTo("tt0111161");
        assertThat(result.getPosterUrl()).endsWith("example.jpg");
        assertThat(result.getActorNames()).isEmpty();
        assertThat(result.getDescription()).isNull();
    }

    @Test
    void doesNotBindTheCandidateWhenItsTitleWasExplicitlyRemoved() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161")).thenReturn(Optional.empty());

        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
    }

    @Test
    void rejectsAmbiguousSameYearCandidates() {
        when(http.suggestion(any())).thenReturn(Optional.of("""
                {"d":[{"id":"tt0111161","l":"The Shawshank Redemption","qid":"movie","y":1994},
                      {"id":"tt1111161","l":"The Shawshank Redemption","qid":"movie","y":1994}]}
                """));

        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
        verify(http, never()).titlePage(any());
    }

    @Test
    void rejectsATitlePageForAnotherWork() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161"))
                .thenReturn(Optional.of(TITLE_PAGE.replace("tt0111161", "tt1111161")));

        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
    }

    @Test
    void rejectsASeriesPageForAFilmContext() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161"))
                .thenReturn(Optional.of(TITLE_PAGE.replace("\"@type\":\"Movie\"", "\"@type\":\"TVSeries\"")));

        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
    }

    @Test
    void aPageWithoutStructuredDataIsReportedAsUnavailable() {
        when(http.suggestion(any())).thenReturn(Optional.of(SUGGESTIONS));
        when(http.titlePage("tt0111161")).thenReturn(Optional.of("<html><body>blocked</body></html>"));

        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();
        assertThat(result.getImdbId()).isEqualTo("tt0111161");
        assertThat(result.getDescription()).isNull();
    }

    @Test
    void malformedSearchResponseIsRetryable() {
        when(http.suggestion(any())).thenReturn(Optional.of("<html>error</html>"));
        assertThatThrownBy(() -> provider.enrich(film(), MetadataSourceEvidence.NONE))
                .isInstanceOf(MetadataProviderRetryableException.class);
    }

    @Test
    void unexpectedTerminalFailureIsNotSwallowed() {
        var input = film();
        input.setImdbId("tt0111161");
        when(http.titlePage("tt0111161"))
                .thenThrow(new MetadataProviderTerminalException("IMDB_HTTP_500", "boom"));

        assertThatThrownBy(() -> provider.enrich(input, MetadataSourceEvidence.NONE))
                .isInstanceOf(MetadataProviderTerminalException.class)
                .extracting("errorCode")
                .isEqualTo("IMDB_HTTP_500");
    }

    private static MetadataSearchContext film() {
        return MetadataSearchContext.builder()
                .title("肖申克的救赎").aliasTitle("The Shawshank Redemption")
                .year("1994").categorySlug("movie").build();
    }
}
