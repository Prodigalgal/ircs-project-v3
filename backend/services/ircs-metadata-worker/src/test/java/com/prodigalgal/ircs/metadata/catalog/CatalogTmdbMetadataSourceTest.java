package com.prodigalgal.ircs.metadata.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbCredential;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbCredentialRepository;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbHttpClient;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbProviderProperties;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbRateLimiter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CatalogTmdbMetadataSourceTest {
    private final TmdbCredentialRepository credentials=mock(TmdbCredentialRepository.class);
    private final TmdbHttpClient http=mock(TmdbHttpClient.class);
    private CatalogTmdbMetadataSource source() {
        when(credentials.findEnabled()).thenReturn(List.of(new TmdbCredential(UUID.randomUUID(),"fixture-key",30,"MINUTE",0)));
        return new CatalogTmdbMetadataSource(credentials,mock(TmdbRateLimiter.class),http,
                new TmdbProviderProperties(),JsonMapper.builder().findAndAddModules().build());
    }
    @Test
    void missingIdentityNeverTurnsTheQueryIdIntoReturnedEvidence() {
        var source=source();
        when(http.getJson(any())).thenReturn(Optional.of("{\"title\":\"Film\",\"release_date\":\"2026-11-01\"}"));
        assertThatThrownBy(()->source.fetch(target("movie",0,"42"))).isInstanceOf(CatalogMetadataUnavailableException.class);
    }
    @Test
    void conflictingIdAndMovieTvShapeAreRejected() {
        var source=source();
        for(String json:List.of("{\"id\":99,\"title\":\"Film\",\"release_date\":\"2026-11-01\"}",
                "{\"id\":42,\"name\":\"Series\",\"first_air_date\":\"2026-11-01\"}")) {
            when(http.getJson(any())).thenReturn(Optional.of(json));
            assertThatThrownBy(()->source.fetch(target("movie",0,"42"))).isInstanceOf(CatalogMetadataUnavailableException.class);
        }
    }
    @Test
    void validIdentityPreservesUnknownScoreAndNormalizesCredits() {
        var source=source();
        when(http.getJson(any())).thenReturn(Optional.of("""
                {"id":42,"title":"Film","original_title":"Original","release_date":"2026-11-01",
                 "vote_count":0,"vote_average":0,"credits":{"cast":[{"name":"Actor"}],"crew":[{"name":"Director","job":"Director"}]}}
                """));
        var facts=source.fetch(target("movie",0,"42"));
        assertThat(facts.tmdbId()).isEqualTo("42");assertThat(facts.metadata().getScore()).isNull();
        assertThat(facts.metadata().getActorNames()).containsExactly("Actor");
        assertThat(facts.metadata().getDirectorNames()).containsExactly("Director");
    }
    @Test
    void seasonIsVerifiedAgainstShowSeasonsAndReturnedSeasonId() {
        var source=source();
        when(http.getJson(any())).thenAnswer(call-> ((java.net.URI)call.getArgument(0)).getPath().contains("/season/")
                ? Optional.of("{\"id\":501,\"season_number\":3,\"air_date\":\"2026-11-01\",\"episodes\":[{\"show_id\":42}]}")
                : Optional.of("{\"id\":42,\"name\":\"Series\",\"first_air_date\":\"2020-01-01\",\"seasons\":[{\"id\":501,\"season_number\":2}]}"));
        assertThatThrownBy(()->source.fetch(target("tv",2,"42"))).isInstanceOf(CatalogMetadataUnavailableException.class);
    }
    @Test
    void noVerifiedProviderAndAdultSourceStayUnavailable() {
        var source=source();
        assertThatThrownBy(()->source.fetch(target("movie",0,null))).isInstanceOf(CatalogMetadataUnavailableException.class);
        when(http.getJson(any())).thenReturn(Optional.of("{\"id\":42,\"title\":\"Film\",\"release_date\":\"2026-11-01\",\"adult\":true}"));
        assertThatThrownBy(()->source.fetch(target("movie",0,"42"))).isInstanceOf(CatalogMetadataUnavailableException.class);
    }

    @Test
    void generalTvImportSelectsARegularSeasonAndKeepsSpecialsDistinct() {
        var source = source();
        when(http.getJson(any())).thenAnswer(call -> {
            String path = ((java.net.URI)call.getArgument(0)).getPath();
            if (path.endsWith("/season/0")) return Optional.of("""
                    {"id":500,"season_number":0,"episodes":[
                        {"id":95,"episode_number":1,"season_number":0,"show_id":42,"air_date":"2026-11-22"}]}
                    """);
            if (path.endsWith("/season/1")) return Optional.of("""
                    {"id":501,"season_number":1,"episodes":[
                        {"id":98,"episode_number":1,"season_number":1,"show_id":42,"air_date":"2026-11-11"}]}
                    """);
            return Optional.of("""
                    {"id":42,"name":"Example Show","first_air_date":"2026-11-11",
                     "seasons":[{"id":500,"season_number":0},{"id":501,"season_number":1}]}
                    """);
        });
        var regular = source.fetchAuthority("tv",42,-1);
        var specials = source.fetchAuthority("tv",42,0);
        assertThat(regular.seasonNumber()).isEqualTo(1);
        assertThat(regular.episodes()).extracting(AuthorityCatalogEvidence.Episode::tmdbId).containsExactly(98L);
        assertThat(specials.seasonNumber()).isZero();
        assertThat(specials.episodes()).extracting(AuthorityCatalogEvidence.Episode::tmdbId).containsExactly(95L);
    }
    private CatalogMetadataTarget target(String type,int season,String tmdb) {
        return new CatalogMetadataTarget(UUID.randomUUID(),UUID.randomUUID(),"TMDB",type,"42",season,1,"Title",null,null,tmdb);
    }
}
