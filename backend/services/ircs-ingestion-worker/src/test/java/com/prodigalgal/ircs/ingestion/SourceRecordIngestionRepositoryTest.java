package com.prodigalgal.ircs.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionEpisodeDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionPlaylistDTO;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class SourceRecordIngestionRepositoryTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SourceRecordIngestionRepository repository = new SourceRecordIngestionRepository(jdbcTemplate, mapper);

    @Test
    void upsertSourceRecordCreatesCoverImageAndLinksSourceRecord() {
        UUID sourceDomainId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID coverImageId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID sourceRecordId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class), eq(UUID.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("insert into source_origins")) {
                        return sourceDomainId;
                    }
                    if (sql.contains("insert into media_image_sources")) {
                        return coverImageId;
                    }
                    if (sql.contains("insert into source_records")) {
                        return sourceRecordId;
                    }
                    throw new AssertionError("Unexpected SQL: " + sql);
                });

        UUID result = repository.upsertSourceRecord(
                video("https://pic.example.invalid/upload/vod/cover.jpg?token=abc#main"),
                null);

        assertThat(result).isEqualTo(sourceRecordId);
        CapturedSql captured = captureQueryForObjectCalls();
        MapSqlParameterSource sourceDomainParams = captured.paramsFor("insert into source_origins");
        assertThat(sourceDomainParams.getValue("domainValue")).isEqualTo("https://pic.example.invalid");
        MapSqlParameterSource coverParams = captured.paramsFor("insert into media_image_sources");
        assertThat(coverParams.getValue("originalUrl")).isEqualTo("/upload/vod/cover.jpg?token=abc#main");
        assertThat(coverParams.getValue("sourceDomainId")).isEqualTo(sourceDomainId);
        MapSqlParameterSource rawParams = captured.paramsFor("insert into source_records");
        assertThat(rawParams.getValue("coverImageId")).isEqualTo(coverImageId);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("status = 'UNPROCESSED'"),
                org.mockito.ArgumentMatchers.any(SqlParameterSource.class));
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.contains("insert into collection_source_origins"),
                org.mockito.ArgumentMatchers.any(SqlParameterSource.class));
    }

    @Test
    void upsertSourceRecordLeavesCoverEmptyWhenSourceDoesNotProvideUrl() {
        UUID sourceRecordId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(sourceRecordId);

        UUID result = repository.upsertSourceRecord(video(null), null);

        assertThat(result).isEqualTo(sourceRecordId);
        CapturedSql captured = captureQueryForObjectCalls();
        assertThat(captured.sql()).hasSize(1);
        MapSqlParameterSource rawParams = captured.paramsFor("insert into source_records");
        assertThat(rawParams.getValue("coverImageId")).isNull();
        verify(jdbcTemplate, never()).update(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class));
    }

    @Test
    void normalizesAuthorityIdsAtPersistenceBoundaryWithoutChangingRawEvidence() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(UUID.randomUUID());
        IngestionVideoDTO original = video(null);
        IngestionVideoDTO invalid = withIds(original, "000", "null", "tt0000000", "暂无");
        repository.upsertSourceRecord(invalid, null);
        MapSqlParameterSource params = captureQueryForObjectCalls().paramsFor("insert into source_records");
        for (String field : List.of("doubanId", "tmdbId", "imdbId", "rottenTomatoesId")) {
            assertThat(params.getValue(field)).as(field).isNull();
        }
        assertThat(params.getValue("sourceVid")).isEqualTo("source-vid");
        assertThat(params.getValue("rawMetadata")).isEqualTo(original.rawMetadata());
    }

    @Test
    void canonicalizesRealIdsInActualInsertParameters() {
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(UUID.randomUUID());
        repository.upsertSourceRecord(withIds(video(null), "00123", "456", " TT1234567 ", "Example_Title"), null);
        MapSqlParameterSource params = captureQueryForObjectCalls().paramsFor("insert into source_records");
        assertThat(params.getValue("doubanId")).isEqualTo("123");
        assertThat(params.getValue("tmdbId")).isEqualTo("456");
        assertThat(params.getValue("imdbId")).isEqualTo("tt1234567");
        assertThat(params.getValue("rottenTomatoesId")).isEqualTo("example_title");
    }

    @Test
    void playlistSyncLinksItsDataSourceToEachObservedDomainIdempotently() {
        UUID videoId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID sourceDomainId = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID preassignedDomainId = UUID.fromString("00000000-0000-0000-0000-000000000303");
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(sourceDomainId);

        var playlists = List.of(new IngestionPlaylistDTO("Main", List.of(
                new IngestionEpisodeDTO("EP1", "https://cdn.example.invalid/1", null),
                new IngestionEpisodeDTO("EP2", "/2.m3u8", preassignedDomainId))));
        repository.replacePlaylists(videoId, new SourceRecordIngestionRepository.PlaybackSyncState(
                "data-hash", "PENDING", UUID.randomUUID(), null, mapper.writeValueAsString(playlists)));

        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("insert into collection_source_origins")
                        && sql.contains("on conflict (collection_source_id, source_origin_id) do nothing")),
                paramsCaptor.capture());
        SqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("domainIds")).isEqualTo(List.of(sourceDomainId, preassignedDomainId));
        assertThat(params.getValue("sourceRecordId")).isEqualTo(videoId);
    }

    private IngestionVideoDTO withIds(IngestionVideoDTO video, String douban, String tmdb, String imdb, String rt) {
        return new IngestionVideoDTO(video.sourceVid(), video.sourceHash(), video.dataHash(), video.title(),
                video.aliasTitle(), video.description(), video.coverImageUrl(), video.year(), video.area(),
                video.language(), video.remarks(), video.score(), video.publishedAt(), video.totalEpisodes(),
                video.duration(), douban, tmdb, imdb, rt, video.rawMetadata(), "PENDING", video.dataSourceId(),
                video.playlists(), 0);
    }

    private CapturedSql captureQueryForObjectCalls() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
                .queryForObject(sqlCaptor.capture(), paramsCaptor.capture(), eq(UUID.class));
        return new CapturedSql(sqlCaptor.getAllValues(), paramsCaptor.getAllValues());
    }

    private IngestionVideoDTO video(String coverImageUrl) {
        return new IngestionVideoDTO(
                "source-vid",
                "source-hash",
                "data-hash",
                "Codex Source",
                null,
                "description",
                coverImageUrl,
                "2026",
                "CN",
                "zh-CN",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "{\"coverImageUrl\":\"" + (coverImageUrl == null ? "" : coverImageUrl) + "\"}",
                "PENDING",
                videoDataSourceId(),
                List.of(),
                0);
    }

    private UUID videoDataSourceId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private record CapturedSql(List<String> sql, List<SqlParameterSource> params) {

        MapSqlParameterSource paramsFor(String marker) {
            for (int i = 0; i < sql.size(); i++) {
                if (sql.get(i).contains(marker)) {
                    return (MapSqlParameterSource) params.get(i);
                }
            }
            throw new AssertionError("SQL marker not found: " + marker);
        }
    }
}
