package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
class RawVideoNormalizationRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void markReadyUpsertsRawRelationsAndReplacesJoinRows() {
        UUID rawVideoId = UUID.randomUUID();
        UUID genreId = UUID.randomUUID();
        UUID languageId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID directorId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(contains("raw_genres"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(genreId));
        when(jdbcTemplate.queryForList(contains("raw_languages"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(languageId));
        when(jdbcTemplate.queryForList(contains("raw_areas"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(areaId));
        when(jdbcTemplate.queryForList(contains("actors"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(actorId));
        when(jdbcTemplate.queryForList(contains("directors"), any(MapSqlParameterSource.class), eq(UUID.class)))
                .thenReturn(List.of(directorId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        repository.markReady(rawVideoId, patch(
                Set.of("动作"),
                Set.of("国语"),
                Set.of("中国大陆"),
                Set.of("演员甲"),
                Set.of("导演甲")));

        verify(jdbcTemplate).batchUpdate(contains("insert into raw_genres"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into raw_languages"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into raw_areas"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(
                argThat(sql -> sql.contains("standard_genre_id")
                        && sql.contains("standard_genre")
                        && sql.contains("coalesce(raw_genres.standard_genre_id, excluded.standard_genre_id)")),
                any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(
                argThat(sql -> sql.contains("standard_language_id")
                        && sql.contains("standard_languages")
                        && sql.contains("coalesce(raw_languages.standard_language_id, excluded.standard_language_id)")),
                any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(
                argThat(sql -> sql.contains("standard_area_id")
                        && sql.contains("standard_areas")
                        && sql.contains("coalesce(raw_areas.standard_area_id, excluded.standard_area_id)")),
                any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into actors"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into directors"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).update(startsWith("delete from video_raw_genres"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(startsWith("delete from video_raw_languages"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(startsWith("delete from video_raw_areas"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(startsWith("delete from video_actors"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(startsWith("delete from video_directors"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).batchUpdate(contains("insert into video_raw_genres"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into video_raw_languages"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into video_raw_areas"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into video_actors"), any(SqlParameterSource[].class));
        verify(jdbcTemplate).batchUpdate(contains("insert into video_directors"), any(SqlParameterSource[].class));
    }

    @Test
    void markReadyDoesNotReplaceRelationsWhenPatchHasNoRelationValues() {
        UUID rawVideoId = UUID.randomUUID();
        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        repository.markReady(rawVideoId, patch(Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));

        verify(jdbcTemplate, never()).batchUpdate(contains("insert into raw_genres"), any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).batchUpdate(contains("insert into raw_languages"), any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).batchUpdate(contains("insert into raw_areas"), any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).batchUpdate(contains("insert into actors"), any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).batchUpdate(contains("insert into directors"), any(SqlParameterSource[].class));
        verify(jdbcTemplate, never()).update(startsWith("delete from video_raw_genres"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).update(startsWith("delete from video_raw_languages"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).update(startsWith("delete from video_raw_areas"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).update(startsWith("delete from video_actors"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, never()).update(startsWith("delete from video_directors"), any(MapSqlParameterSource.class));
    }

    @Test
    void markReadyUpsertsRawCategoryAndBindsRawVideoWithoutClearingStandardCategory() {
        UUID rawVideoId = UUID.randomUUID();
        UUID dataSourceId = UUID.randomUUID();
        UUID rawCategoryId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                        contains("insert into raw_category"),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(rawCategoryId);

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        repository.markReady(rawVideoId, patchWithCategory(dataSourceId, "movie", "电影"));

        verify(jdbcTemplate).queryForObject(
                argThat(sql -> sql.contains("insert into raw_category")
                        && sql.contains("category_id")
                        && sql.contains("standard_category")
                        && sql.contains("coalesce(raw_category.category_id, excluded.category_id)")),
                any(MapSqlParameterSource.class),
                eq(UUID.class));
        verify(jdbcTemplate).update(contains("data_source_category_id"), any(MapSqlParameterSource.class));
    }

    @Test
    void findDueFailedNormalizationRecoveryIdsScansDueFailuresOnly() {
        UUID failedId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-07T10:00:00Z");
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("normalization_status = 'FAILED'")
                                && sql.contains("next_normalization_retry_time <= :now")
                                && !sql.contains("normalization_status = 'PENDING'")
                                && !sql.contains("PERMANENT_FAILURE")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(failedId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        List<UUID> result = repository.findDueFailedNormalizationRecoveryIds(now, 25);

        assertEquals(List.of(failedId), result);
    }

    @Test
    void claimDueFailedNormalizationRecoveryIdsMarksPendingWithRowLease() {
        UUID failedId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-07T10:00:00Z");
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("for update skip locked")
                                && sql.contains("update raw_videos rv")
                                && sql.contains("normalization_status = 'FAILED'")
                                && sql.contains("next_normalization_retry_time <= :now")
                                && sql.contains("normalization_status = 'PENDING'")
                                && sql.contains("next_normalization_retry_time = null")
                                && sql.contains("returning rv.id")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(failedId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        List<UUID> result = repository.claimDueFailedNormalizationRecoveryIds(now, 25);

        assertEquals(List.of(failedId), result);
    }

    @Test
    void findStaleActiveNormalizationRecoveryIdsScansPendingAndProcessingOnly() {
        UUID pendingId = UUID.randomUUID();
        UUID processingId = UUID.randomUUID();
        Instant staleBefore = Instant.parse("2026-06-07T09:50:00Z");
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("normalization_status in ('PENDING', 'PROCESSING')")
                                && sql.contains("updated_at <= :staleBefore")
                                && !sql.contains("PERMANENT_FAILURE")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(pendingId, processingId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        List<UUID> result = repository.findStaleActiveNormalizationRecoveryIds(staleBefore, 25);

        assertEquals(List.of(pendingId, processingId), result);
    }

    @Test
    void claimStaleActiveNormalizationRecoveryIdsMarksPendingWithRowLease() {
        UUID pendingId = UUID.randomUUID();
        UUID processingId = UUID.randomUUID();
        Instant staleBefore = Instant.parse("2026-06-07T09:50:00Z");
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("for update skip locked")
                                && sql.contains("update raw_videos rv")
                                && sql.contains("normalization_status in ('PENDING', 'PROCESSING')")
                                && sql.contains("updated_at <= :staleBefore")
                                && sql.contains("normalization_status = 'PENDING'")
                                && sql.contains("next_normalization_retry_time = null")
                                && sql.contains("returning rv.id")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(pendingId, processingId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        List<UUID> result = repository.claimStaleActiveNormalizationRecoveryIds(staleBefore, 25);

        assertEquals(List.of(pendingId, processingId), result);
    }

    @Test
    void markRecoveryQueuedResetsRecoverableStatusesToPending() {
        UUID rawVideoId = UUID.randomUUID();
        when(jdbcTemplate.update(
                        argThat(sql -> sql.contains("normalization_status = 'PENDING'")
                                && sql.contains("next_normalization_retry_time = null")
                                && sql.contains("normalization_status in ('FAILED', 'PENDING', 'PROCESSING')")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        assertEquals(true, repository.markRecoveryQueued(rawVideoId));
    }

    @Test
    void releaseRecoveryPublishFailuresRestoresPendingClaimsToFailedRetry() {
        UUID rawVideoId = UUID.randomUUID();
        Instant nextRetryTime = Instant.parse("2026-06-07T10:01:00Z");
        when(jdbcTemplate.update(
                        argThat(sql -> sql.contains("normalization_status = 'FAILED'")
                                && sql.contains("next_normalization_retry_time = :nextRetryTime")
                                && sql.contains("normalization_status = 'PENDING'")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        assertEquals(1, repository.releaseRecoveryPublishFailures(List.of(rawVideoId), nextRetryTime));
    }

    @Test
    void sampleRawVideoIdsOrdersByOldestUpdatedAt() {
        UUID rawVideoId = UUID.randomUUID();
        when(jdbcTemplate.queryForList(
                        argThat(sql -> sql.contains("from raw_videos")
                                && sql.contains("order by updated_at asc nulls first, id asc")
                                && sql.contains("limit :limit")),
                        any(MapSqlParameterSource.class),
                        eq(UUID.class)))
                .thenReturn(List.of(rawVideoId));

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        assertEquals(List.of(rawVideoId), repository.sampleRawVideoIds(5));
    }

    @Test
    void resetAllNormalizationPendingKeepsV1StatusOnlySemantics() {
        when(jdbcTemplate.update(
                        argThat(sql -> sql.contains("update raw_videos")
                                && sql.contains("normalization_status = 'PENDING'")
                                && sql.contains("updated_at = now()")
                                && !sql.contains("next_normalization_retry_time")
                                && !sql.contains("normalization_retry_count")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(12);

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        assertEquals(12, repository.resetAllNormalizationPending());
    }

    @Test
    void countRawVideosReturnsZeroWhenJdbcReturnsNull() {
        when(jdbcTemplate.queryForObject(
                        contains("select count(*) from raw_videos"),
                        any(MapSqlParameterSource.class),
                        eq(Long.class)))
                .thenReturn(null);

        RawVideoNormalizationRepository repository = new RawVideoNormalizationRepository(jdbcTemplate);

        assertEquals(0L, repository.countRawVideos());
    }

    private RawVideoPatch patch(
            Set<String> genres,
            Set<String> languages,
            Set<String> areas,
            Set<String> actors,
            Set<String> directors) {
        return patch(null, null, null, genres, languages, areas, actors, directors);
    }

    private RawVideoPatch patchWithCategory(UUID dataSourceId, String sourceCode, String sourceName) {
        return patch(dataSourceId, sourceCode, sourceName, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private RawVideoPatch patch(
            UUID dataSourceId,
            String sourceCode,
            String sourceName,
            Set<String> genres,
            Set<String> languages,
            Set<String> areas,
            Set<String> actors,
            Set<String> directors) {
        return new RawVideoPatch(
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
