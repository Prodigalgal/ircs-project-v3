package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AggregationCandidateRepositoryTest {

    @Test
    void batchesExternalRecallWithPerInputLimitsAndEnrichesDeduplicatedCandidatesOnce() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        RawVideoAggregationRecord first = externalRaw("douban-first");
        RawVideoAggregationRecord second = externalRaw("douban-second");
        UUID sharedId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(2);
            return List.of(
                    mapper.mapRow(row(0, sharedId), 0),
                    mapper.mapRow(row(1, sharedId), 1),
                    mapper.mapRow(row(1, secondId), 2));
        });
        when(metadata.populateUnifiedMetadata(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<UnifiedVideoAggregationCandidate> candidates = repository.findContextUnifiedCandidates(List.of(first, second));

        assertThat(candidates).extracting(UnifiedVideoAggregationCandidate::id).containsExactly(sharedId, secondId);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), anyRowMapper());
        assertThat(sql.getValue()).contains("union all", "context_index", "uv.douban_id = :doubanId0",
                "uv.douban_id = :doubanId1", "order by context_index, context_updated_at desc");
        assertThat(sql.getValue().split("limit :limit", -1)).hasSize(3);
        assertThat(params.getValue().getValue("limit")).isEqualTo(20);
        assertThat(params.getValue().getValue("doubanId0")).isEqualTo("douban-first");
        assertThat(params.getValue().getValue("doubanId1")).isEqualTo("douban-second");
        verify(metadata).populateUnifiedMetadata(candidates);
    }

    @Test
    void boundsUnionQuerySizeForLargeContextBatches() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        RawVideoAggregationRecord raw = externalRaw("douban-batch");
        List<RawVideoAggregationRecord> input = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            input.add(raw);
        }
        when(search.findCandidateUnifiedVideoIds(anyString(), anyString()))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.notAttempted());
        AtomicInteger batchIndex = new AtomicInteger();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.startsWith("with context_candidates")) {
                return List.of();
            }
            RowMapper<?> mapper = invocation.getArgument(2);
            List<Object> rows = new ArrayList<>();
            int start = batchIndex.getAndIncrement() * 100;
            int count = Math.min(input.size() - start, 100);
            for (int index = 0; index < count; index++) {
                rows.add(mapper.mapRow(row(index, UUID.randomUUID()), index));
            }
            return rows;
        });
        when(metadata.populateUnifiedMetadata(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(repository.findContextUnifiedCandidates(input)).hasSize(201);

        verify(jdbc, times(3)).query(anyString(), any(MapSqlParameterSource.class), anyRowMapper());
        verify(metadata).populateUnifiedMetadata(anyList());
    }

    @Test
    void rejectsPlaceholderIdsWithoutSkippingTitleRecall() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, strategy, mock(AggregationContextSearchClient.class), metadata);
        RawVideoAggregationRecord raw = rawVideo(
                "Codex Signal", "2026", "123", "0", "unknown", "-");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenReturn(List.of());

        assertThat(repository.findMatchPlan(raw).hasRoot()).isFalse();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), anyRowMapper());
        assertThat(sql.getValue()).contains("similarity(title, :title)", "limit :limit")
                .doesNotContain("douban_id =", "tmdb_id =", "imdb_id =", "rotten_tomatoes_id =");
        assertThat(params.getValue().getValue("limit")).isEqualTo(20);
        verifyNoInteractions(metadata);
    }

    @Test
    void contextBatchRecallsAllFourUsableExternalIdProviders() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        RawVideoAggregationRecord first = rawVideo("Codex One", "2026", "d1", "t1", "i1", "r1");
        RawVideoAggregationRecord second = rawVideo("Codex Two", "2026", "d2", "t2", "i2", "r2");
        when(search.findCandidateUnifiedVideoIds(anyString(), anyString()))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.notAttempted());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("with context_candidates") ? List.of() : List.of();
        });
        when(metadata.populateUnifiedMetadata(anyList())).thenReturn(List.of());

        assertThat(repository.findContextUnifiedCandidates(List.of(first, second))).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).query(sql.capture(), params.capture(), anyRowMapper());
        String batchSql = sql.getAllValues().stream()
                .filter(value -> value.startsWith("with context_candidates"))
                .findFirst()
                .orElseThrow();
        assertThat(batchSql).contains(
                "uv.douban_id = :doubanId0", "uv.tmdb_id = :tmdbId0",
                "uv.imdb_id = :imdbId0", "uv.rotten_tomatoes_id = :rottenTomatoesId0",
                "douban_id in (:contextDoubanIds)", "tmdb_id in (:contextTmdbIds)",
                "imdb_id in (:contextImdbIds)", "rotten_tomatoes_id in (:contextRtIds)");
        MapSqlParameterSource batchParams = params.getAllValues().get(sql.getAllValues().indexOf(batchSql));
        assertThat(batchParams.getValue("contextDoubanIds")).isEqualTo(new java.util.LinkedHashSet<>(List.of("d1", "d2")));
        assertThat(batchParams.getValue("contextTmdbIds")).isEqualTo(new java.util.LinkedHashSet<>(List.of("t1", "t2")));
        assertThat(batchParams.getValue("contextImdbIds")).isEqualTo(new java.util.LinkedHashSet<>(List.of("i1", "i2")));
        assertThat(batchParams.getValue("contextRtIds")).isEqualTo(new java.util.LinkedHashSet<>(List.of("r1", "r2")));
        verify(search, times(2)).findCandidateUnifiedVideoIds(anyString(), anyString());
        verify(metadata).populateUnifiedMetadata(List.of());
    }

    @Test
    void emptyExternalAndContextSearchResultsFallbackToBoundedTitleSql() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", "d1", null, null, null);
        when(search.findCandidateUnifiedVideoIds("Codex Signal", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of()));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenReturn(List.of());
        when(metadata.populateUnifiedMetadata(anyList())).thenReturn(List.of());

        assertThat(repository.findContextUnifiedCandidates(List.of(raw))).isEmpty();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(2)).query(sql.capture(), params.capture(), anyRowMapper());
        assertThat(sql.getAllValues().get(0)).contains("douban_id = :doubanId");
        assertThat(sql.getAllValues().get(1)).contains("similarity(title, :title)", "limit :limit");
        assertThat(params.getAllValues().get(1).getValue("limit")).isEqualTo(20);
        verify(search).findCandidateUnifiedVideoIds("Codex Signal", "2026");
        verify(metadata).populateUnifiedMetadata(List.of());
    }

    @Test
    void deduplicatesContextSearchByNormalizedTitleAndYearWithinOneOperation() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        RawVideoAggregationRecord first = rawVideo("Codex Signal 1080p", "2020", null, null, null, null);
        RawVideoAggregationRecord second = rawVideo("Codex Signal", " 2020 ", null, null, null, null);
        when(search.findCandidateUnifiedVideoIds(anyString(), anyString()))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of()));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenReturn(List.of());
        when(metadata.populateUnifiedMetadata(anyList())).thenReturn(List.of());

        assertThat(repository.findContextUnifiedCandidates(List.of(first, second))).isEmpty();

        verify(search, times(1)).findCandidateUnifiedVideoIds(anyString(), anyString());
        verify(jdbc, times(1)).query(anyString(), any(MapSqlParameterSource.class), anyRowMapper());
        verify(metadata).populateUnifiedMetadata(List.of());
    }

    @Test
    void titleRecallIncludesAdjacentFourDigitYearsAndUnknownYearBuckets() {
        assertTitleYearRecall("2020", "(year in (:yearBuckets) or year is null or btrim(year) = '')",
                List.of("2019", "2020", "2021"));
        assertTitleYearRecall("1999", "(year in (:yearBuckets) or year is null or btrim(year) = '')",
                List.of("1998", "1999", "2000"));
        assertTitleYearRecall("2020/21", "(year = :year or year is null or btrim(year) = '')", "2020/21");
        assertTitleYearRecall("unknown", "similarity(title, :title)", null);
    }

    @Test
    void singleMatchCombinesRecallChannelsThenLoadsMetadataOnceAndKeepsTwentyCandidates() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationMatchingStrategy strategy = mock(AggregationMatchingStrategy.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, strategy, mock(AggregationContextSearchClient.class), metadata);
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", "d1", null, null, null);
        List<UnifiedVideoAggregationCandidate> external = new ArrayList<>();
        List<UnifiedVideoAggregationCandidate> titles = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            external.add(candidate(UUID.randomUUID(), "External " + index));
            titles.add(candidate(UUID.randomUUID(), "Title " + index));
        }
        when(strategy.isUsableExternalId("douban", "d1")).thenReturn(true);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("douban_id = :doubanId") ? external : titles;
        });
        when(metadata.populateUnifiedMetadata(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(strategy.findMatchPlan(any(RawVideoAggregationRecord.class), anyList()))
                .thenReturn(AggregationMatchPlan.none());

        assertThat(repository.findMatchPlan(raw).hasRoot()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UnifiedVideoAggregationCandidate>> enriched =
                ArgumentCaptor.forClass(List.class);
        verify(metadata).populateUnifiedMetadata(enriched.capture());
        assertThat(enriched.getValue()).hasSize(20);
        List<UUID> expectedIds = new ArrayList<>(external.stream()
                .map(UnifiedVideoAggregationCandidate::id).toList());
        expectedIds.addAll(titles.stream().map(UnifiedVideoAggregationCandidate::id).toList().subList(0, 5));
        assertThat(enriched.getValue()).extracting(UnifiedVideoAggregationCandidate::id)
                .containsExactlyElementsOf(expectedIds);
        verify(strategy).findMatchPlan(raw, enriched.getValue());
    }

    @Test
    void contextSearchIdsAreBoundedToTwentyPerRaw() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationContextSearchClient search = mock(AggregationContextSearchClient.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), search, metadata);
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            ids.add(UUID.randomUUID());
        }
        RawVideoAggregationRecord raw = rawVideo("Codex Signal", "2026", null, null, null, null);
        when(search.findCandidateUnifiedVideoIds("Codex Signal", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(ids));
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            MapSqlParameterSource params = invocation.getArgument(1);
            RowMapper<?> mapper = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            List<UUID> requested = (List<UUID>) params.getValue("ids");
            List<Object> rows = new ArrayList<>();
            for (int index = 0; index < requested.size(); index++) {
                rows.add(mapper.mapRow(row(index, requested.get(index)), index));
            }
            return rows;
        });
        when(metadata.populateUnifiedMetadata(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<UnifiedVideoAggregationCandidate> candidates = repository.findContextUnifiedCandidates(List.of(raw));

        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), params.capture(), anyRowMapper());
        assertThat((List<?>) params.getValue().getValue("ids")).hasSize(20);
        assertThat(params.getValue().getValue("ids")).isEqualTo(ids.subList(0, 20));
        assertThat(candidates).extracting(UnifiedVideoAggregationCandidate::id).containsExactlyElementsOf(ids.subList(0, 20));
        verify(search).findCandidateUnifiedVideoIds("Codex Signal", "2026");
        verify(metadata).populateUnifiedMetadata(candidates);
    }

    @Test
    void explicitUnifiedIdsLoadCompletelyInHundredIdChunksAndKeepRequestedOrder() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), mock(AggregationContextSearchClient.class),
                mock(AggregationMetadataNameRepository.class));
        List<UUID> ids = ids(205);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            MapSqlParameterSource params = invocation.getArgument(1);
            RowMapper<?> mapper = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            List<UUID> batch = (List<UUID>) params.getValue("ids");
            List<Object> rows = new ArrayList<>();
            for (int index = batch.size() - 1; index >= 0; index--) {
                rows.add(mapper.mapRow(row(index, batch.get(index)), rows.size()));
            }
            return rows;
        });

        List<UnifiedVideoAggregationCandidate> candidates = repository.queryUnifiedCandidatesByIds(ids);

        assertThat(candidates).extracting(UnifiedVideoAggregationCandidate::id).containsExactlyElementsOf(ids);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).query(anyString(), params.capture(), anyRowMapper());
        assertThat(params.getAllValues()).extracting(value -> ((List<?>) value.getValue("ids")).size())
                .containsExactly(100, 100, 5);
    }

    @Test
    void writeValidationReadsFreshFlatMetadataWithoutUsingMetadataCache() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = new AggregationMetadataNameRepository(
                jdbc, null, tools.jackson.databind.json.JsonMapper.builder().build());
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), mock(AggregationContextSearchClient.class), metadata);
        List<UUID> ids = ids(205);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertThat(sql).contains("actor_names", "director_names", "area_codes");
            MapSqlParameterSource params = invocation.getArgument(1);
            RowMapper<?> mapper = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            List<UUID> batch = (List<UUID>) params.getValue("ids");
            List<Object> rows = new ArrayList<>();
            for (int index = batch.size() - 1; index >= 0; index--) {
                rows.add(mapper.mapRow(writeRow(batch.get(index)), rows.size()));
            }
            return rows;
        });

        List<UnifiedVideoAggregationCandidate> candidates = repository.queryUnifiedCandidatesByIdsForWrite(ids);

        assertThat(candidates).extracting(UnifiedVideoAggregationCandidate::id).containsExactlyElementsOf(ids);
        assertThat(candidates.getFirst().actorNames()).containsExactly("actor-" + ids.getFirst());
        assertThat(candidates.getFirst().directorNames()).containsExactly("director-" + ids.getFirst());
        assertThat(candidates.getFirst().areaNames()).containsExactly("area-" + ids.getFirst());
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(3)).query(anyString(), params.capture(), anyRowMapper());
        assertThat(params.getAllValues()).extracting(value -> ((List<?>) value.getValue("ids")).size())
                .containsExactly(100, 100, 5);
    }

    private void assertTitleYearRecall(String year, String expectedSql, Object expectedYearValue) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AggregationMetadataNameRepository metadata = mock(AggregationMetadataNameRepository.class);
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, AggregationTestFixtures.matchingStrategy(), mock(AggregationContextSearchClient.class), metadata);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenReturn(List.of());
        when(metadata.populateUnifiedMetadata(anyList())).thenReturn(List.of());

        repository.findMatchPlan(rawVideo("Codex Signal", year, null, null, null, null));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), anyRowMapper());
        assertThat(sql.getValue()).contains(expectedSql);
        if (expectedYearValue instanceof List<?> buckets) {
            assertThat(params.getValue().getValue("yearBuckets")).isEqualTo(buckets);
            assertThat(params.getValue().hasValue("year")).isFalse();
        } else if (expectedYearValue instanceof String exact) {
            assertThat(params.getValue().getValue("year")).isEqualTo(exact);
            assertThat(params.getValue().hasValue("yearBuckets")).isFalse();
        } else {
            assertThat(params.getValue().hasValue("year")).isFalse();
            assertThat(params.getValue().hasValue("yearBuckets")).isFalse();
        }
    }

    private static RawVideoAggregationRecord rawVideo(
            String title, String year, String doubanId, String tmdbId, String imdbId, String rtId) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(), title, null, null, year, null, null, null, null, null, null, null,
                "电影", null, null, null, null, doubanId, tmdbId, imdbId, rtId,
                "READY", "SUCCESS", "PENDING");
    }

    private static UnifiedVideoAggregationCandidate candidate(UUID id, String title) {
        return new UnifiedVideoAggregationCandidate(
                id, title, null, null, "2026", null, null, null, "电影",
                null, null, null, null, null);
    }

    private static List<UUID> ids(int count) {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ids.add(UUID.randomUUID());
        }
        return List.copyOf(ids);
    }

    private static RawVideoAggregationRecord externalRaw(String doubanId) {
        RawVideoAggregationRecord raw = mock(RawVideoAggregationRecord.class);
        when(raw.doubanId()).thenReturn(doubanId);
        return raw;
    }

    private static ResultSet row(int contextIndex, UUID id) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getInt("context_index")).thenReturn(contextIndex);
        when(rs.getObject("id", UUID.class)).thenReturn(id);
        return rs;
    }

    private static ResultSet writeRow(UUID id) throws Exception {
        ResultSet rs = row(0, id);
        when(rs.getString("actor_names")).thenReturn("[\"actor-" + id + "\"]");
        when(rs.getString("director_names")).thenReturn("[\"director-" + id + "\"]");
        when(rs.getString("area_codes")).thenReturn("[\"area-" + id + "\"]");
        return rs;
    }

    private static RowMapper<Object> anyRowMapper() {
        return any();
    }
}
