package com.prodigalgal.ircs.metadata.provider.imdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataSourceEvidence;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ImdbMetadataProviderTest {
    private static final URI DETAIL = URI.create("https://www.imdb.com/title/tt0111161/");
    private static final String SUGGESTIONS = """
            {"d":[{"id":"nm0000209","l":"A person"},
                  {"id":"tt0111161","l":"The Shawshank Redemption","qid":"movie","y":1994,
                   "s":"A preview credit",
                   "i":{"imageUrl":"https://m.media-amazon.com/images/example.jpg"}}]}
            """;
    private static final String DETAILS = """
            <link rel="canonical" href="https://www.imdb.com/title/tt0111161/">
            <script type="application/ld+json">
              {"@type":"Movie","url":"https://www.imdb.com/title/tt0111161/",
               "name":"The Shawshank Redemption","datePublished":"1994-09-23",
               "description":"The selected film overview.",
               "image":"https://m.media-amazon.com/images/public-fixture.jpg",
               "genre":["Drama"],"aggregateRating":{"ratingValue":9.3},
               "actor":[{"name":"Tim Robbins"}],"director":{"name":"Frank Darabont"}}
            </script>
            """;

    private final ImdbHttpClient http = mock(ImdbHttpClient.class);
    private final ImdbMetadataProvider provider = new ImdbMetadataProvider(http, new ImdbProviderProperties(), new ObjectMapper());

    @Test
    void searchesByAliasAndCollectsVerifiedWorkDetails() {
        when(http.get(any(), eq(false))).thenReturn(Optional.of(SUGGESTIONS));
        when(http.get(DETAIL, true)).thenReturn(Optional.of(DETAILS));
        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();

        assertThat(result.getImdbId()).isEqualTo("tt0111161");
        assertThat(result.getImageSource()).isEqualTo(ProviderType.IMDB);
        assertThat(result.getDescription()).isEqualTo("The selected film overview.");
        assertThat(result.getActorNames()).containsExactly("Tim Robbins");
        assertThat(result.getDirectorNames()).containsExactly("Frank Darabont");
        assertThat(result.getGenreNames()).containsExactly("Drama");
        assertThat(result.getScore()).isEqualByComparingTo(new BigDecimal("9.3"));
    }

    @Test
    void readsVerifiedKnownIdWithoutTitleSearch() {
        var input = film();
        input.setImdbId("tt0111161");
        when(http.get(DETAIL, true)).thenReturn(Optional.of(DETAILS));
        assertThat(provider.enrich(input, MetadataSourceEvidence.NONE)).isPresent();
        verify(http, never()).get(any(), eq(false));
    }

    @Test
    void keepsVerifiedSuggestionWhenDetailAccessIsRestricted() {
        when(http.get(any(), eq(false))).thenReturn(Optional.of(SUGGESTIONS));
        when(http.get(DETAIL, true)).thenThrow(ImdbHttpClient.detailsUnavailable());
        var result = provider.enrich(film(), MetadataSourceEvidence.NONE).orElseThrow();

        assertThat(result.getImdbId()).isEqualTo("tt0111161");
        assertThat(result.getPosterUrl()).endsWith("example.jpg");
        assertThat(result.getActorNames()).isEmpty();
        assertThat(result.getDescription()).isNull();
    }

    @Test
    void rejectsAmbiguousSameYearCandidates() {
        when(http.get(any(), eq(false))).thenReturn(Optional.of("""
                {"d":[{"id":"tt0111161","l":"The Shawshank Redemption","qid":"movie","y":1994},
                      {"id":"tt1111161","l":"The Shawshank Redemption","qid":"movie","y":1994}]}
                """));
        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
        verify(http, never()).get(any(), eq(true));
    }

    @Test
    void rejectsDetailRedirectedToAnotherWork() {
        when(http.get(any(), eq(false))).thenReturn(Optional.of(SUGGESTIONS));
        when(http.get(DETAIL, true)).thenReturn(Optional.of(DETAILS.replace("tt0111161", "tt1111161")));
        assertThat(provider.enrich(film(), MetadataSourceEvidence.NONE)).isEmpty();
    }

    @Test
    void malformedSearchResponseIsRetryable() {
        when(http.get(any(), eq(false))).thenReturn(Optional.of("<html>error</html>"));
        assertThatThrownBy(() -> provider.enrich(film(), MetadataSourceEvidence.NONE))
                .isInstanceOf(MetadataProviderRetryableException.class);
    }

    private static MetadataSearchContext film() {
        return MetadataSearchContext.builder()
                .title("肖申克的救赎").aliasTitle("The Shawshank Redemption")
                .year("1994").categorySlug("movie").build();
    }
}
