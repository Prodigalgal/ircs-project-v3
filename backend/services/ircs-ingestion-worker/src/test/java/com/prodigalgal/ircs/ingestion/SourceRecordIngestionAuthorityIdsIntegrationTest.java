package com.prodigalgal.ircs.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class SourceRecordIngestionAuthorityIdsIntegrationTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private SourceRecordIngestionRepository repository;
    private String schema;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        admin = new JdbcTemplate(source);
        schema = "ingestion_ids_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        var scoped = new DriverManagerDataSource(source.getUrl(), source.getUsername(), source.getPassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        scoped.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(scoped);
        repository = new SourceRecordIngestionRepository(new NamedParameterJdbcTemplate(scoped));
        jdbc.execute("""
                create table source_records(
                    id uuid primary key,created_at timestamptz,updated_at timestamptz,version bigint,
                    source_vid text,source_hash text unique,data_hash text,title text,alias_title text,
                    description text,media_image_source_id uuid,year text,area text,raw_language_str text,remarks text,
                    score numeric,published_at date,total_episodes text,duration text,
                    douban_id text,tmdb_id text,imdb_id text,rotten_tomatoes_id text,locked_fields jsonb,
                    enrichment_status text,enrichment_retry_count integer,normalization_status text,
                    normalization_retry_count integer,aggregation_status text,raw_metadata jsonb,collection_source_id uuid);
                """);
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            // This randomly named schema is created and owned exclusively by this test.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void protectsEachLockedIdForArrayAndObjectLocksAndClearsUnlockedStaleIds() {
        for (String locks : List.of("[\"doubanId\",\"tmdbId\",\"imdbId\",\"rottenTomatoesId\"]",
                "{\"doubanId\":true,\"tmdbId\":true,\"imdbId\":true,\"rottenTomatoesId\":true}")) {
            String sourceHash = UUID.randomUUID().toString();
            UUID id = repository.upsertSourceRecord(video(sourceHash, "123", "456", "tt1234567", "real_slug"), null);
            jdbc.update("update source_records set locked_fields=cast(? as jsonb) where id=?", locks, id);
            assertThat(repository.upsertSourceRecord(video(sourceHash, "0", "null", "tt0000000", "unknown"), id)).isEqualTo(id);
            assertThat(jdbc.queryForMap("select douban_id,tmdb_id,imdb_id,rotten_tomatoes_id from source_records where id=?", id))
                    .containsEntry("douban_id", "123").containsEntry("tmdb_id", "456")
                    .containsEntry("imdb_id", "tt1234567").containsEntry("rotten_tomatoes_id", "real_slug");
            jdbc.update("update source_records set locked_fields='[]'::jsonb where id=?", id);
            repository.upsertSourceRecord(video(sourceHash, "0", "null", "tt0000000", "unknown"), id);
            assertThat(jdbc.queryForMap("select douban_id,tmdb_id,imdb_id,rotten_tomatoes_id from source_records where id=?", id))
                    .allSatisfy((key, value) -> assertThat(value).as(key).isNull());
            assertThat(jdbc.queryForObject("select raw_metadata->'doubanId' from source_records where id=?", String.class, id)).isEqualTo("0");
        }
    }

    @Test
    void oneLockDoesNotPreventOtherAuthorityFieldsFromRefreshing() {
        String hash = UUID.randomUUID().toString();
        UUID id = repository.upsertSourceRecord(video(hash, "123", "456", "tt1234567", "real_slug"), null);
        jdbc.update("update source_records set locked_fields='[\"doubanId\"]'::jsonb where id=?", id);
        repository.upsertSourceRecord(video(hash, "789", "987", "tt7654321", "new_slug"), id);
        assertThat(jdbc.queryForMap("select douban_id,tmdb_id,imdb_id,rotten_tomatoes_id from source_records where id=?", id))
                .containsEntry("douban_id", "123").containsEntry("tmdb_id", "987")
                .containsEntry("imdb_id", "tt7654321").containsEntry("rotten_tomatoes_id", "new_slug");
    }

    private IngestionVideoDTO video(String hash, String douban, String tmdb, String imdb, String rt) {
        return new IngestionVideoDTO("0", hash, UUID.randomUUID().toString(), "原题", null, null, null,
                null, null, null, null, null, null, null, null, douban, tmdb, imdb, rt,
                "{\"doubanId\":0,\"rawTypeId\":0}", "PENDING", UUID.randomUUID(), List.of(), 0);
    }
}
