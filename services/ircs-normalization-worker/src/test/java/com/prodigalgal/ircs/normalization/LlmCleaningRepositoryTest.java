package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class LlmCleaningRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void findCandidateReadsExactQueuedRawIdWithoutDbLease() {
        UUID rawId = UUID.randomUUID();
        when(jdbcTemplate.query(
                        argThat(sql -> sql != null
                                && sql.contains("from raw_languages")
                                && sql.contains("where id = :rawId")
                                && sql.contains("standard_language_id is null")
                                && !sql.contains("skip locked")
                                && !sql.contains("llm_cleaning_claimed")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of(new LlmCleaningCandidate(rawId, "国语")));
        LlmCleaningRepository repository = new LlmCleaningRepository(jdbcTemplate);

        Optional<LlmCleaningCandidate> candidate = repository.findCandidate(LlmCleaningKind.LANGUAGE, rawId);

        assertThat(candidate).contains(new LlmCleaningCandidate(rawId, "国语"));
    }

    @Test
    void applyMatchOnlyUpdatesUnmappedRawRows() {
        UUID rawId = UUID.randomUUID();
        UUID standardId = UUID.randomUUID();
        when(jdbcTemplate.update(
                        argThat(sql -> sql != null
                                && sql.contains("update raw_languages")
                                && sql.contains("set standard_language_id = :standardId")
                                && sql.contains("updated_at = now()")
                                && sql.contains("standard_language_id is null")
                                && !sql.contains("llm_cleaning_claimed")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        LlmCleaningRepository repository = new LlmCleaningRepository(jdbcTemplate);

        boolean changed = repository.applyMatch(LlmCleaningKind.LANGUAGE, rawId, standardId);

        assertThat(changed).isTrue();
    }

    @Test
    void applyNoiseOnlyDeletesUnmappedNonCategoryRawRows() {
        UUID rawId = UUID.randomUUID();
        when(jdbcTemplate.update(
                        argThat(sql -> sql != null
                                && sql.contains("delete from video_raw_genres")
                                && sql.contains("exists")
                                && sql.contains("from raw_genres")
                                && sql.contains("standard_genre_id is null")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbcTemplate.update(
                        argThat(sql -> sql != null
                                && sql.contains("delete from raw_genres")
                                && sql.contains("where id = :rawId and standard_genre_id is null")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        LlmCleaningRepository repository = new LlmCleaningRepository(jdbcTemplate);

        boolean changed = repository.applyNoise(LlmCleaningKind.GENRE, rawId);

        assertThat(changed).isTrue();
    }

    @Test
    void categoryNoiseDoesNotOverwriteAlreadyMappedRows() {
        UUID rawId = UUID.randomUUID();
        when(jdbcTemplate.update(
                        argThat(sql -> sql != null
                                && sql.contains("update raw_category")
                                && sql.contains("category_id is null")
                                && sql.contains("where id = :rawId")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(
                        argThat(sql -> sql != null
                                && sql.contains("from standard_category")
                                && sql.contains("lower(name) = lower(:fallback)")),
                        any(MapSqlParameterSource.class),
                        eq(Boolean.class)))
                .thenReturn(true);
        LlmCleaningRepository repository = new LlmCleaningRepository(jdbcTemplate);

        boolean changed = repository.applyNoise(LlmCleaningKind.CATEGORY, rawId);

        assertThat(changed).isFalse();
        verify(jdbcTemplate).queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Boolean.class));
    }
}
