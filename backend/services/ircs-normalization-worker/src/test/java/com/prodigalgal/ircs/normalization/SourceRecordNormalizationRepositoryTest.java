package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class SourceRecordNormalizationRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void markReadyWritesFlatMetadataWithoutLegacyRelationTables() {
        UUID sourceRecordId = UUID.randomUUID();
        doReturn(List.of("action"))
                .when(jdbcTemplate)
                .queryForList(argThat(sql -> sql.contains("catalog_genres sg")), any(MapSqlParameterSource.class), eq(String.class));
        doReturn(List.of("zh-CN"))
                .when(jdbcTemplate)
                .queryForList(argThat(sql -> sql.contains("catalog_languages sl")), any(MapSqlParameterSource.class), eq(String.class));
        doReturn(List.of("CN"))
                .when(jdbcTemplate)
                .queryForList(argThat(sql -> sql.contains("catalog_regions sa")), any(MapSqlParameterSource.class), eq(String.class));

        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        SourceRecordNormalizationRepository.MarkReadyResult result = repository.markReady(sourceRecordId, patch(
                Set.of("动作"),
                Set.of("国语"),
                Set.of("中国大陆"),
                Set.of("演员甲"),
                Set.of("导演甲")));

        assertEquals(new SourceRecordNormalizationRepository.MarkReadyResult(), result);
        verify(jdbcTemplate).update(
                argThat(sql -> sql != null
                        && sql.contains("actor_names")
                        && sql.contains("director_names")
                        && sql.contains("area_codes")
                        && sql.contains("language_codes")
                        && sql.contains("genre_codes")
                        && sql.contains("normalization_snapshot")),
                any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(SqlParameterSource[].class));
    }

    @Test
    void markReadyDoesNotResolveCodesWhenPatchHasNoRelationValues() {
        UUID sourceRecordId = UUID.randomUUID();
        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        repository.markReady(sourceRecordId, patch(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));

        verify(jdbcTemplate, never()).queryForList(
                argThat(sql -> sql != null && sql.contains("standard_")),
                any(MapSqlParameterSource.class),
                eq(String.class));
        verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(SqlParameterSource[].class));
    }

    @Test
    void markReadyWritesSourceCategoryIntoSourceRecordWithoutLegacyMappingTable() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                        argThat(sql -> sql.contains("select slug")
                                && sql.contains("from catalog_categories")
                                && sql.contains("cast(:fallbackCategoryCode as text)")),
                        any(MapSqlParameterSource.class),
                        eq(String.class)))
                .thenReturn("movie");

        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        repository.markReady(sourceRecordId, patchWithCategory(dataSourceId, "movie", "电影"));

        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("select slug")
                        && sql.contains("catalog_categories")
                        && sql.contains("cast(:fallbackCategoryCode as text)")),
                any(MapSqlParameterSource.class),
                eq(String.class));
        verify(jdbcTemplate).update(
                argThat(sql -> sql.contains("update source_records")
                        && sql.contains("collection_source_id = coalesce(collection_source_id, :dataSourceId)")
                        && sql.contains("source_category_code")
                        && sql.contains("source_category_name")
                        && sql.contains("category_code")),
                any(MapSqlParameterSource.class));
    }

    @Test
    void sampleSourceRecordIdsOrdersByOldestUpdatedAt() {
        UUID sourceRecordId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("from source_records")
                                && sql.contains("order by updated_at asc nulls first, id asc")
                                && sql.contains("limit :limit")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(sourceRecordId));

        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        assertEquals(List.of(sourceRecordId), repository.sampleSourceRecordIds(5));
    }

    @Test
    void resetAllNormalizationPendingClearsRetryStateForHistoricalRemap() {
        when(jdbcTemplate.update(
                        argThat(sql -> sql.contains("update source_records")
                                && sql.contains("normalization_status = 'PENDING'")
                                && sql.contains("updated_at = now()")
                                && sql.contains("next_normalization_retry_time = null")
                                && sql.contains("normalization_retry_count = 0")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(12);

        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        assertEquals(12, repository.resetAllNormalizationPending());
    }

    @Test
    void countSourceRecordsReturnsZeroWhenJdbcReturnsNull() {
        when(jdbcTemplate.queryForObject(
                        contains("select count(*) from source_records"),
                        any(MapSqlParameterSource.class),
                        eq(Long.class)))
                .thenReturn(null);

        SourceRecordNormalizationRepository repository = SourceRecordNormalizationRepository.forTest(jdbcTemplate);

        assertEquals(0L, repository.countSourceRecords());
    }

    private SourceRecordPatch patch(
            Set<String> genres,
            Set<String> languages,
            Set<String> areas,
            Set<String> actors,
            Set<String> directors) {
        return patch(null, null, null, genres, languages, areas, actors, directors);
    }

    private SourceRecordPatch patchWithCategory(UUID dataSourceId, String sourceCode, String sourceName) {
        return patch(dataSourceId, sourceCode, sourceName, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private SourceRecordPatch patch(
            UUID dataSourceId,
            String sourceCode,
            String sourceName,
            Set<String> genres,
            Set<String> languages,
            Set<String> areas,
            Set<String> actors,
            Set<String> directors) {
        return new SourceRecordPatch(
                "Title",
                null,
                null,
                null,
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                dataSourceId,
                sourceCode,
                sourceName,
                genres,
                languages,
                areas,
                actors,
                directors);
    }
}
