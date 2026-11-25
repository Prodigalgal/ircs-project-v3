package com.prodigalgal.ircs.metadata.provider.douban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoubanMetadataProviderTest {

    @Mock
    private DoubanCredentialRepository credentialRepository;

    @Mock
    private DoubanHttpClient httpClient;

    private DoubanMetadataProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DoubanMetadataProvider(
                credentialRepository,
                httpClient,
                new ObjectMapper(),
                new DoubanProviderProperties());
    }

    @Test
    void enrichesMatchingTitleWithoutCredential() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("The Matrix")
                .categorySlug("movie")
                .year("1999")
                .build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), argThat(Optional::isEmpty))).thenReturn(response(200, """
                [
                  {
                    "id":"1291843",
                    "title":"黑客帝国",
                    "sub_title":"The Matrix",
                    "year":"1999",
                    "img":"https://img.example.invalid/matrix.jpg"
                  }
                ]
                """), response(200, detailHtml("1291843", "黑客帝国", "The Matrix", "1999", "tt0133093")));

        Optional<EnrichedMetadataDTO> result = provider.enrich(context);

        assertThat(result).isPresent();
        EnrichedMetadataDTO metadata = result.orElseThrow();
        assertThat(metadata.getDoubanId()).isEqualTo("1291843");
        assertThat(metadata.getTitle()).isEqualTo("黑客帝国");
        assertThat(metadata.getOriginalTitle()).isEqualTo("The Matrix");
        assertThat(metadata.getYear()).isEqualTo("1999");
        assertThat(metadata.getImdbId()).isEqualTo("tt0133093");
        assertThat(metadata.getDescription()).isEqualTo("A hacker discovers reality.");
        assertThat(metadata.getScore()).isEqualByComparingTo("9.0");
        assertThat(metadata.getDirectorNames()).contains("Lana Wachowski");
        assertThat(metadata.getActorNames()).contains("Keanu Reeves");
        assertThat(metadata.getGenreNames()).contains("动作");
        assertThat(metadata.getImageSource()).isEqualTo(ProviderType.DOUBAN);
        verify(credentialRepository).findPreferred();
        verify(httpClient).getText(argThat(this::isSuggestUri), any());
    }

    @Test
    void returnsEmptyWhenSuggestResultDoesNotMatch() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("The Matrix")
                .year("1999")
                .build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(response(200, """
                [{"id":"1","title":"Unrelated","sub_title":"Other","year":"1980"}]
                """));

        assertThat(provider.enrich(context)).isEmpty();
    }

    @Test
    void doesNotChooseBetweenMultipleMatchingSubjects() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID()).title("The Matrix").year("1999").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(response(200, """
                [{"id":"1291843","title":"黑客帝国","sub_title":"The Matrix","year":"1999"},
                 {"id":"999999","title":"The Matrix","year":"1999"}]
                """));

        assertThat(provider.enrich(context)).isEmpty();
        verify(httpClient, org.mockito.Mockito.never()).getText(
                argThat(uri -> uri.toString().contains("/subject/")), any());
    }

    @Test
    void usesOptionalCredentialWhenPresent() {
        DoubanCredential credential = new DoubanCredential(UUID.randomUUID(), "dbcl2=secret", "UA", 1, "MINUTE", 0);
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("黑客帝国")
                .build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.of(credential));
        when(httpClient.getText(any(), argThat(optional -> optional.isPresent()
                && optional.orElseThrow().cookie().equals("dbcl2=secret"))))
                .thenReturn(response(200, "[]"));

        assertThat(provider.enrich(context)).isEmpty();
    }

    @Test
    void doesNotSupportBlankTitle() {
        assertFalse(provider.supports(MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("")
                .build()));
    }

    @Test
    void distinguishesMalformedProviderEnvelopeFromValidEmptyResult() {
        var context = MetadataSearchContext.builder().title("Example").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(response(200, "{\"error\":\"blocked\"}"), response(200, "[]"));

        var failure = org.junit.jupiter.api.Assertions.assertThrows(
                com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException.class,
                () -> provider.enrich(context));

        assertThat(failure.getErrorCode()).isEqualTo("PROVIDER_PARSE_ERROR");
        assertThat(provider.enrich(context)).isEmpty();
    }

    @Test
    void skipsSequelAndCandidateWithoutIdentifier() {
        var context = MetadataSearchContext.builder().title("Alien").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(response(200, """
                [{"id":"26825482","title":"Alien Covenant"},
                 {"title":"Alien"}, {"id":"1300868","title":"Alien"}]
                """), response(200, detailHtml("1300868", "Alien", "Alien", "1979", null)));

        assertThat(provider.enrich(context).orElseThrow().getDoubanId()).isEqualTo("1300868");
    }

    @Test
    void returnsVerifiedPartialCandidateWhenDetailIsBlocked() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID()).title("The Matrix").year("1999").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(
                response(200, "[{\"id\":\"1291843\",\"title\":\"黑客帝国\",\"sub_title\":\"The Matrix\",\"year\":\"1999\",\"img\":\"https://img.example.invalid/matrix.jpg\"}]"),
                response(202, "<html><title>安全验证</title><body>captcha</body></html>"));

        EnrichedMetadataDTO metadata = provider.enrich(context).orElseThrow();

        assertThat(metadata.getDoubanId()).isEqualTo("1291843");
        assertThat(metadata.getTitle()).isEqualTo("黑客帝国");
        assertThat(metadata.getYear()).isEqualTo("1999");
        assertThat(metadata.getPosterUrl()).isEqualTo("https://img.example.invalid/matrix.jpg");
        assertThat(metadata.getDescription()).isNull();
        assertThat(metadata.getScore()).isNull();
    }

    @Test
    void looksUpKnownIdDirectlyAndRejectsConflictingImdbIdentity() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID()).title("The Matrix").year("1999")
                .doubanId("1291843").imdbId("tt0234215").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(
                response(200, detailHtml("1291843", "黑客帝国", "The Matrix", "1999", "tt0133093")));

        assertThat(provider.enrich(context)).isEmpty();
        verify(httpClient).getText(argThat(uri -> uri.toString().equals(
                "https://movie.douban.com/subject/1291843/")), any());
    }

    @Test
    void rejectsDetailsWhoseCanonicalIdentityDoesNotMatchRequestedSubject() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID()).title("The Matrix").doubanId("1291843").build();
        when(credentialRepository.findPreferred()).thenReturn(Optional.empty());
        when(httpClient.getText(any(), any())).thenReturn(
                response(200, detailHtml("999", "黑客帝国", "The Matrix", "1999", "tt0133093")));

        assertThat(provider.enrich(context)).isEmpty();
    }

    private DoubanHttpResponse response(int status, String body) {
        return new DoubanHttpResponse(status, body);
    }

    private String detailHtml(String id, String title, String originalTitle, String year, String imdbId) {
        return """
                <html><head>
                  <link rel="canonical" href="https://movie.douban.com/subject/%s/">
                  <meta property="og:url" content="https://movie.douban.com/subject/%s/">
                  <meta property="og:title" content="%s (豆瓣)">
                  <meta property="og:image" content="https://img.example.invalid/poster.jpg">
                </head><body>
                  <h1><span property="v:itemreviewed">%s</span><span class="year">(%s)</span></h1>
                  <div id="info"><span class="pl">原名:</span> <span>%s</span><br>
                    <span class="pl">上映日期:</span> 1999-03-31<br>
                    <span class="pl">IMDb:</span> %s<br></div>
                  <div id="link-report"><span property="v:summary">A hacker discovers reality.</span></div>
                  <strong property="v:average" content="9.0">9.0</strong>
                  <a rel="v:directedBy">Lana Wachowski</a><a rel="v:starring">Keanu Reeves</a>
                  <span property="v:genre">动作</span>
                </body></html>
                """.formatted(id, id, title, title, year, originalTitle, imdbId == null ? "" : imdbId);
    }

    private boolean isSuggestUri(URI uri) {
        return uri != null
                && uri.toString().startsWith("https://movie.douban.com/j/subject_suggest")
                && uri.toString().contains("q=The%20Matrix");
    }
}
