package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.DoubanTarget;
import com.prodigalgal.ircs.metadata.catalog.DoubanCatalogObservation.MatchBasis;
import com.prodigalgal.ircs.metadata.catalog.DoubanWorkMatcher.TmdbCredit;
import com.prodigalgal.ircs.metadata.catalog.DoubanWorkMatcher.TmdbWorkEvidence;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanHttpClient;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanProviderProperties;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogDoubanSupplementSourceTest {
    private static final String MATCHED_ID = "26266893";
    private static final String OTHER_ID = "1291843";
    private static final String IMDB_ID = "tt7605074";

    private final DoubanHttpClient http = mock(DoubanHttpClient.class);
    private final CatalogDoubanSupplementSource source = new CatalogDoubanSupplementSource(
            http, JsonMapper.builder().build(), new DoubanProviderProperties());

    @Test
    void refreshesTheKnownSubjectWithoutSearchingByTitle() {
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, IMDB_ID)));

        var result = fetchById(null, MATCHED_ID, IMDB_ID).orElseThrow();

        assertThat(result.facts().doubanId()).isEqualTo(MATCHED_ID);
        assertThat(result.facts().title()).isEqualTo("流浪地球");
        assertThat(result.facts().originalTitle()).isEqualTo("The Wandering Earth");
        assertThat(result.facts().year()).isEqualTo("2019");
        assertThat(result.facts().imdbId()).isEqualTo(IMDB_ID);
        assertThat(result.facts().aliases()).containsExactly("流浪地球1", "The Wandering Earth");
        assertThat(result.facts().mainlandReleaseDate()).isEqualTo(LocalDate.of(2019, 2, 5));
        assertThat(result.rating()).isEqualByComparingTo("7.9");
        assertThat(result.voteCount()).isEqualTo(2062586L);
        verify(http).getPublicPage(subject(MATCHED_ID), "text/html");
        verifyNoMoreInteractions(http);
    }

    @Test
    void verifiesCandidatesAgainstTheImdbIdOnTheirDetailPages() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("""
                        [{"id":"1291843","imdb_id":"tt7605074"}, {"id":"26266893"}, {"id":"26266893"}]
                        """));
        when(http.getPublicPage(subject(OTHER_ID), "text/html")).thenReturn(Optional.of(page(OTHER_ID, "tt0133093")));
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, IMDB_ID)));

        var result = fetchById("A&B + C", null, IMDB_ID).orElseThrow();

        assertThat(result.facts().doubanId()).isEqualTo(MATCHED_ID);
        verify(http).getPublicPage(argThat(uri -> "q=A%26B%20%2B%20C".equals(uri.getRawQuery())), eq("application/json"));
        verify(http).getPublicPage(subject(OTHER_ID), "text/html");
        verify(http).getPublicPage(subject(MATCHED_ID), "text/html");
        verifyNoMoreInteractions(http);
    }

    @Test
    void rejectsMultipleVerifiedCandidatesAndConflictingKnownSubjects() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("""
                        [{"id":"1291843"}, {"id":"26266893"}]
                        """));
        when(http.getPublicPage(subject(OTHER_ID), "text/html")).thenReturn(Optional.of(page(OTHER_ID, IMDB_ID)));
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, IMDB_ID)));
        assertThatThrownBy(() -> fetchById("query", null, IMDB_ID))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(CatalogDoubanSupplementSource.IDENTITY_CONFLICT);

        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, "tt0133093")));
        assertThatThrownBy(() -> fetchById("query", MATCHED_ID, IMDB_ID))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(CatalogDoubanSupplementSource.IDENTITY_CONFLICT);
    }

    @Test
    void capsUnverifiedCandidatesAndRejectsAccessDenialsAsUpstreamFailures() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("""
                        [{"id":"1"}, {"id":"2"}, {"id":"3"}, {"id":"4"}]
                        """));
        for (String id : new String[]{"1", "2", "3"}) {
            when(http.getPublicPage(subject(id), "text/html")).thenReturn(Optional.empty());
        }
        assertThat(fetchById("query", null, IMDB_ID)).isEmpty();
        verify(http).getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json"));
        for (String id : new String[]{"1", "2", "3"}) verify(http).getPublicPage(subject(id), "text/html");
        verifyNoMoreInteractions(http);

        when(http.getPublicPage(subject(MATCHED_ID), "text/html"))
                .thenThrow(new MetadataProviderTerminalException("DOUBAN_FORBIDDEN", "test"));
        assertThatThrownBy(() -> fetchById(null, MATCHED_ID, IMDB_ID))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(CatalogDoubanSupplementSource.ACCESS_DENIED);
    }

    @Test
    void doesNotFetchWithoutIdentityAndRejectsMalformedAndOversizedDetailPages() {
        assertThat(fetchById("query", null, null)).isEmpty();
        verifyNoInteractions(http);

        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of("<html>blocked</html>"));
        assertThatThrownBy(() -> fetchById(null, MATCHED_ID, null))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(CatalogDoubanSupplementSource.INVALID_RESPONSE);
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(" ".repeat(1024 * 1024 + 1)));
        assertThatThrownBy(() -> fetchById(null, MATCHED_ID, null))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage("DOUBAN_SUPPLEMENT_RESPONSE_TOO_LARGE");
    }

    @Test
    void discoversMissingExternalIdsUsingTmdbDetailsAndChecksTheOriginalTitle() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("[{\"id\":\"1291843\"}]"), Optional.of("[{\"id\":\"26266893\"}]"));
        when(http.getPublicPage(subject(OTHER_ID), "text/html"))
                .thenReturn(Optional.of(page(OTHER_ID, "tt0133093").replace("郭帆", "另一位导演")));
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, IMDB_ID)));
        var target = target("movie", "流浪地球", "The Wandering Earth", 2019, 0);

        var result = source.fetch(target, () -> evidence(0)).orElseThrow();

        assertThat(result.matchBasis()).isEqualTo(MatchBasis.TMDB_DETAILS);
        assertThat(result.facts().doubanId()).isEqualTo(MATCHED_ID);
        assertThat(result.facts().imdbId()).isEqualTo(IMDB_ID);
        verify(http).getPublicPage(argThat(uri -> "q=The%20Wandering%20Earth".equals(uri.getRawQuery())),
                eq("application/json"));
    }

    @Test
    void acceptsAnUnambiguousDoubanSubjectWithoutAnImdbIdAndRejectsMultipleMatches() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("[{\"id\":\"26266893\"}]"), Optional.of("[{\"id\":\"26266893\"},{\"id\":\"1291843\"}]"));
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(page(MATCHED_ID, "")));
        when(http.getPublicPage(subject(OTHER_ID), "text/html")).thenReturn(Optional.of(page(OTHER_ID, "")));
        var target = target("movie", "流浪地球", null, 2019, 0);

        var result = source.fetch(target, () -> evidence(0)).orElseThrow();
        assertThat(result.facts().imdbId()).isNull();
        assertThatThrownBy(() -> source.fetch(target, () -> evidence(0)))
                .isInstanceOf(CatalogMetadataUnavailableException.class).hasMessage(CatalogDoubanSupplementSource.IDENTITY_CONFLICT);
    }

    @Test
    void verifiesTheScopeOfSingleSeasonSeriesWithoutExternalIds() {
        var html = page(MATCHED_ID, "").replace("上映日期", "首播")
                .replace("<span class=\"pl\">IMDb:", "<span class=\"pl\">集数:</span>12<br><span class=\"pl\">IMDb:");
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("[{\"id\":\"26266893\"}]"));
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(html));
        var target = target("tv", "流浪地球", null, 2019, 1);

        assertThat(source.fetch(target, () -> evidence(12))).isPresent();
        assertThat(source.fetch(target, () -> evidence(11))).isEmpty();
        assertThat(source.fetch(target("tv", "流浪地球", null, 2019, 2), () -> evidence(12))).isEmpty();
        assertThat(source.fetch(target("tv", "流浪地球", null, 2020, 1), () -> evidence(12))).isEmpty();
    }

    @Test
    void countsCorroboratingPeopleRatherThanTheirAliasesAndKeepsKnownImdbAuthority() {
        when(http.getPublicPage(argThat(uri -> uri.getPath().equals("/j/subject_suggest")), eq("application/json")))
                .thenReturn(Optional.of("[{\"id\":\"26266893\"}]"));
        var html = page(MATCHED_ID, "tt0133093").replace("屈楚萧", "Wu Jing");
        when(http.getPublicPage(subject(MATCHED_ID), "text/html")).thenReturn(Optional.of(html));
        var target = target("movie", "流浪地球", null, 2019, 0);
        var onlyActor = new TmdbWorkEvidence(List.of(new TmdbCredit(UUID.randomUUID(), false, "吴京", "Wu Jing")), 0);

        assertThat(source.fetch(target, () -> onlyActor)).isEmpty();
        var withImdb = new DoubanTarget(target.workId(), target.tmdbId(), target.mediaType(), target.title(),
                target.originalTitle(), target.releaseYear(), target.regularSeasonCount(), null, IMDB_ID, null);
        assertThat(source.fetch(withImdb, () -> evidence(0))).isEmpty();
    }

    private Optional<DoubanCatalogObservation> fetchById(String title, String doubanId, String imdbId) {
        var target = new DoubanTarget(UUID.randomUUID(), 535167, "movie", title, null, null, 0, null, imdbId, doubanId);
        return source.fetch(target, () -> {
            throw new AssertionError("External ID matching should not load TMDB credits");
        });
    }

    private static DoubanTarget target(String mediaType, String title, String originalTitle, int year, int regularSeasons) {
        return new DoubanTarget(UUID.randomUUID(), 535167, mediaType, title, originalTitle, year, regularSeasons, null,
                null, null);
    }

    private static TmdbWorkEvidence evidence(int episodes) {
        return new TmdbWorkEvidence(List.of(new TmdbCredit(UUID.randomUUID(), true, "郭帆", "Frant Gwo"),
                new TmdbCredit(UUID.randomUUID(), false, "吴京", "Wu Jing"),
                new TmdbCredit(UUID.randomUUID(), false, "屈楚萧", null)), episodes);
    }

    private static URI subject(String id) {
        return URI.create("https://movie.douban.com/subject/" + id + "/");
    }

    private static String page(String id, String imdbId) {
        return """
                <link rel="canonical" href="https://movie.douban.com/subject/%s/">
                <h1><span property="v:itemreviewed">流浪地球</span><span class="year">(2019)</span></h1>
                <div id="info"><span class="pl">原名:</span>The Wandering Earth<br>
                <span class="pl">导演:</span><a property="v:directedBy">郭帆</a><br>
                <span class="pl">主演:</span><a property="v:starring">吴京</a> / <a property="v:starring">屈楚萧</a><br>
                <span class="pl">又名:</span>流浪地球1 / The Wandering Earth<br>
                <span class="pl">上映日期:</span><span property="v:initialReleaseDate">2019-02-05(中国大陆)</span><br>
                <span class="pl">IMDb:</span>%s<br></div>
                <strong property="v:average">7.9</strong>
                <span property="v:votes">2062586</span>
                """.formatted(id, imdbId);
    }
}
