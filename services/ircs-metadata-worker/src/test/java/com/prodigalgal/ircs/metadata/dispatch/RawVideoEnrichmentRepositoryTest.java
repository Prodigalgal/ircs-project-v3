package com.prodigalgal.ircs.metadata.dispatch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.pipeline.PipelineCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RawVideoEnrichmentRepositoryTest {

    @Test
    void usesCurrentCategoryTables() {
        String sql = RawVideoEnrichmentRepository.SELECT_BY_ID;

        assertTrue(sql.contains("raw_category"));
        assertTrue(sql.contains("standard_category"));
        assertFalse(sql.contains("data_source_categories"));
        assertFalse(sql.contains("categories c"));
    }

    @Test
    void claimDueEnrichmentRecoveryCommandsMarksStaleReadyPendingOrFailedWithRowLease() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedJdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        UUID rawVideoId = UUID.randomUUID();
        PipelineCommand command = PipelineCommand.enrich(rawVideoId, "hash-v1");
        Instant staleBefore = Instant.parse("2026-06-17T08:00:00Z");
        when(namedJdbcTemplate.query(
                        argThat(sql -> sql.contains("for update skip locked")
                                && sql.contains("normalization_status = 'READY'")
                                && sql.contains("enrichment_status in ('PENDING', 'FAILED')")
                                && sql.contains("updated_at <= :staleBefore")
                                && sql.contains("enrichment_status = 'PENDING'")
                                && sql.contains("returning rv.id, rv.data_hash")),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of(command));
        RawVideoEnrichmentRepository repository = new RawVideoEnrichmentRepository(jdbcTemplate, namedJdbcTemplate);

        List<PipelineCommand> result = repository.claimDueEnrichmentRecoveryCommands(staleBefore, 50);

        assertEquals(List.of(command), result);
    }

    @Test
    void releaseRecoveryPublishFailuresMarksPendingRowsFailed() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedJdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        UUID rawVideoId = UUID.randomUUID();
        when(namedJdbcTemplate.update(
                        argThat(sql -> sql.contains("enrichment_status = 'FAILED'")
                                && sql.contains("where id in (:ids)")
                                && sql.contains("enrichment_status = 'PENDING'")),
                        any(java.util.Map.class)))
                .thenReturn(1);
        RawVideoEnrichmentRepository repository = new RawVideoEnrichmentRepository(jdbcTemplate, namedJdbcTemplate);

        assertEquals(1, repository.releaseRecoveryPublishFailures(List.of(rawVideoId)));
    }
}
