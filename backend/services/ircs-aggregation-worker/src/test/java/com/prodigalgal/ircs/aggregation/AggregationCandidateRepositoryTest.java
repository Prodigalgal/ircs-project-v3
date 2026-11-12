package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, mock(AggregationMatchingStrategy.class), mock(AggregationContextSearchClient.class), metadata);
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
        AggregationCandidateRepository repository = new AggregationCandidateRepository(
                jdbc, mock(AggregationMatchingStrategy.class), mock(AggregationContextSearchClient.class), metadata);
        RawVideoAggregationRecord raw = externalRaw("douban-batch");
        List<RawVideoAggregationRecord> input = new ArrayList<>();
        for (int index = 0; index < 201; index++) {
            input.add(raw);
        }
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), anyRowMapper())).thenReturn(List.of());
        when(metadata.populateUnifiedMetadata(anyList())).thenReturn(List.of());

        assertThat(repository.findContextUnifiedCandidates(input)).isEmpty();

        verify(jdbc, times(3)).query(anyString(), any(MapSqlParameterSource.class), anyRowMapper());
        verify(metadata).populateUnifiedMetadata(List.of());
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

    private static RowMapper<Object> anyRowMapper() {
        return any();
    }
}
