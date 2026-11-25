package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.metadata.provider.douban.DoubanCredentialRepository;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanHttpClient;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanProviderProperties;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogDoubanSupplementSourceTest {
    private final DoubanCredentialRepository credentials = mock(DoubanCredentialRepository.class);
    private final DoubanHttpClient http = mock(DoubanHttpClient.class);
    private CatalogDoubanSupplementSource source;

    @BeforeEach
    void setUp() { source = new CatalogDoubanSupplementSource(credentials, http, JsonMapper.builder().build(), new DoubanProviderProperties()); }

    @Test
    void knownDoubanIdSelectsExactCandidateAndMapsOnlyPresentFields() {
        when(http.getJson(any(), any())).thenReturn(Optional.of("""
                [{"id":"88","title":"wrong"},{"id":"1291843","title":"黑客帝国","sub_title":"原名：The Matrix","year":"1999","rating":"8.7","imdb_id":"tt0133093"}]
                """));
        var result = source.fetch("Matrix query", "1291843", "tt0133093").orElseThrow();
        assertThat(result.getDoubanId()).isEqualTo("1291843");
        assertThat(result.getTitle()).isEqualTo("黑客帝国");
        assertThat(result.getOriginalTitle()).isEqualTo("The Matrix");
        assertThat(result.getYear()).isEqualTo("1999");
        assertThat(result.getScore()).isEqualByComparingTo("8.7");
        assertThat(result.getImdbId()).isEqualTo("tt0133093");
        verify(http).getJson(argThat(uri -> uri.toString().contains("q=Matrix%20query")), argThat(Optional::isEmpty));
    }

    @Test
    void requiresExactReturnedImdbWhenDoubanIdIsNotKnownAndRejectsAmbiguousMatches() {
        when(http.getJson(any(), any())).thenReturn(Optional.of("""
                [{"id":"1291843","title":"Anything","imdb_id":"tt0133093"}]
                """));
        assertThat(source.fetch("query title", null, "tt0133093")).isPresent();
        when(http.getJson(any(), any())).thenReturn(Optional.of("""
                [{"id":"1291843","imdb_id":"tt0133093"},{"id":"1291844","imdb_id":"tt0133093"}]
                """));
        assertThat(source.fetch("query title", null, "tt0133093")).isEmpty();
    }

    @Test
    void rejectsExplicitIdentityConflictAndMalformedHtmlInsteadOfNoMatch() {
        when(http.getJson(any(), any())).thenReturn(Optional.of("""
                [{"id":"1291843","imdb_id":"tt9999999"}]
                """));
        assertThatThrownBy(() -> source.fetch("query", "1291843", "tt0133093"))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage("DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT");
        when(http.getJson(any(), any())).thenReturn(Optional.of("<html>blocked</html>"));
        assertThatThrownBy(() -> source.fetch("query", "1291843", null))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage("DOUBAN_SUPPLEMENT_INVALID_RESPONSE");
    }

    @Test
    void doesNotFetchWithoutVerifiedIdentityAndRejectsOversizedResponse() {
        assertThat(source.fetch("query", null, null)).isEmpty();
        when(http.getJson(any(), any())).thenReturn(Optional.of(" ".repeat(512 * 1024 + 1)));
        assertThatThrownBy(() -> source.fetch("query", "1291843", null))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage("DOUBAN_SUPPLEMENT_RESPONSE_TOO_LARGE");
    }
}
