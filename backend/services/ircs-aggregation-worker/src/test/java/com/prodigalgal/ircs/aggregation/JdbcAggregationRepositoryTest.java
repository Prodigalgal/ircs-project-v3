package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.prodigalgal.ircs.common.metadata.JsonStringArrays;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class JdbcAggregationRepositoryTest {

    @Test
    void retainsVerifiedCatalogScalarsWhenRawVotingWouldReplaceThem() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                "jdbc:h2:mem:catalog-retain-"+UUID.randomUUID()+";MODE=PostgreSQL;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        createPipelineSchema(jdbc);
        UUID id=UUID.randomUUID();
        seedScalarUnlockedFixture(jdbc,id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        jdbc.update("insert into catalog_metadata_enrichment_jobs values(?,current_timestamp)",id);
        JdbcAggregationRepository actual=JdbcAggregationRepository.forTest(new NamedParameterJdbcTemplate(source),
                AggregationTestFixtures.matchingStrategy());
        actual.rebuildUnifiedPipelineBasicAttributes(id);
        actual.rebuildUnifiedPipelineDynamicFields(id);
        assertEquals("Old Title",scalar(jdbc,"select title from catalog_entries where id=?",id,String.class));
        assertEquals("2000",scalar(jdbc,"select year from catalog_entries where id=?",id,String.class));
        assertEquals(new BigDecimal("1.0"),scalar(jdbc,"select score from catalog_entries where id=?",id,BigDecimal.class));
        assertEquals("old description",scalar(jdbc,"select description from catalog_entries where id=?",id,String.class));
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private AggregationMatchingStrategy matchingStrategy;

    @Mock
    private AggregationContextSearchClient contextSearchClient;

    @Captor
    private ArgumentCaptor<String> sqlCaptor;

    @Captor
    private ArgumentCaptor<MapSqlParameterSource> paramsCaptor;

    @BeforeEach
    void useRealProviderUsabilityForMockedScoring() {
        AggregationSafetyPolicy policy = AggregationTestFixtures.safetyPolicy();
        org.mockito.Mockito.lenient().when(matchingStrategy.isUsableExternalId(
                        any(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenAnswer(call -> policy.isUsableExternalId(call.getArgument(0), call.getArgument(1)));
        org.mockito.Mockito.lenient().when(matchingStrategy.rejectedExternalIdValues(any(String.class)))
                .thenAnswer(call -> policy.rejectedExternalIdValues(call.getArgument(0)));
        org.mockito.Mockito.lenient().when(matchingStrategy.normalizeExternalId(
                        any(String.class), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenAnswer(call -> policy.normalizeExternalId(call.getArgument(0), call.getArgument(1)));
    }

    @Test
    void recallsTitleYearCandidatesWhenExternalIdsAreAbsent() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord("Codex Signal", "2026", null);
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "2026",
                "12",
                "45m",
                null,
                "电影",
                null,
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);

        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate));
        when(matchingStrategy.findMatchPlan(eq(sourceRecord), eq(List.of(candidate))))
                .thenReturn(AggregationMatchPlan.rootOnly(catalogEntryId));

        Optional<UUID> result = repository.findMatchingCatalogEntry(sourceRecord);

        assertEquals(Optional.of(catalogEntryId), result);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getValue().contains("similarity(title, :title)"));
        assertTrue(sqlCaptor.getValue().contains("year in (:yearBuckets)"));
        assertEquals("Codex Signal", paramsCaptor.getValue().getValue("title"));
        assertEquals(List.of("2025", "2026", "2027"), paramsCaptor.getValue().getValue("yearBuckets"));
        verify(matchingStrategy).findMatchPlan(sourceRecord, List.of(candidate));
    }

    @Test
    void titleRecallOmitsYearPredicateWhenRawYearIsBlank() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord("Codex Signal", "", null);
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);

        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate));
        when(matchingStrategy.findMatchPlan(eq(sourceRecord), eq(List.of(candidate))))
                .thenReturn(AggregationMatchPlan.rootOnly(catalogEntryId));

        Optional<UUID> result = repository.findMatchingCatalogEntry(sourceRecord);

        assertEquals(Optional.of(catalogEntryId), result);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertFalse(sqlCaptor.getValue().contains(":year is null"));
        assertFalse(sqlCaptor.getValue().contains("year = :year"));
        assertTrue(sqlCaptor.getValue().contains("similarity(title, :title)"));
        assertFalse(paramsCaptor.getValue().hasValue("year"));
    }

    @Test
    void recallsContextUnifiedCandidatesForRawBatchWithStableDeduplication() {
        SourceRecordAggregationRecord rawWithExternalId = sourceRecord("Codex Signal", "2026", "35000001");
        SourceRecordAggregationRecord rawWithTitleOnly = sourceRecord("Codex Signal", "2026", null);
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "2026",
                "12",
                "45m",
                null,
                "电影",
                "35000001",
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);

        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate), List.of(candidate));

        List<CatalogEntryAggregationCandidate> result =
                repository.findContextUnifiedCandidates(List.of(rawWithExternalId, rawWithTitleOnly));

        assertEquals(List.of(candidate), result);
        verify(jdbcTemplate, times(2)).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getAllValues().get(0).contains("douban_id = :doubanId"));
        assertTrue(sqlCaptor.getAllValues().get(1).contains("similarity(title, :title)"));
    }

    @Test
    void contextTitleRecallUsesSearchServiceIdsAndLoadsCandidatesBackFromDb() {
        SourceRecordAggregationRecord rawWithTitleOnly = sourceRecord("Codex Signal", "2026", null);
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "2026",
                "12",
                "45m",
                null,
                "电影",
                null,
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository =
                JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy, contextSearchClient);

        when(contextSearchClient.findCandidateCatalogEntryIds("Codex Signal", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of(catalogEntryId)));
        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate));

        List<CatalogEntryAggregationCandidate> result =
                repository.findContextUnifiedCandidates(List.of(rawWithTitleOnly));

        assertEquals(List.of(candidate), result);
        verify(contextSearchClient).findCandidateCatalogEntryIds("Codex Signal", "2026");
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getValue().contains("where id in (:ids)"));
        assertEquals(List.of(catalogEntryId), paramsCaptor.getValue().getValue("ids"));
    }

    @Test
    void contextTitleRecallFallsBackToBoundedSqlWhenSearchServiceReturnsEmpty() {
        SourceRecordAggregationRecord rawWithTitleOnly = sourceRecord("Codex Signal", "2026", null);
        JdbcAggregationRepository repository =
                JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy, contextSearchClient);

        when(contextSearchClient.findCandidateCatalogEntryIds("Codex Signal", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.attempted(List.of()));

        List<CatalogEntryAggregationCandidate> result =
                repository.findContextUnifiedCandidates(List.of(rawWithTitleOnly));

        assertEquals(List.of(), result);
        verify(contextSearchClient).findCandidateCatalogEntryIds("Codex Signal", "2026");
        verify(jdbcTemplate).query(
                any(String.class),
                any(MapSqlParameterSource.class),
                anyUnifiedCandidateRowMapper());
    }

    @Test
    void contextTitleRecallFallsBackToSqlWhenSearchRecallIsNotAttempted() {
        SourceRecordAggregationRecord rawWithTitleOnly = sourceRecord("Codex Signal", "2026", null);
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "2026",
                "12",
                "45m",
                null,
                "电影",
                null,
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository =
                JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy, contextSearchClient);

        when(contextSearchClient.findCandidateCatalogEntryIds("Codex Signal", "2026"))
                .thenReturn(AggregationContextSearchClient.ContextSearchResult.notAttempted());
        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate));

        List<CatalogEntryAggregationCandidate> result =
                repository.findContextUnifiedCandidates(List.of(rawWithTitleOnly));

        assertEquals(List.of(candidate), result);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getValue().contains("similarity(title, :title)"));
    }

    @Test
    void contextExternalRecallDoesNotCallSearchServiceWhenDoubanOrTmdbIsPresent() {
        SourceRecordAggregationRecord rawWithExternalId = sourceRecord("Codex Signal", "2026", "35000001");
        UUID catalogEntryId = UUID.randomUUID();
        CatalogEntryAggregationCandidate candidate = new CatalogEntryAggregationCandidate(
                catalogEntryId,
                "Codex Signal",
                null,
                null,
                "2026",
                "12",
                "45m",
                null,
                "电影",
                "35000001",
                null,
                null,
                null,
                null);
        JdbcAggregationRepository repository =
                JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy, contextSearchClient);

        when(jdbcTemplate.query(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        anyUnifiedCandidateRowMapper()))
                .thenReturn(List.of(candidate));

        List<CatalogEntryAggregationCandidate> result =
                repository.findContextUnifiedCandidates(List.of(rawWithExternalId));

        assertEquals(List.of(candidate), result);
        verify(contextSearchClient, times(0)).findCandidateCatalogEntryIds(any(String.class), any(String.class));
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getValue().contains("douban_id = :doubanId"));
    }

    @Test
    void contextExternalRecallAlsoUsesImdbAndRottenTomatoes() {
        SourceRecordAggregationRecord sourceRecord = new SourceRecordAggregationRecord(
                UUID.randomUUID(),
                null,
                null,
                "Aggregation description",
                "2026",
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                null,
                null,
                "tt0133093",
                "rt-1",
                "READY",
                "SUCCESS",
                "PROCESSING");
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);

        List<CatalogEntryAggregationCandidate> result = repository.findContextUnifiedCandidates(List.of(sourceRecord));

        assertEquals(List.of(), result);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), anyUnifiedCandidateRowMapper());
        assertTrue(sqlCaptor.getValue().contains("imdb_id = :imdbId"));
        assertTrue(sqlCaptor.getValue().contains("rotten_tomatoes_id = :rottenTomatoesId"));
    }

    @Test
    void findSourceRecordExposesStandardCategoryNameForGraphBucket() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-raw-category-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createCandidateSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID categoryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        jdbc.update("insert into catalog_categories (id, name, slug) values (?, ?, ?)", categoryId, "电影", "movie");
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, year,
                    enrichment_status, normalization_status, aggregation_status, category_code
                ) values (?, now(), now(), 0, 'source-1', 'hash-1', 'Codex Signal', '2026',
                    'SUCCESS', 'READY', 'PROCESSING', 'movie')
                """, sourceRecordId);

        SourceRecordAggregationRecord result = repository.findSourceRecord(sourceRecordId).orElseThrow();

        assertEquals("电影", result.categoryName());
    }

    @Test
    void findSourceRecordExposesMetadataNamesForV1GraphScoring() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-raw-metadata-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createCandidateSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID sourceRecordId = UUID.randomUUID();
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, year,
                    actor_names, director_names, area_codes,
                    enrichment_status, normalization_status, aggregation_status
                ) values (?, now(), now(), 0, 'source-1', 'hash-1', 'Codex Signal', '2026',
                    cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                    'SUCCESS', 'READY', 'PROCESSING')
                """, sourceRecordId, flatJson("Actor A"), flatJson("Director A"), flatJson("JP"));

        SourceRecordAggregationRecord result = repository.findSourceRecord(sourceRecordId).orElseThrow();

        assertEquals(Set.of("Actor A"), result.actorNames());
        assertEquals(Set.of("Director A"), result.directorNames());
        assertEquals(Set.of("JP"), result.areaNames());
    }

    @Test
    void contextUnifiedCandidatesExposeStandardCategoryNameForGraphBucket() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-unified-category-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createCandidateSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID categoryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        jdbc.update("insert into catalog_categories (id, name, slug) values (?, ?, ?)", categoryId, "电视剧", "tv");
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, year,
                    douban_id, enrichment_status, normalization_status, aggregation_status
                ) values (?, now(), now(), 0, 'source-1', 'hash-1', 'Codex Signal', '2026',
                    '35000001', 'SUCCESS', 'READY', 'PROCESSING')
                """, sourceRecordId);
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, year, douban_id, category_code, metadata_status
                ) values (?, now(), now(), 0, 'Codex Signal', '2026', '35000001', 'tv', 'SYNCED')
                """, catalogEntryId);
        SourceRecordAggregationRecord sourceRecord = repository.findSourceRecord(sourceRecordId).orElseThrow();

        List<CatalogEntryAggregationCandidate> result = repository.findContextUnifiedCandidates(List.of(sourceRecord));

        assertEquals(1, result.size());
        assertEquals("电视剧", result.getFirst().categoryName());
    }

    @Test
    void contextUnifiedCandidatesExposeMetadataNamesForV1GraphScoring() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-unified-metadata-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createCandidateSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID sourceRecordId = UUID.randomUUID();
        UUID catalogEntryId = UUID.randomUUID();
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, year,
                    douban_id, enrichment_status, normalization_status, aggregation_status
                ) values (?, now(), now(), 0, 'source-1', 'hash-1', 'Codex Signal', '2026',
                    '35000001', 'SUCCESS', 'READY', 'PROCESSING')
                """, sourceRecordId);
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, year, douban_id,
                    actor_names, director_names, area_codes, metadata_status
                ) values (?, now(), now(), 0, 'Codex Signal', '2026', '35000001',
                    cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), 'SYNCED')
                """, catalogEntryId, flatJson("Actor U"), flatJson("Director U"), flatJson("US"));
        SourceRecordAggregationRecord sourceRecord = repository.findSourceRecord(sourceRecordId).orElseThrow();

        List<CatalogEntryAggregationCandidate> result = repository.findContextUnifiedCandidates(List.of(sourceRecord));

        assertEquals(1, result.size());
        assertEquals(Set.of("Actor U"), result.getFirst().actorNames());
        assertEquals(Set.of("Director U"), result.getFirst().directorNames());
        assertEquals(Set.of("US"), result.getFirst().areaNames());
    }


    @Test
    void mergeDuplicateCatalogEntriesMigratesBindingsAndDeletesVictimRowsInOrder() {
        UUID rootId = UUID.randomUUID();
        UUID victimId = UUID.randomUUID();
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);
        when(jdbcTemplate.query(any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        repository.mergeDuplicateCatalogEntries(rootId, List.of(victimId));

        verify(jdbcTemplate, times(7)).update(sqlCaptor.capture(), paramsCaptor.capture());
        List<String> sqlStatements = sqlCaptor.getAllValues();
        assertTrue(sqlStatements.get(0).contains("update catalog_source_bindings"));
        assertTrue(sqlStatements.get(1).contains("insert into member_catalog_favorites"));
        assertTrue(sqlStatements.get(1).contains("on conflict (member_id, catalog_entry_id) do nothing"));
        assertTrue(sqlStatements.get(2).contains("delete from member_catalog_favorites"));
        assertTrue(sqlStatements.get(3).contains("insert into member_playback_progress"));
        assertTrue(sqlStatements.get(3).contains("member_playback_progress.last_watched_at < excluded.last_watched_at"));
        assertTrue(sqlStatements.get(4).contains("delete from member_playback_progress"));
        assertTrue(sqlStatements.get(5).contains("update catalog_entries root"));
        assertTrue(sqlStatements.get(5).contains("victim.id = :victimId"));
        assertTrue(sqlStatements.get(6).contains("delete from catalog_entries"));
        assertTrue(sqlStatements.stream().noneMatch(this::containsRetiredUnifiedMetadataTable));
        assertTrue(paramsCaptor.getAllValues().subList(0, 5).stream()
                .allMatch(params -> rootId.equals(params.getValue("rootId"))
                        && List.of(victimId).equals(params.getValue("victimIds"))));
        assertEquals(rootId, paramsCaptor.getAllValues().get(5).getValue("rootId"));
        assertEquals(victimId, paramsCaptor.getAllValues().get(5).getValue("victimId"));
        assertEquals(rootId, paramsCaptor.getAllValues().get(6).getValue("rootId"));
        assertEquals(List.of(victimId), paramsCaptor.getAllValues().get(6).getValue("victimIds"));
    }

    @Test
    void deleteAllAggregationResetOwnedRowsDeletesGlobalResetTablesInSafeOrder() {
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);

        int changedRows = repository.deleteAllAggregationResetOwnedRows();

        assertEquals(8, changedRows);
        verify(jdbcTemplate, times(8)).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        List<String> sqlStatements = sqlCaptor.getAllValues();
        assertTrue(sqlStatements.get(0).contains("delete from magnet_discovery_provider_runs"));
        assertTrue(sqlStatements.get(1).contains("delete from magnet_discovery_jobs"));
        assertTrue(sqlStatements.get(2).contains("delete from catalog_magnet_links"));
        assertTrue(sqlStatements.get(3).contains("delete from member_catalog_favorites"));
        assertTrue(sqlStatements.get(4).contains("delete from member_playback_progress"));
        assertTrue(sqlStatements.get(5).contains("delete from catalog_source_bindings"));
        assertTrue(sqlStatements.get(6).contains("delete from catalog_entry_tags"));
        assertTrue(sqlStatements.get(7).contains("delete from catalog_entries"));
    }

    @Test
    void markAllRawAggregationPendingUsesAggregationTimelineOnly() {
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);
        when(jdbcTemplate.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(3);

        int changedRows = repository.markAllRawAggregationPending();

        assertEquals(3, changedRows);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertTrue(sqlCaptor.getValue().contains("set aggregation_status = 'PENDING'"));
        assertTrue(sqlCaptor.getValue().contains("aggregation_status_updated_at = now()"));
    }

    @Test
    void findEligiblePendingRawIdsSelectsPendingSourceRecordsOnly() {
        UUID sourceRecordId = UUID.randomUUID();
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(sourceRecordId));

        List<UUID> result = repository.findEligiblePendingRawIds(25);

        assertEquals(List.of(sourceRecordId), result);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));
        assertTrue(sqlCaptor.getValue().contains("from source_records rv"));
        assertTrue(sqlCaptor.getValue().contains("where rv.aggregation_status = 'PENDING'"));
        assertTrue(sqlCaptor.getValue().contains("rv.normalization_status = 'READY'"));
        assertTrue(sqlCaptor.getValue().contains("rv.enrichment_status is distinct from 'PENDING'"));
        assertTrue(sqlCaptor.getValue().contains("nullif(trim(rv.title), '') is not null"));
        assertEquals(25, paramsCaptor.getValue().getValue("limit"));
    }

    @Test
    void findUnifiedIdsMissingCoverFromRawSelectsBoundCatalogEntriesWithRawCover() {
        UUID catalogEntryId = UUID.randomUUID();
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(jdbcTemplate, matchingStrategy);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(catalogEntryId));

        List<UUID> result = repository.findUnifiedIdsMissingCoverFromRaw(10);

        assertEquals(List.of(catalogEntryId), result);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));
        assertTrue(sqlCaptor.getValue().contains("from catalog_entries uv"));
        assertTrue(sqlCaptor.getValue().contains("join catalog_source_bindings rvuv"));
        assertTrue(sqlCaptor.getValue().contains("uv.media_image_source_id is null"));
        assertTrue(sqlCaptor.getValue().contains("rv.media_image_source_id is not null"));
        assertEquals(10, paramsCaptor.getValue().getValue("limit"));
    }

    @Test
    void backfillUnifiedCoverImagesFromRawUpdatesMissingUnifiedCoversInBatch() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-backfill-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID firstSourceRecordId = UUID.randomUUID();
        UUID secondSourceRecordId = UUID.randomUUID();
        UUID firstCover = UUID.randomUUID();
        UUID secondCover = UUID.randomUUID();
        seedCoverImage(jdbc, firstCover, "EXTERNAL", null);
        seedCoverImage(jdbc, secondCover, "LOCAL", 128L);
        seedCoverUnified(jdbc, catalogEntryId, null, "[]");
        seedCoverRaw(jdbc, firstSourceRecordId, "cover-backfill-first", firstCover, LocalDateTime.of(2026, 1, 1, 0, 0));
        seedCoverRaw(jdbc, secondSourceRecordId, "cover-backfill-second", secondCover, LocalDateTime.of(2026, 1, 2, 0, 0));
        bindPipelineRaw(jdbc, firstSourceRecordId, catalogEntryId);
        bindPipelineRaw(jdbc, secondSourceRecordId, catalogEntryId);

        List<UnifiedCoverBackfillResult> result = repository.backfillUnifiedCoverImagesFromRaw(10);

        assertEquals(List.of(new UnifiedCoverBackfillResult(catalogEntryId, firstCover, "EXTERNAL", "UNPROCESSED")), result);
        assertEquals(firstCover, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
    void mergeDuplicateCatalogEntriesMigratesRowsWithConflictSafeCleanup() throws Exception {
        try (var database = CatalogTestDatabase.open()) {
            var dataSource = database.dataSource();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
            JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
            UUID rootId = UUID.randomUUID();
            UUID victimId = UUID.randomUUID();
            UUID rawId = UUID.randomUUID();
            UUID memberWithConflict = UUID.randomUUID();
            UUID memberWithoutConflict = UUID.randomUUID();
            seedMergeFixture(jdbc, rootId, victimId, rawId, memberWithConflict, memberWithoutConflict);

            repository.mergeDuplicateCatalogEntries(rootId, List.of(victimId));

            assertEquals(1, count(jdbc, "select count(*) from catalog_entries where id = ?", rootId));
            assertEquals(0, count(jdbc, "select count(*) from catalog_entries where id = ?", victimId));
            assertEquals(1, count(jdbc, "select count(*) from catalog_source_bindings where source_record_id = ? and catalog_entry_id = ?", rawId, rootId));
            assertEquals(0, count(jdbc, "select count(*) from catalog_source_bindings where catalog_entry_id = ?", victimId));
            assertEquals("BOUND", jdbc.queryForObject(
                    "select aggregation_status from source_records where id = ?",
                    String.class,
                    rawId));
            assertEquals(1, count(jdbc, "select count(*) from member_catalog_favorites where member_id = ? and catalog_entry_id = ?", memberWithConflict, rootId));
            assertEquals(0, count(jdbc, "select count(*) from member_catalog_favorites where catalog_entry_id = ?", victimId));
            assertEquals(1, count(jdbc, "select count(*) from member_playback_progress where member_id = ? and catalog_entry_id = ?", memberWithoutConflict, rootId));
            assertEquals(0, count(jdbc, "select count(*) from member_playback_progress where catalog_entry_id = ?", victimId));
            assertEquals(Set.of("Root Actor", "Victim Actor"), jsonSet(jdbc, "select actor_names from catalog_entries where id = ?", rootId));
            assertEquals(Set.of("Victim Director"), jsonSet(jdbc, "select director_names from catalog_entries where id = ?", rootId));
            assertEquals(Set.of("shared-genre", "victim-genre"), jsonSet(jdbc, "select genre_codes from catalog_entries where id = ?", rootId));
            assertEquals(Set.of("zh"), jsonSet(jdbc, "select language_codes from catalog_entries where id = ?", rootId));
            assertEquals(Set.of("CN"), jsonSet(jdbc, "select area_codes from catalog_entries where id = ?", rootId));
            assertEquals("movie", scalar(jdbc, "select category_code from catalog_entries where id = ?", rootId, String.class));
            assertEquals("victim-douban", scalar(jdbc, "select douban_id from catalog_entries where id = ?", rootId, String.class));
            assertEquals("root-tmdb", scalar(jdbc, "select tmdb_id from catalog_entries where id = ?", rootId, String.class));
            assertEquals("victim-imdb", scalar(jdbc, "select imdb_id from catalog_entries where id = ?", rootId, String.class));
            assertEquals("victim-rt", scalar(jdbc, "select rotten_tomatoes_id from catalog_entries where id = ?", rootId, String.class));
            assertEquals("victim-description", scalar(jdbc, "select description from catalog_entries where id = ?", rootId, String.class));
            assertEquals("root-alias", scalar(jdbc, "select alias_title from catalog_entries where id = ?", rootId, String.class));
            assertEquals(new BigDecimal("8.8"), scalar(jdbc, "select score from catalog_entries where id = ?", rootId, BigDecimal.class));
            assertEquals(LocalDate.of(2026, 1, 2), scalar(jdbc, "select published_at from catalog_entries where id = ?", rootId, LocalDate.class));
            assertEquals("12", scalar(jdbc, "select total_episodes from catalog_entries where id = ?", rootId, String.class));
            assertEquals("45m", scalar(jdbc, "select duration from catalog_entries where id = ?", rootId, String.class));
            assertEquals("root-remarks", scalar(jdbc, "select remarks from catalog_entries where id = ?", rootId, String.class));
            assertEquals(2, scalar(jdbc, "select season from catalog_entries where id = ?", rootId, Integer.class));
            assertEquals("victim-subtitle", scalar(jdbc, "select subtitle from catalog_entries where id = ?", rootId, String.class));
            assertEquals(
                    LocalDateTime.of(2026, 6, 2, 10, 0),
                    scalar(jdbc, "select last_trend_at from catalog_entries where id = ?", rootId, LocalDateTime.class));
            assertEquals("DIRTY", scalar(jdbc, "select metadata_status from catalog_entries where id = ?", rootId, String.class));
        }
    }

    @Test
    void markBoundUpdatesAggregationStatusTimeWithoutMutatingBusinessUpdatedAt() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-status-time-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID sourceRecordId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime businessUpdatedAt = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime statusUpdatedAt = LocalDateTime.of(2026, 1, 3, 0, 0);
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, aggregation_status_updated_at, version,
                    source_vid, source_hash, title, enrichment_status, normalization_status, aggregation_status
                ) values (?, ?, ?, ?, 0, 'status-raw', 'status-hash', 'Status Raw', 'SUCCESS', 'READY', 'PROCESSING')
                """,
                sourceRecordId,
                createdAt,
                businessUpdatedAt,
                statusUpdatedAt);

        assertEquals(1, repository.markBound(sourceRecordId));

        assertEquals("BOUND", scalar(jdbc, "select aggregation_status from source_records where id = ?", sourceRecordId, String.class));
        assertEquals(businessUpdatedAt, scalar(jdbc, "select updated_at from source_records where id = ?", sourceRecordId, LocalDateTime.class));
        LocalDateTime changedStatusTime = scalar(
                jdbc,
                "select aggregation_status_updated_at from source_records where id = ?",
                sourceRecordId,
                LocalDateTime.class);
        assertTrue(changedStatusTime.isAfter(statusUpdatedAt));
    }

    @Test
    void rebuildUnifiedPipelineRelationsVotesCategoryAndRebuildsUnlockedRelations() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-pipeline-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID oldCategoryId = UUID.randomUUID();
        UUID votedCategoryId = UUID.randomUUID();
        UUID losingCategoryId = UUID.randomUUID();
        UUID rawOne = UUID.randomUUID();
        UUID rawTwo = UUID.randomUUID();
        UUID rawThree = UUID.randomUUID();
        UUID actorOne = UUID.randomUUID();
        UUID actorTwo = UUID.randomUUID();
        UUID staleActor = UUID.randomUUID();
        UUID directorOne = UUID.randomUUID();
        UUID staleDirector = UUID.randomUUID();
        UUID genreOne = UUID.randomUUID();
        UUID genreTwo = UUID.randomUUID();
        UUID staleGenre = UUID.randomUUID();
        UUID languageOne = UUID.randomUUID();
        UUID staleLanguage = UUID.randomUUID();
        UUID areaOne = UUID.randomUUID();
        UUID staleArea = UUID.randomUUID();
        seedPipelineUnlockedFixture(
                jdbc,
                catalogEntryId,
                oldCategoryId,
                votedCategoryId,
                losingCategoryId,
                rawOne,
                rawTwo,
                rawThree,
                actorOne,
                actorTwo,
                staleActor,
                directorOne,
                staleDirector,
                genreOne,
                genreTwo,
                staleGenre,
                languageOne,
                staleLanguage,
                areaOne,
                staleArea);

        repository.rebuildUnifiedPipelineRelations(catalogEntryId);

        assertEquals(votedCategoryId, scalar(
                jdbc,
                "select category_id from catalog_entries where id = ?",
                catalogEntryId,
                UUID.class));
        assertEquals("movie", scalar(jdbc, "select category_code from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(Set.of("Actor One", "Actor Two"), jsonSet(jdbc, "select actor_names from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("Director One"), jsonSet(jdbc, "select director_names from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("genre-one", "genre-two"), jsonSet(jdbc, "select genre_codes from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("language-one"), jsonSet(jdbc, "select language_codes from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("area-one"), jsonSet(jdbc, "select area_codes from catalog_entries where id = ?", catalogEntryId));
    }

    @Test
    void rebuildUnifiedPipelineRelationsPreservesLockedFields() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-pipeline-locked-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID lockedCategoryId = UUID.randomUUID();
        UUID rawCategoryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID lockedActor = UUID.randomUUID();
        UUID rawActor = UUID.randomUUID();
        UUID lockedDirector = UUID.randomUUID();
        UUID rawDirector = UUID.randomUUID();
        UUID lockedGenre = UUID.randomUUID();
        UUID rawGenre = UUID.randomUUID();
        UUID lockedLanguage = UUID.randomUUID();
        UUID rawLanguage = UUID.randomUUID();
        UUID lockedArea = UUID.randomUUID();
        UUID rawArea = UUID.randomUUID();
        seedPipelineLockedFixture(
                jdbc,
                catalogEntryId,
                lockedCategoryId,
                rawCategoryId,
                sourceRecordId,
                lockedActor,
                rawActor,
                lockedDirector,
                rawDirector,
                lockedGenre,
                rawGenre,
                lockedLanguage,
                rawLanguage,
                lockedArea,
                rawArea);

        repository.rebuildUnifiedPipelineRelations(catalogEntryId);

        assertEquals(lockedCategoryId, scalar(
                jdbc,
                "select category_id from catalog_entries where id = ?",
                catalogEntryId,
                UUID.class));
        assertEquals("locked-category", scalar(jdbc, "select category_code from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(Set.of("Locked Actor"), jsonSet(jdbc, "select actor_names from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("Locked Director"), jsonSet(jdbc, "select director_names from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("locked-genre"), jsonSet(jdbc, "select genre_codes from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("locked-language"), jsonSet(jdbc, "select language_codes from catalog_entries where id = ?", catalogEntryId));
        assertEquals(Set.of("locked-area"), jsonSet(jdbc, "select area_codes from catalog_entries where id = ?", catalogEntryId));
    }

    @Test
    void rebuildUnifiedPipelineScalarsAggregatesUnlockedRawSources() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-scalars-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID rawOne = UUID.randomUUID();
        UUID rawTwo = UUID.randomUUID();
        UUID rawThree = UUID.randomUUID();
        seedScalarUnlockedFixture(jdbc, catalogEntryId, rawOne, rawTwo, rawThree);

        repository.rebuildUnifiedPipelineScalars(catalogEntryId);

        assertEquals("Voted Title", scalar(jdbc, "select title from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("2026", scalar(jdbc, "select year from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(new BigDecimal("9.5"), scalar(jdbc, "select score from catalog_entries where id = ?", catalogEntryId, BigDecimal.class));
        assertEquals(LocalDate.of(2025, 1, 1), scalar(jdbc, "select published_at from catalog_entries where id = ?", catalogEntryId, LocalDate.class));
        assertEquals("longest alias title", scalar(jdbc, "select alias_title from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(
                "the longest description wins",
                scalar(jdbc, "select description from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("latest remarks", scalar(jdbc, "select remarks from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("12", scalar(jdbc, "select total_episodes from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("45m", scalar(jdbc, "select duration from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(2, scalar(jdbc, "select season from catalog_entries where id = ?", catalogEntryId, Integer.class));
        assertEquals("longest subtitle value", scalar(jdbc, "select subtitle from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("35000999", scalar(jdbc, "select douban_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("70001", scalar(jdbc, "select tmdb_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("tt0133093", scalar(jdbc, "select imdb_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("rt-first-valid", scalar(jdbc, "select rotten_tomatoes_id from catalog_entries where id = ?", catalogEntryId, String.class));
    }

    @Test
    void rebuildUnifiedPipelineScalarsPreservesAllLockedFieldsIncludingExternalIds() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-scalars-locked-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        seedScalarLockedFixture(jdbc, catalogEntryId, sourceRecordId);

        repository.rebuildUnifiedPipelineScalars(catalogEntryId);

        assertEquals("Locked Title", scalar(jdbc, "select title from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("1999", scalar(jdbc, "select year from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(new BigDecimal("1.1"), scalar(jdbc, "select score from catalog_entries where id = ?", catalogEntryId, BigDecimal.class));
        assertEquals(LocalDate.of(1999, 1, 1), scalar(jdbc, "select published_at from catalog_entries where id = ?", catalogEntryId, LocalDate.class));
        assertEquals("locked alias", scalar(jdbc, "select alias_title from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("locked description", scalar(jdbc, "select description from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("locked remarks", scalar(jdbc, "select remarks from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("1", scalar(jdbc, "select total_episodes from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("10m", scalar(jdbc, "select duration from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals(1, scalar(jdbc, "select season from catalog_entries where id = ?", catalogEntryId, Integer.class));
        assertEquals("locked subtitle", scalar(jdbc, "select subtitle from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("", scalar(jdbc, "select douban_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("", scalar(jdbc, "select tmdb_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("", scalar(jdbc, "select imdb_id from catalog_entries where id = ?", catalogEntryId, String.class));
        assertEquals("", scalar(jdbc, "select rotten_tomatoes_id from catalog_entries where id = ?", catalogEntryId, String.class));
    }

    @Test
    void sportsDisplayRebuildTakesTitleSubtitleAndYearFromOneObservedFixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-sports-display-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        UUID work = UUID.randomUUID();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), other = UUID.randomUUID();
        seedScalarUnlockedFixture(jdbc, work, first, second, other);
        jdbc.update("update catalog_entries set category_code='sports' where id=?", work);
        jdbc.update("update source_records set category_code='sports', title=?, subtitle=?, year='2026' where id in (?,?)",
                "7月4日城市足球联赛", "福州队VS龙岩队", first, second);
        jdbc.update("update source_records set category_code='sports', title=?, subtitle=?, year='2025' where id=?",
                "6月13日篮球邀请赛", "男子组决赛甲城高中篮球代表队VS乙城高中篮球代表队", other);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(
                new NamedParameterJdbcTemplate(dataSource), AggregationTestFixtures.matchingStrategy());

        repository.rebuildUnifiedPipelineScalars(work);

        assertEquals("7月4日城市足球联赛", scalar(jdbc, "select title from catalog_entries where id=?", work, String.class));
        assertEquals("福州队VS龙岩队", scalar(jdbc, "select subtitle from catalog_entries where id=?", work, String.class));
        assertEquals("2026", scalar(jdbc, "select year from catalog_entries where id=?", work, String.class));

        jdbc.update("update catalog_entries set title=?, locked_fields=? where id=?",
                "6月13日篮球邀请赛", "[\"title\"]", work);
        repository.rebuildUnifiedPipelineScalars(work);

        assertEquals("6月13日篮球邀请赛", scalar(jdbc, "select title from catalog_entries where id=?", work, String.class));
        assertEquals("男子组决赛甲城高中篮球代表队VS乙城高中篮球代表队",
                scalar(jdbc, "select subtitle from catalog_entries where id=?", work, String.class));
        assertEquals("2025", scalar(jdbc, "select year from catalog_entries where id=?", work, String.class));
    }

    @Test
    void externalIdFillIgnoresMalformedEvidenceAndPreservesLockedPlaceholders() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-provider-ids-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        UUID unifiedId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        seedScalarUnlockedFixture(jdbc, unifiedId, first, second, third);
        jdbc.update("update catalog_entries set year = '2026', douban_id = '0', tmdb_id = 'unknown', imdb_id = 'tt0000000', rotten_tomatoes_id = '-', locked_fields = '[\"doubanId\"]' where id = ?", unifiedId);
        jdbc.update("update source_records set tmdb_id = 'tmdb-invalid', imdb_id = '1.0', rotten_tomatoes_id = 'https://invalid.example/' where id = ?", first);
        jdbc.update("update source_records set tmdb_id = '00070002', imdb_id = 'TT0133093', rotten_tomatoes_id = '  second_valid  ' where id = ?", second);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(
                new NamedParameterJdbcTemplate(dataSource), AggregationTestFixtures.matchingStrategy());

        repository.rebuildUnifiedPipelineExternalIds(unifiedId);

        assertEquals("0", scalar(jdbc, "select douban_id from catalog_entries where id = ?", unifiedId, String.class));
        assertEquals("70002", scalar(jdbc, "select tmdb_id from catalog_entries where id = ?", unifiedId, String.class));
        assertEquals("tt0133093", scalar(jdbc, "select imdb_id from catalog_entries where id = ?", unifiedId, String.class));
        assertEquals("second_valid", scalar(jdbc, "select rotten_tomatoes_id from catalog_entries where id = ?", unifiedId, String.class));
        assertFalse(AggregationTestFixtures.safetyPolicy().isUsableExternalId("douban", "0"));
    }

    @Test
    void externalIdFillDoesNotOverwriteAConcurrentEditOrNewFieldLock() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-provider-cas-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        UUID unifiedId = UUID.randomUUID();
        seedScalarUnlockedFixture(jdbc, unifiedId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("update catalog_entries set year = '2026', douban_id = '', tmdb_id = '', imdb_id = '', rotten_tomatoes_id = '' where id = ?", unifiedId);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource) {
            private boolean edited;

            @Override
            public int update(String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource params) {
                if (!edited && sql.contains("set douban_id = :selectedId")) {
                    edited = true;
                    jdbc.update("update catalog_entries set douban_id = '35000888', locked_fields = '[\"tmdbId\"]' where id = ?", unifiedId);
                }
                return super.update(sql, params);
            }
        };
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());

        repository.rebuildUnifiedPipelineExternalIds(unifiedId);

        assertEquals("35000888", scalar(jdbc, "select douban_id from catalog_entries where id = ?", unifiedId, String.class));
        assertEquals("", scalar(jdbc, "select tmdb_id from catalog_entries where id = ?", unifiedId, String.class));
    }

    @Test
    void rebuildUnifiedAdultAssessmentMarksRestrictionWithoutChangingCategory() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-adult-assessment-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();

        seedStandardCategory(jdbc, UUID.randomUUID(), "movie", "电影");
        jdbc.update("""
                insert into collection_sources (id, name, base_url, adult_restricted)
                values (?, 'Adult Source', 'https://jav.example.test', true)
                """, dataSourceId);
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, category_code,
                    actor_names, director_names, genre_codes, metadata_status
                ) values (
                    ?, now(), now(), 0, '普通电影标题', 'movie',
                    cast('[]' as jsonb), cast('[]' as jsonb), cast('[]' as jsonb), 'SYNCED'
                )
                """, catalogEntryId);
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title,
                    collection_source_id, source_category_code, source_category_name, raw_metadata,
                    enrichment_status, normalization_status, aggregation_status, category_code
                ) values (
                    ?, now(), now(), 0, 'adult-raw', 'adult-hash', 'JAV Sample',
                    ?, 'movie', '电影', cast('{"site":"jav.example.test"}' as jsonb),
                    'SUCCESS', 'READY', 'BOUND', 'movie'
                )
                """, sourceRecordId, dataSourceId);
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);

        repository.rebuildUnifiedAdultAssessment(catalogEntryId);

        assertEquals(Boolean.TRUE, scalar(jdbc, "select adult_restricted from catalog_entries where id = ?", catalogEntryId, Boolean.class));
        assertEquals("movie", scalar(jdbc, "select category_code from catalog_entries where id = ?", catalogEntryId, String.class));
        String assessment = scalar(jdbc, "select adult_assessment from catalog_entries where id = ?", catalogEntryId, String.class);
        assertTrue(assessment.contains("dataSourceAdultRestricted"));
        assertFalse(assessment.contains("rawMetadata"));
    }

    @Test
    void rebuildUnifiedPipelineCoverImageChoosesFirstRawCoverWhenUnifiedCoverIsEmpty() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-empty-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID firstRaw = UUID.randomUUID();
        UUID secondRaw = UUID.randomUUID();
        UUID firstCover = UUID.randomUUID();
        UUID secondCover = UUID.randomUUID();
        seedCoverImage(jdbc, firstCover, "EXTERNAL", 100L);
        seedCoverImage(jdbc, secondCover, "EXTERNAL", 109L);
        seedCoverUnified(jdbc, catalogEntryId, null, "[]");
        seedCoverRaw(jdbc, firstRaw, "hash-cover-first", firstCover, LocalDateTime.of(2026, 1, 1, 0, 0));
        seedCoverRaw(jdbc, secondRaw, "hash-cover-second", secondCover, LocalDateTime.of(2026, 1, 2, 0, 0));
        bindPipelineRaw(jdbc, firstRaw, catalogEntryId);
        bindPipelineRaw(jdbc, secondRaw, catalogEntryId);

        List<UUID> promoteCandidates = repository.rebuildUnifiedPipelineCoverImage(catalogEntryId);

        assertTrue(promoteCandidates.isEmpty());
        assertEquals(firstCover, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    @Test
    void rebuildUnifiedPipelineCoverImagePrefersManagedCoverOverExternalCurrent() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-managed-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID externalCover = UUID.randomUUID();
        UUID localCover = UUID.randomUUID();
        seedCoverImage(jdbc, externalCover, "EXTERNAL", 900L);
        seedCoverImage(jdbc, localCover, "LOCAL", 1L);
        seedCoverUnified(jdbc, catalogEntryId, externalCover, "[]");
        seedCoverRaw(jdbc, sourceRecordId, "hash-cover-local", localCover, LocalDateTime.of(2026, 1, 1, 0, 0));
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);

        List<UUID> promoteCandidates = repository.rebuildUnifiedPipelineCoverImage(catalogEntryId);

        assertEquals(List.of(localCover), promoteCandidates);
        assertEquals(localCover, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    @Test
    void rebuildUnifiedPipelineCoverImageUsesTenPercentThresholdForSameManagedness() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-threshold-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID firstRaw = UUID.randomUUID();
        UUID secondRaw = UUID.randomUUID();
        UUID currentLocal = UUID.randomUUID();
        UUID tooSmallLocal = UUID.randomUUID();
        UUID largeEnoughLocal = UUID.randomUUID();
        seedCoverImage(jdbc, currentLocal, "LOCAL", 100L);
        seedCoverImage(jdbc, tooSmallLocal, "LOCAL", 110L);
        seedCoverImage(jdbc, largeEnoughLocal, "LOCAL", 111L);
        seedCoverUnified(jdbc, catalogEntryId, currentLocal, "[]");
        seedCoverRaw(jdbc, firstRaw, "hash-cover-small", tooSmallLocal, LocalDateTime.of(2026, 1, 1, 0, 0));
        seedCoverRaw(jdbc, secondRaw, "hash-cover-large", largeEnoughLocal, LocalDateTime.of(2026, 1, 2, 0, 0));
        bindPipelineRaw(jdbc, firstRaw, catalogEntryId);
        bindPipelineRaw(jdbc, secondRaw, catalogEntryId);

        List<UUID> promoteCandidates = repository.rebuildUnifiedPipelineCoverImage(catalogEntryId);

        assertEquals(List.of(largeEnoughLocal), promoteCandidates);
        assertEquals(largeEnoughLocal, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    @Test
    void rebuildUnifiedPipelineCoverImageIgnoresCoverImageUrlLockLikeV1Handler() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-locked-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID externalCover = UUID.randomUUID();
        UUID r2Cover = UUID.randomUUID();
        seedCoverImage(jdbc, externalCover, "EXTERNAL", 900L);
        seedCoverImage(jdbc, r2Cover, "R2", null);
        seedCoverUnified(jdbc, catalogEntryId, externalCover, "[\"coverImageUrl\"]");
        seedCoverRaw(jdbc, sourceRecordId, "hash-cover-r2", r2Cover, LocalDateTime.of(2026, 1, 1, 0, 0));
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);

        List<UUID> promoteCandidates = repository.rebuildUnifiedPipelineCoverImage(catalogEntryId);

        assertTrue(promoteCandidates.isEmpty());
        assertEquals(r2Cover, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    @Test
    void rebuildUnifiedPipelineCoverImageDoesNotPromoteLocalCoverUnlessItIsStored() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:aggregation-cover-local-not-stored-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;NON_KEYWORDS=YEAR;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createPipelineSchema(jdbc);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcAggregationRepository repository = JdbcAggregationRepository.forTest(namedJdbc, AggregationTestFixtures.matchingStrategy());
        UUID catalogEntryId = UUID.randomUUID();
        UUID sourceRecordId = UUID.randomUUID();
        UUID localFetchingCover = UUID.randomUUID();
        seedCoverImage(jdbc, localFetchingCover, "LOCAL", "FETCHING", 200L);
        seedCoverUnified(jdbc, catalogEntryId, null, "[]");
        seedCoverRaw(jdbc, sourceRecordId, "hash-cover-fetching", localFetchingCover, LocalDateTime.of(2026, 1, 1, 0, 0));
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);

        List<UUID> promoteCandidates = repository.rebuildUnifiedPipelineCoverImage(catalogEntryId);

        assertTrue(promoteCandidates.isEmpty());
        assertEquals(localFetchingCover, scalar(jdbc, "select media_image_source_id from catalog_entries where id = ?", catalogEntryId, UUID.class));
    }

    private SourceRecordAggregationRecord sourceRecord(String title, String year, String doubanId) {
        return new SourceRecordAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                "Aggregation description",
                year,
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                doubanId,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private RowMapper<CatalogEntryAggregationCandidate> anyUnifiedCandidateRowMapper() {
        return (RowMapper) any(RowMapper.class);
    }

    private void createCandidateSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                create table catalog_categories (
                    id uuid primary key,
                    name varchar(100) not null,
                    slug varchar(100) not null unique
                )
                """);
        jdbc.execute("""
                create table source_records (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    aggregation_status_updated_at timestamp default now() not null,
                    version bigint,
                    source_vid varchar(255) not null,
                    source_hash varchar(64) not null unique,
                    title varchar(255),
                    alias_title varchar(255),
                    description text,
                    year varchar(20),
                    score decimal(3, 1),
                    published_at date,
                    total_episodes varchar(50),
                    duration varchar(50),
                    remarks varchar(255),
                    subtitle varchar(255),
                    season integer,
                    douban_id varchar(20),
                    tmdb_id varchar(20),
                    imdb_id varchar(20),
                    rotten_tomatoes_id varchar(50),
                    enrichment_status varchar(50) not null,
                    normalization_status varchar(50) not null,
                    aggregation_status varchar(50) not null,
                    actor_names jsonb default '[]',
                    director_names jsonb default '[]',
                    area_codes jsonb default '[]',
                    language_codes jsonb default '[]',
                    genre_codes jsonb default '[]',
                    category_code varchar(100)
                )
                """);
        jdbc.execute("""
                create table catalog_entries (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    title varchar(255),
                    alias_title varchar(255),
                    subtitle varchar(255),
                    year varchar(20),
                    total_episodes varchar(50),
                    duration varchar(50),
                    remarks varchar(255),
                    douban_id varchar(20),
                    tmdb_id varchar(20),
                    imdb_id varchar(20),
                    rotten_tomatoes_id varchar(50),
                    season integer,
                    category_id uuid,
                    category_code varchar(100),
                    actor_names jsonb default '[]',
                    director_names jsonb default '[]',
                    area_codes jsonb default '[]',
                    language_codes jsonb default '[]',
                    genre_codes jsonb default '[]',
                    adult_restricted boolean default false not null,
                    adult_assessment jsonb default '{}',
                    adult_checked_at timestamp,
                    metadata_status varchar(50) not null
                )
                """);
    }

    private void createPipelineSchema(JdbcTemplate jdbc) {
        jdbc.execute("create table catalog_metadata_enrichment_jobs(catalog_entry_id uuid primary key,last_success_at timestamp)");
        jdbc.execute("""
                create table catalog_entries (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    title varchar(255) not null,
                    alias_title varchar(255),
                    description text,
                    year varchar(20),
                    score decimal(3, 1),
                    published_at date,
                    douban_id varchar(20),
                    tmdb_id varchar(20),
                    imdb_id varchar(20),
                    rotten_tomatoes_id varchar(50),
                    total_episodes varchar(50),
                    duration varchar(50),
                    remarks varchar(255),
                    season integer,
                    subtitle varchar(255),
                    media_image_source_id uuid,
                    locked_fields jsonb,
                    category_id uuid,
                    category_code varchar(100),
                    actor_names jsonb default '[]',
                    director_names jsonb default '[]',
                    area_codes jsonb default '[]',
                    language_codes jsonb default '[]',
                    genre_codes jsonb default '[]',
                    adult_restricted boolean default false not null,
                    adult_assessment jsonb default '{}',
                    adult_checked_at timestamp,
                    metadata_status varchar(50) not null
                )
                """);
        jdbc.execute("""
                create table source_records (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    aggregation_status_updated_at timestamp default now() not null,
                    version bigint,
                    source_vid varchar(255) not null,
                    source_hash varchar(64) not null unique,
                    title varchar(255) not null,
                    alias_title varchar(255),
                    description text,
                    year varchar(20),
                    score decimal(3, 1),
                    published_at date,
                    douban_id varchar(20),
                    tmdb_id varchar(20),
                    imdb_id varchar(20),
                    rotten_tomatoes_id varchar(50),
                    total_episodes varchar(50),
                    duration varchar(50),
                    remarks varchar(255),
                    season integer,
                    subtitle varchar(255),
                    media_image_source_id uuid,
                    collection_source_id uuid,
                    source_category_code varchar(100),
                    source_category_name varchar(255),
                    raw_metadata jsonb default '{}',
                    enrichment_status varchar(50) not null,
                    normalization_status varchar(50) not null,
                    aggregation_status varchar(50) not null,
                    actor_names jsonb default '[]',
                    director_names jsonb default '[]',
                    area_codes jsonb default '[]',
                    language_codes jsonb default '[]',
                    genre_codes jsonb default '[]',
                    category_code varchar(100)
                )
                """);
        jdbc.execute("""
                create table catalog_source_bindings (
                    source_record_id uuid not null,
                    catalog_entry_id uuid not null,
                    primary key (source_record_id, catalog_entry_id)
                )
                """);
        jdbc.execute("""
                create table collection_sources (
                    id uuid primary key,
                    name varchar(255) not null,
                    base_url varchar(1024),
                    adult_restricted boolean default false not null
                )
                """);
        jdbc.execute("""
                create table catalog_categories (
                    id uuid primary key,
                    name varchar(100) not null,
                    slug varchar(100) not null unique
                )
                """);
        jdbc.execute("""
                create table media_image_sources (
                    id uuid primary key,
                    storage_type varchar(50) not null,
                    status varchar(50) not null,
                    file_size bigint,
                    source_origin_id uuid
                )
                """);
        jdbc.execute("""
                create table source_origins (
                    id uuid primary key,
                    domain_value varchar(1024) not null
                )
                """);
    }

    private void seedMergeFixture(
            JdbcTemplate jdbc,
            UUID rootId,
            UUID victimId,
            UUID rawId,
            UUID memberWithConflict,
            UUID memberWithoutConflict) {
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, alias_title, description, score, published_at,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, total_episodes, duration, remarks,
                    season, subtitle, last_trend_at, locked_fields, metadata_status
                ) values (?, now(), now(), 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', 'SYNCED')
                """,
                rootId,
                "Root",
                "root-alias",
                null,
                null,
                LocalDate.of(2026, 1, 2),
                null,
                "root-tmdb",
                null,
                null,
                null,
                "45m",
                "root-remarks",
                null,
                null,
                LocalDateTime.of(2026, 6, 1, 10, 0));
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, alias_title, description, score, published_at,
                    douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, total_episodes, duration, remarks,
                    season, subtitle, last_trend_at, locked_fields, metadata_status
                ) values (?, now(), now(), 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', 'SYNCED')
                """,
                victimId,
                "Victim",
                "victim-alias",
                "victim-description",
                new BigDecimal("8.8"),
                LocalDate.of(2026, 3, 4),
                "victim-douban",
                "victim-tmdb",
                "victim-imdb",
                "victim-rt",
                "12",
                "50m",
                "victim-remarks",
                2,
                "victim-subtitle",
                LocalDateTime.of(2026, 6, 2, 10, 0));
        jdbc.update("insert into source_records (id, created_at, updated_at, version, source_vid, source_hash, title, enrichment_status, normalization_status, aggregation_status) values (?, now(), now(), 0, 'raw-1', 'hash-1', 'Raw', 'SUCCESS', 'READY', 'BOUND')", rawId);
        jdbc.update("insert into catalog_source_bindings (source_record_id, catalog_entry_id) values (?, ?)", rawId, victimId);
        seedMember(jdbc, memberWithConflict, "conflict@example.test");
        seedMember(jdbc, memberWithoutConflict, "move@example.test");
        jdbc.update("insert into member_catalog_favorites (id, created_at, updated_at, version, member_id, catalog_entry_id) values (?, now(), now(), 0, ?, ?)", UUID.randomUUID(), memberWithConflict, rootId);
        jdbc.update("insert into member_catalog_favorites (id, created_at, updated_at, version, member_id, catalog_entry_id) values (?, now(), now(), 0, ?, ?)", UUID.randomUUID(), memberWithConflict, victimId);
        jdbc.update("insert into member_catalog_favorites (id, created_at, updated_at, version, member_id, catalog_entry_id) values (?, now(), now(), 0, ?, ?)", UUID.randomUUID(), memberWithoutConflict, victimId);
        jdbc.update("insert into member_playback_progress (id, created_at, updated_at, version, member_id, catalog_entry_id, work_id, season_id, episode_name, progress_seconds, duration_seconds, last_watched_at) select ?, now(), now(), 0, ?, id, work_id, season_id, 'Root Episode', 1, 10, now() from catalog_entries where id=?", UUID.randomUUID(), memberWithConflict, rootId);
        jdbc.update("insert into member_playback_progress (id, created_at, updated_at, version, member_id, catalog_entry_id, work_id, season_id, episode_name, progress_seconds, duration_seconds, last_watched_at) select ?, now(), now(), 0, ?, id, work_id, season_id, 'Victim Episode', 2, 10, now() from catalog_entries where id=?", UUID.randomUUID(), memberWithConflict, victimId);
        jdbc.update("insert into member_playback_progress (id, created_at, updated_at, version, member_id, catalog_entry_id, work_id, season_id, episode_name, progress_seconds, duration_seconds, last_watched_at) select ?, now(), now(), 0, ?, id, work_id, season_id, 'Move Episode', 3, 10, now() from catalog_entries where id=?", UUID.randomUUID(), memberWithoutConflict, victimId);
        jdbc.update("""
                update catalog_entries
                   set actor_names = cast(? as jsonb),
                       genre_codes = cast(? as jsonb),
                       category_code = 'movie'
                 where id = ?
                """, flatJson("Root Actor"), flatJson("shared-genre"), rootId);
        jdbc.update("""
                update catalog_entries
                   set actor_names = cast(? as jsonb),
                       director_names = cast(? as jsonb),
                       genre_codes = cast(? as jsonb),
                       language_codes = cast(? as jsonb),
                       area_codes = cast(? as jsonb),
                       category_code = 'tv'
                 where id = ?
                """,
                flatJson("Victim Actor"),
                flatJson("Victim Director"),
                flatJson("shared-genre", "victim-genre"),
                flatJson("zh"),
                flatJson("CN"),
                victimId);
    }

    private void seedPipelineUnlockedFixture(
            JdbcTemplate jdbc,
            UUID catalogEntryId,
            UUID oldCategoryId,
            UUID votedCategoryId,
            UUID losingCategoryId,
            UUID rawOne,
            UUID rawTwo,
            UUID rawThree,
            UUID actorOne,
            UUID actorTwo,
            UUID staleActor,
            UUID directorOne,
            UUID staleDirector,
            UUID genreOne,
            UUID genreTwo,
            UUID staleGenre,
            UUID languageOne,
            UUID staleLanguage,
            UUID areaOne,
            UUID staleArea) {
        jdbc.update("insert into catalog_entries (id, created_at, updated_at, version, title, locked_fields, category_id, category_code, actor_names, director_names, genre_codes, language_codes, area_codes, metadata_status) values (?, now(), now(), 0, 'Unified', '[]', ?, 'old-category', cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), 'SYNCED')",
                catalogEntryId,
                oldCategoryId,
                flatJson("Stale Actor"),
                flatJson("Stale Director"),
                flatJson("stale-genre"),
                flatJson("stale-language"),
                flatJson("stale-area"));
        seedStandardCategory(jdbc, votedCategoryId, "movie", "电影");
        seedStandardCategory(jdbc, losingCategoryId, "tv", "电视剧");
        seedPipelineSourceRecord(jdbc, rawOne, "hash-pipeline-1", "movie");
        seedPipelineSourceRecord(jdbc, rawTwo, "hash-pipeline-2", "movie");
        seedPipelineSourceRecord(jdbc, rawThree, "hash-pipeline-3", "tv");
        bindPipelineRaw(jdbc, rawOne, catalogEntryId);
        bindPipelineRaw(jdbc, rawTwo, catalogEntryId);
        bindPipelineRaw(jdbc, rawThree, catalogEntryId);
        updateRawFlatMetadata(
                jdbc,
                rawOne,
                flatJson("Actor One"),
                flatJson(),
                flatJson(),
                flatJson("language-one"),
                flatJson("genre-one"),
                "movie");
        updateRawFlatMetadata(
                jdbc,
                rawTwo,
                flatJson("Actor Two"),
                flatJson("Director One"),
                flatJson("area-one"),
                flatJson(),
                flatJson("genre-one"),
                "movie");
        updateRawFlatMetadata(
                jdbc,
                rawThree,
                flatJson("Actor One"),
                flatJson(),
                flatJson(),
                flatJson(),
                flatJson("genre-two"),
                "tv");
    }

    private void seedPipelineLockedFixture(
            JdbcTemplate jdbc,
            UUID catalogEntryId,
            UUID lockedCategoryId,
            UUID rawCategoryId,
            UUID sourceRecordId,
            UUID lockedActor,
            UUID rawActor,
            UUID lockedDirector,
            UUID rawDirector,
            UUID lockedGenre,
            UUID rawGenre,
            UUID lockedLanguage,
            UUID rawLanguage,
            UUID lockedArea,
            UUID rawArea) {
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, locked_fields, category_id, category_code,
                    actor_names, director_names, genre_codes, language_codes, area_codes, metadata_status
                ) values (
                    ?, now(), now(), 0, 'Locked Unified',
                    '["category","actors","directors","genres","area","language"]',
                    ?, 'locked-category',
                    cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), 'SYNCED'
                )
                """,
                catalogEntryId,
                lockedCategoryId,
                flatJson("Locked Actor"),
                flatJson("Locked Director"),
                flatJson("locked-genre"),
                flatJson("locked-language"),
                flatJson("locked-area"));
        seedStandardCategory(jdbc, rawCategoryId, "raw-category", "Raw Category");
        seedPipelineSourceRecord(jdbc, sourceRecordId, "hash-pipeline-locked", "raw-category");
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);
        updateRawFlatMetadata(
                jdbc,
                sourceRecordId,
                flatJson("Raw Actor"),
                flatJson("Raw Director"),
                flatJson("raw-area"),
                flatJson("raw-language"),
                flatJson("raw-genre"),
                "raw-category");
    }

    private void seedStandardCategory(JdbcTemplate jdbc, UUID categoryId, String slug, String name) {
        jdbc.update("insert into catalog_categories (id, name, slug) values (?, ?, ?)", categoryId, name, slug);
    }

    private void seedPipelineSourceRecord(JdbcTemplate jdbc, UUID sourceRecordId, String sourceHash, String categoryCode) {
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title,
                    enrichment_status, normalization_status, aggregation_status, category_code
                ) values (?, now(), now(), 0, ?, ?, 'Raw', 'SUCCESS', 'READY', 'BOUND', ?)
                """, sourceRecordId, sourceHash, sourceHash, categoryCode);
    }

    private void updateRawFlatMetadata(
            JdbcTemplate jdbc,
            UUID sourceRecordId,
            String actorNames,
            String directorNames,
            String areaCodes,
            String languageCodes,
            String genreCodes,
            String categoryCode) {
        jdbc.update("""
                update source_records
                   set actor_names = cast(? as jsonb),
                       director_names = cast(? as jsonb),
                       area_codes = cast(? as jsonb),
                       language_codes = cast(? as jsonb),
                       genre_codes = cast(? as jsonb),
                       category_code = ?
                 where id = ?
                """, actorNames, directorNames, areaCodes, languageCodes, genreCodes, categoryCode, sourceRecordId);
    }

    private void seedScalarUnlockedFixture(JdbcTemplate jdbc, UUID catalogEntryId, UUID rawOne, UUID rawTwo, UUID rawThree) {
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, alias_title, description, year, score,
                    published_at, douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, total_episodes,
                    duration, remarks, season, subtitle, locked_fields, metadata_status
                ) values (
                    ?, timestamp '2026-01-01 00:00:00', timestamp '2026-01-01 00:00:00', 0,
                    'Old Title', 'old alias', 'old description', '2000', 1.0, date '2000-01-01',
                    '35000999', '', '', '', '', '', 'old remarks', null, 'old subtitle',
                    '[]', 'SYNCED'
                )
                """, catalogEntryId);
        seedScalarSourceRecord(
                jdbc,
                rawOne,
                "hash-scalar-1",
                "Voted Title",
                "short",
                "short description",
                "2026",
                new BigDecimal("8.5"),
                LocalDate.of(2025, 1, 1),
                "0",
                "70001",
                null,
                "rt-first-valid",
                "12",
                "45m",
                "older remarks",
                2,
                "sub",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 1, 0));
        seedScalarSourceRecord(
                jdbc,
                rawTwo,
                "hash-scalar-2",
                "Voted Title",
                "longest alias title",
                "the longest description wins",
                "2026",
                new BigDecimal("9.5"),
                LocalDate.of(2025, 2, 1),
                "35000999",
                "70001",
                "tt0133093",
                null,
                "24",
                "60m",
                "latest remarks",
                2,
                "longest subtitle value",
                LocalDateTime.of(2026, 1, 2, 0, 0),
                LocalDateTime.of(2026, 1, 4, 1, 0));
        seedScalarSourceRecord(
                jdbc,
                rawThree,
                "hash-scalar-3",
                "Other Title",
                "alias",
                "medium description",
                "2025",
                new BigDecimal("7.0"),
                LocalDate.of(2025, 3, 1),
                null,
                null,
                null,
                null,
                "36",
                "90m",
                "middle remarks",
                null,
                "subtitle",
                LocalDateTime.of(2026, 1, 3, 0, 0),
                LocalDateTime.of(2026, 1, 3, 1, 0));
        bindPipelineRaw(jdbc, rawOne, catalogEntryId);
        bindPipelineRaw(jdbc, rawTwo, catalogEntryId);
        bindPipelineRaw(jdbc, rawThree, catalogEntryId);
    }

    private void seedScalarLockedFixture(JdbcTemplate jdbc, UUID catalogEntryId, UUID sourceRecordId) {
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, alias_title, description, year, score,
                    published_at, douban_id, tmdb_id, imdb_id, rotten_tomatoes_id, total_episodes,
                    duration, remarks, season, subtitle, locked_fields, metadata_status
                ) values (
                    ?, timestamp '2026-01-01 00:00:00', timestamp '2026-01-01 00:00:00', 0,
                    'Locked Title', 'locked alias', 'locked description', '1999', 1.1, date '1999-01-01',
                    '', '', '', '', '1', '10m', 'locked remarks', 1, 'locked subtitle',
                    '["title","year","score","publishedAt","aliasTitle","description","remarks","totalEpisodes","duration","season","subtitle","doubanId","tmdb_id","imdbId","rotten_tomatoes_id"]',
                    'SYNCED'
                )
                """, catalogEntryId);
        seedScalarSourceRecord(
                jdbc,
                sourceRecordId,
                "hash-scalar-locked",
                "Raw Title",
                "raw alias longest",
                "raw description longest",
                "2026",
                new BigDecimal("9.9"),
                LocalDate.of(2026, 6, 9),
                "35000001",
                "70001",
                "tt0133093",
                "raw-rt",
                "99",
                "99m",
                "raw remarks",
                9,
                "raw subtitle longest",
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 1, 0));
        bindPipelineRaw(jdbc, sourceRecordId, catalogEntryId);
    }

    private void seedScalarSourceRecord(
            JdbcTemplate jdbc,
            UUID sourceRecordId,
            String sourceHash,
            String title,
            String aliasTitle,
            String description,
            String year,
            BigDecimal score,
            LocalDate publishedAt,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            String totalEpisodes,
            String duration,
            String remarks,
            Integer season,
            String subtitle,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, alias_title,
                    description, year, score, published_at, douban_id, tmdb_id, imdb_id,
                    rotten_tomatoes_id, total_episodes, duration, remarks, season, subtitle,
                    enrichment_status, normalization_status, aggregation_status
                ) values (?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', 'READY', 'BOUND')
                """,
                sourceRecordId,
                createdAt,
                updatedAt,
                sourceHash,
                sourceHash,
                title,
                aliasTitle,
                description,
                year,
                score,
                publishedAt,
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                totalEpisodes,
                duration,
                remarks,
                season,
                subtitle);
    }

    private void seedCoverImage(JdbcTemplate jdbc, UUID coverImageId, String storageType, Long fileSize) {
        seedCoverImage(jdbc, coverImageId, storageType, defaultCoverStatus(storageType), fileSize);
    }

    private void seedCoverImage(JdbcTemplate jdbc, UUID coverImageId, String storageType, String status, Long fileSize) {
        jdbc.update(
                "insert into media_image_sources (id, storage_type, status, file_size) values (?, ?, ?, ?)",
                coverImageId,
                storageType,
                status,
                fileSize);
    }

    private String defaultCoverStatus(String storageType) {
        return switch (storageType) {
            case "LOCAL" -> "LOCAL_STORED";
            case "R2" -> "REMOTE_STORED";
            default -> "UNPROCESSED";
        };
    }

    private void seedCoverUnified(JdbcTemplate jdbc, UUID catalogEntryId, UUID coverImageId, String lockedFields) {
        jdbc.update("""
                insert into catalog_entries (
                    id, created_at, updated_at, version, title, media_image_source_id, locked_fields, metadata_status
                ) values (?, timestamp '2026-01-01 00:00:00', timestamp '2026-01-01 00:00:00', 0, 'Unified', ?, ?, 'SYNCED')
                """,
                catalogEntryId,
                coverImageId,
                lockedFields);
    }

    private void seedCoverRaw(
            JdbcTemplate jdbc,
            UUID sourceRecordId,
            String sourceHash,
            UUID coverImageId,
            LocalDateTime createdAt) {
        jdbc.update("""
                insert into source_records (
                    id, created_at, updated_at, version, source_vid, source_hash, title, media_image_source_id,
                    enrichment_status, normalization_status, aggregation_status
                ) values (?, ?, ?, 0, ?, ?, 'Raw', ?, 'SUCCESS', 'READY', 'BOUND')
                """,
                sourceRecordId,
                createdAt,
                createdAt,
                sourceHash,
                sourceHash,
                coverImageId);
    }

    private void bindPipelineRaw(JdbcTemplate jdbc, UUID sourceRecordId, UUID catalogEntryId) {
        jdbc.update(
                "insert into catalog_source_bindings (source_record_id, catalog_entry_id) values (?, ?)",
                sourceRecordId,
                catalogEntryId);
    }

    private void seedMember(JdbcTemplate jdbc, UUID memberId, String email) {
        jdbc.update(
                "insert into member_accounts (id, created_at, updated_at, email, password_hash, role, status) values (?, now(), now(), ?, 'hash', 'USER', 'ACTIVE')",
                memberId,
                email);
    }

    private int count(JdbcTemplate jdbc, String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private <T> T scalar(JdbcTemplate jdbc, String sql, Object arg, Class<T> type) {
        return jdbc.queryForObject(sql, type, arg);
    }

    private Set<String> jsonSet(JdbcTemplate jdbc, String sql, Object arg) {
        return JsonStringArrays.readSet(OBJECT_MAPPER, scalar(jdbc, sql, arg, String.class));
    }

    private String flatJson(String... values) {
        return JsonStringArrays.write(OBJECT_MAPPER, List.of(values));
    }

    private boolean containsRetiredUnifiedMetadataTable(String sql) {
        return sql.contains("unified_video_actors")
                || sql.contains("unified_video_directors")
                || sql.contains("unified_video_genres")
                || sql.contains("unified_video_standard_languages")
                || sql.contains("unified_video_standard_areas");
    }
}
