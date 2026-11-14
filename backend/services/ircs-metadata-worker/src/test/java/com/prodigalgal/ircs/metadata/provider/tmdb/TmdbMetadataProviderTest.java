package com.prodigalgal.ircs.metadata.provider.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TmdbMetadataProviderTest {

    @Mock
    private TmdbCredentialRepository credentialRepository;

    @Mock
    private TmdbRateLimiter rateLimiter;

    @Mock
    private TmdbHttpClient httpClient;

    private TmdbCredential credential;
    private TmdbMetadataProvider provider;

    @BeforeEach
    void setUp() {
        TmdbProviderProperties properties = new TmdbProviderProperties();
        credential = new TmdbCredential(UUID.randomUUID(), "test-key", 30, "MINUTE", 0);
        provider = new TmdbMetadataProvider(
                credentialRepository,
                rateLimiter,
                httpClient,
                new ObjectMapper(),
                properties);
    }

    @Test
    void enrichesMatchingMovieWithDetails() {
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("The Matrix")
                .categorySlug("movie")
                .year("1999")
                .build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(argThat(this::isMovieSearchUri))).thenReturn(Optional.of("""
                {"total_pages":1,"results":[
                  {"id":603,"title":"The Matrix","original_title":"The Matrix","release_date":"1999-03-31"}
                ]}
                """));
        when(httpClient.getJson(argThat(this::isMovieDetailUri))).thenReturn(Optional.of("""
                {
                  "title":"The Matrix",
                  "original_title":"The Matrix",
                  "overview":"A hacker discovers reality.",
                  "poster_path":"/matrix.jpg",
                  "backdrop_path":"/matrix-bg.jpg",
                  "vote_average":8.2,
                  "release_date":"1999-03-31",
                  "external_ids":{"imdb_id":"tt0133093"},
                  "production_countries":[{"name":"United States"}],
                  "genres":[{"name":"Science Fiction"}],
                  "credits":{
                    "cast":[{"name":"Keanu Reeves"}],
                    "crew":[{"job":"Director","name":"Lana Wachowski"}]
                  }
                }
                """));

        Optional<EnrichedMetadataDTO> result = provider.enrich(context);

        assertTrue(result.isPresent());
        EnrichedMetadataDTO metadata = result.get();
        assertEquals("603", metadata.getTmdbId());
        assertEquals("tt0133093", metadata.getImdbId());
        assertEquals("The Matrix", metadata.getTitle());
        assertEquals("1999", metadata.getYear());
        assertEquals(ProviderType.TMDB, metadata.getImageSource());
        assertTrue(metadata.getActorNames().contains("Keanu Reeves"));
        assertTrue(metadata.getDirectorNames().contains("Lana Wachowski"));
        assertTrue(metadata.getGenreNames().contains("Science Fiction"));
        verify(rateLimiter, times(2)).acquire(credential);
    }

    @Test
    void rotatesStartingCredentialAcrossRequests() {
        TmdbCredential first = new TmdbCredential(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "key-1",
                30,
                "MINUTE",
                0);
        TmdbCredential second = new TmdbCredential(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "key-2",
                30,
                "MINUTE",
                0);
        MetadataSearchContext context = MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("No Match")
                .categorySlug("movie")
                .build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(first, second));
        when(httpClient.getJson(any(URI.class))).thenReturn(Optional.of("""
                {"total_pages":1,"results":[]}
                """));

        provider.enrich(context);
        provider.enrich(context);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(httpClient, times(2)).getJson(uriCaptor.capture());
        assertTrue(uriCaptor.getAllValues().get(0).toString().contains("api_key=key-1"));
        assertTrue(uriCaptor.getAllValues().get(1).toString().contains("api_key=key-2"));
        verify(rateLimiter).acquire(first);
        verify(rateLimiter).acquire(second);
    }

    @Test
    void throwsRetryableFailureWhenNoCredentialsExist() {
        when(credentialRepository.findEnabled()).thenReturn(List.of());

        MetadataProviderRetryableException ex = assertThrows(
                MetadataProviderRetryableException.class,
                () -> provider.enrich(MetadataSearchContext.builder()
                        .videoId(UUID.randomUUID())
                        .title("The Matrix")
                        .build()));

        assertEquals("POOL_EXHAUSTED", ex.getErrorCode());
    }

    @Test
    void doesNotSupportBlankTitle() {
        assertFalse(provider.supports(MetadataSearchContext.builder()
                .videoId(UUID.randomUUID())
                .title("")
                .build()));
    }

    @Test
    void distinguishesInvalidSearchEnvelopeFromNoMatch() {
        var context = MetadataSearchContext.builder().title("Example").categorySlug("movie").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(any(URI.class))).thenReturn(Optional.of("{\"success\":false}"),
                Optional.of("{\"results\":[],\"total_pages\":1}"));

        var failure = assertThrows(MetadataProviderRetryableException.class, () -> provider.enrich(context));

        assertEquals("PROVIDER_PARSE_ERROR", failure.getErrorCode());
        assertTrue(provider.enrich(context).isEmpty());
    }

    @Test
    void retriesMalformedDetailsInsteadOfReportingNoMatch() {
        var context = MetadataSearchContext.builder().title("The Matrix").categorySlug("movie").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(argThat(this::isMovieSearchUri))).thenReturn(Optional.of("""
                {"results":[{"id":603,"title":"The Matrix"}]}
                """));
        when(httpClient.getJson(argThat(this::isMovieDetailUri))).thenReturn(Optional.of("{\"success\":false}"));

        var failure = assertThrows(MetadataProviderRetryableException.class, () -> provider.enrich(context));

        assertEquals("PROVIDER_PARSE_ERROR", failure.getErrorCode());
    }

    @Test
    void ignoresWrongMediaTypeInvalidIdentifierAndSequelBeforeExactMatch() {
        var context = MetadataSearchContext.builder().title("The Matrix").categorySlug("movie").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(argThat(this::isMovieSearchUri))).thenReturn(Optional.of("""
                {"results":[{"id":1,"media_type":"tv","name":"The Matrix"},
                  {"title":"The Matrix"}, {"id":2,"title":"The Matrix Reloaded"},
                  {"id":603,"title":"The Matrix"}]}
                """));
        when(httpClient.getJson(argThat(this::isMovieDetailUri))).thenReturn(Optional.of("{\"title\":\"The Matrix\"}"));

        assertEquals("603", provider.enrich(context).orElseThrow().getTmdbId());
        verify(httpClient, times(2)).getJson(any(URI.class));
    }

    @Test
    void seasonZeroUsesTvEndpointEvenWithoutCategoryAndPreservesSupportedYearFilter() {
        var context = MetadataSearchContext.builder().title("Example").season(0).year("2026").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(any(URI.class))).thenReturn(Optional.of("{\"results\":[]}"));

        assertTrue(provider.enrich(context).isEmpty());

        verify(httpClient).getJson(argThat(uri -> uri.getPath().endsWith("/search/tv")
                && uri.getQuery().contains("year=2026")
                && !uri.getQuery().contains("first_air_date_year")));
    }

    @Test
    void movieKeepsYearWhileMultiDoesNotSendUnsupportedYearParameter() {
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(any(URI.class))).thenReturn(Optional.of("{\"results\":[],\"total_pages\":1}"));

        provider.enrich(MetadataSearchContext.builder().title("Example").categorySlug("movie").year("2026").build());
        provider.enrich(MetadataSearchContext.builder().title("Example").year("2026").build());

        verify(httpClient).getJson(argThat(uri -> uri.getPath().endsWith("/search/movie")
                && uri.getQuery().contains("year=2026")));
        verify(httpClient, times(3)).getJson(argThat(uri -> uri.getPath().endsWith("/search/multi")
                && !uri.getQuery().contains("year=")));
    }

    @Test
    void validatesLaterSeasonAgainstSeasonAirDateInsteadOfSeriesPremiere() {
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        stubSeasonResponses("{\"season_number\":5,\"air_date\":\"2026-05-12\"}");

        var result = provider.enrich(context).orElseThrow();

        assertEquals("2026", result.getYear());
        assertEquals(java.time.LocalDate.of(2026, 5, 12), result.getPublishedAt());
        verify(rateLimiter, times(3)).acquire(credential);
        verify(httpClient).getJson(argThat(uri -> uri.getPath().endsWith("/tv/101/season/5")));
    }

    @Test
    void validatesSeasonZeroUsingSpecialsAirDate() {
        var context = MetadataSearchContext.builder().title("Example").season(0).year("2026").build();
        stubSeasonResponses("{\"season_number\":0,\"air_date\":\"2026-02-01\"}");

        var result = provider.enrich(context).orElseThrow();

        assertEquals("2026", result.getYear());
        verify(httpClient).getJson(argThat(uri -> uri.getPath().endsWith("/tv/101/season/0")));
    }

    @Test
    void rejectsSeasonYearConflictAndMissingOrWrongSeasonEvidence() {
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        for (String response : new String[] {
                "{\"season_number\":5,\"air_date\":\"2020-02-01\"}",
                "{\"season_number\":4,\"air_date\":\"2026-02-01\"}",
                "{\"season_number\":5,\"air_date\":null}",
                "{\"air_date\":\"2026-02-01\"}"}) {
            stubSeasonResponses(response);
            assertTrue(provider.enrich(context).isEmpty(), response);
        }
    }

    @Test
    void seasonParseErrorsRemainRetryable() {
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        stubSeasonResponses("[]");

        var failure = assertThrows(MetadataProviderRetryableException.class, () -> provider.enrich(context));

        assertEquals("PROVIDER_PARSE_ERROR", failure.getErrorCode());
    }

    @Test
    void hydratesAtMostTwoSeasonCandidatesAndDoesNotConfirmFromSeriesDateAlone() {
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(any(URI.class))).thenAnswer(invocation -> {
            String path = ((URI) invocation.getArgument(0)).getPath();
            if (path.endsWith("/search/tv")) {
                return Optional.of("""
                        {"results":[{"id":101,"name":"Example","first_air_date":"2026-01-01"},
                          {"id":102,"name":"Example","first_air_date":"2026-01-01"},
                          {"id":103,"name":"Example","first_air_date":"2026-01-01"}]}
                        """);
            }
            if (path.contains("/season/")) {
                return Optional.of("{\"season_number\":5}");
            }
            return Optional.of("{\"name\":\"Example\",\"first_air_date\":\"2026-01-01\"}");
        });

        assertTrue(provider.enrich(context).isEmpty());

        verify(httpClient, times(5)).getJson(any(URI.class));
        verify(httpClient, org.mockito.Mockito.never()).getJson(argThat(uri -> uri.getPath().contains("/tv/103")));
        verify(rateLimiter, times(5)).acquire(credential);
    }

    @Test
    void continuesToSecondCandidateAfterSeasonMismatch() {
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        when(httpClient.getJson(any(URI.class))).thenAnswer(invocation -> {
            String path = ((URI) invocation.getArgument(0)).getPath();
            if (path.endsWith("/search/tv")) {
                return Optional.of("{\"results\":[{\"id\":101,\"name\":\"Example\"},{\"id\":102,\"name\":\"Example\"}]}");
            }
            if (path.endsWith("/101/season/5")) {
                return Optional.of("{\"season_number\":5,\"air_date\":\"2020-01-01\"}");
            }
            if (path.endsWith("/102/season/5")) {
                return Optional.of("{\"season_number\":5,\"air_date\":\"2026-01-01\"}");
            }
            return Optional.of("{\"name\":\"Example\",\"first_air_date\":\"2019-01-01\"}");
        });

        assertEquals("102", provider.enrich(context).orElseThrow().getTmdbId());
        verify(httpClient, times(5)).getJson(any(URI.class));
    }

    @Test
    void sharesSeasonHydrationBudgetAcrossCredentialsAndRetainsEarlierRetryableFailure() {
        TmdbCredential second = new TmdbCredential(UUID.randomUUID(), "second-key", 30, "MINUTE", 0);
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential, second));
        when(httpClient.getJson(any(URI.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().endsWith("/search/tv")) {
                return Optional.of("{\"results\":[{\"id\":101,\"name\":\"Example\"},{\"id\":102,\"name\":\"Example\"}]}");
            }
            if (uri.getQuery().contains("api_key=test-key")) {
                throw new MetadataProviderRetryableException("PROVIDER_NETWORK_ERROR", "First detail request failed");
            }
            if (uri.getPath().contains("/season/")) {
                return Optional.of("{\"season_number\":5,\"air_date\":\"2020-01-01\"}");
            }
            return Optional.of("{\"name\":\"Example\",\"first_air_date\":\"2019-01-01\"}");
        });

        var failure = assertThrows(MetadataProviderRetryableException.class, () -> provider.enrich(context));

        assertEquals("PROVIDER_NETWORK_ERROR", failure.getErrorCode());
        verify(httpClient, times(5)).getJson(any(URI.class));
        verify(httpClient, org.mockito.Mockito.never()).getJson(argThat(uri -> uri.getPath().contains("/tv/102")));
        verify(rateLimiter, times(2)).acquire(credential);
        verify(rateLimiter, times(3)).acquire(second);
    }

    @Test
    void doesNotStartAnotherCredentialAfterTwoHydrationsEndInDetailFailure() {
        TmdbCredential second = new TmdbCredential(UUID.randomUUID(), "second-key", 30, "MINUTE", 0);
        var context = MetadataSearchContext.builder().title("Example").season(5).year("2026").build();
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential, second));
        when(httpClient.getJson(any(URI.class))).thenAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            if (uri.getPath().endsWith("/search/tv")) {
                return Optional.of("{\"results\":[{\"id\":101,\"name\":\"Example\"},{\"id\":102,\"name\":\"Example\"}]}");
            }
            if (uri.getPath().endsWith("/tv/102")) {
                throw new MetadataProviderRetryableException("PROVIDER_NETWORK_ERROR", "Second detail request failed");
            }
            if (uri.getPath().contains("/season/")) {
                return Optional.of("{\"season_number\":5}");
            }
            return Optional.of("{\"name\":\"Example\"}");
        });

        var failure = assertThrows(MetadataProviderRetryableException.class, () -> provider.enrich(context));

        assertEquals("PROVIDER_NETWORK_ERROR", failure.getErrorCode());
        verify(httpClient, times(4)).getJson(any(URI.class));
        verify(rateLimiter, org.mockito.Mockito.never()).acquire(second);
        verify(httpClient, org.mockito.Mockito.never()).getJson(argThat(uri -> uri.getQuery().contains("second-key")));
    }

    private void stubSeasonResponses(String seasonResponse) {
        when(credentialRepository.findEnabled()).thenReturn(List.of(credential));
        org.mockito.Mockito.doAnswer(invocation -> {
            String path = ((URI) invocation.getArgument(0)).getPath();
            if (path.endsWith("/search/tv")) {
                return Optional.of("{\"results\":[{\"id\":101,\"name\":\"Example\",\"first_air_date\":\"2019-01-01\"}]}");
            }
            if (path.contains("/season/")) {
                return Optional.of(seasonResponse);
            }
            return Optional.of("{\"name\":\"Example\",\"first_air_date\":\"2019-01-01\"}");
        }).when(httpClient).getJson(any(URI.class));
    }

    private boolean isMovieSearchUri(URI uri) {
        return uri != null && uri.toString().contains("/search/movie");
    }

    private boolean isMovieDetailUri(URI uri) {
        return uri != null && uri.toString().contains("/movie/603");
    }
}
