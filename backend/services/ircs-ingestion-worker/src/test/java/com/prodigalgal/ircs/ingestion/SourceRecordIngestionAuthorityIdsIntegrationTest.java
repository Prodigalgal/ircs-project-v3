package com.prodigalgal.ircs.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.ingestion.IngestionVideoDTO;
import com.prodigalgal.ircs.contracts.ingestion.IngestionItem;
import com.prodigalgal.ircs.contracts.ingestion.PlaylistSyncMessage;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class SourceRecordIngestionAuthorityIdsIntegrationTest {
    private CatalogTestDatabase database;
    private JdbcTemplate jdbc;
    private SourceRecordIngestionRepository repository;
    private TransactionTemplate transactions;
    private UUID collectionSourceId;

    @BeforeEach
    void setUp() throws Exception {
        database = CatalogTestDatabase.open();
        jdbc = new JdbcTemplate(database.dataSource());
        repository = new SourceRecordIngestionRepository(new NamedParameterJdbcTemplate(database.dataSource()),
                JsonMapper.builder().findAndAddModules().build());
        transactions = new TransactionTemplate(new DataSourceTransactionManager(database.dataSource()));
        collectionSourceId = UUID.randomUUID();
        jdbc.update("""
                insert into collection_sources(id,created_at,updated_at,name,base_url,list_path,detail_path)
                values(?,now(),now(),'Fixture','https://example.invalid','/list','/detail')
                """, collectionSourceId);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
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

    @Test
    void aLateObservationDoesNotReplaceTheActiveVersionAndAReplayIsIdempotent() {
        var service = new SourceRecordIngestionTransactions(repository);
        String identity = UUID.randomUUID().toString();
        var first = video(identity, "12", "34", "tt1234567", "slug");
        var next = video(identity, "12", "34", "tt1234567", "slug");
        var late = video(identity, "12", "34", "tt1234567", "slug");
        Instant now = Instant.parse("2026-11-23T00:00:00Z");
        var initial = transactions.execute(status -> service.persist(new IngestionItem(first, false, now)));
        UUID id = initial.id();
        assertThat(transactions.execute(status -> service.persist(new IngestionItem(next, false, now.plusSeconds(2)))).id())
                .isEqualTo(id);
        assertThat(transactions.execute(status -> service.persist(new IngestionItem(late, false, now.plusSeconds(1)))).skipped())
                .isTrue();
        assertThat(transactions.execute(status -> service.persist(new IngestionItem(next, false, now.plusSeconds(3)))).skipped())
                .isTrue();
        assertThat(jdbc.queryForObject("select data_hash from source_records where id=?", String.class, id))
                .isEqualTo(next.dataHash());
        assertThat(jdbc.queryForObject("select count(*) from source_record_versions where source_record_id=?",
                Integer.class,id)).isEqualTo(3);
        var stale = transactions.execute(status -> service.syncPlaylists(new PlaylistSyncMessage(id, identity,
                late.dataHash(), List.of())));
        assertThat(stale.result().status()).isEqualTo("STALE");
    }

    private IngestionVideoDTO video(String hash, String douban, String tmdb, String imdb, String rt) {
        return new IngestionVideoDTO(hash, hash, UUID.randomUUID().toString(), "原题", null, null, null,
                null, null, null, null, null, null, null, null, douban, tmdb, imdb, rt,
                "{\"doubanId\":0,\"rawTypeId\":0}", "PENDING", collectionSourceId, List.of(), 0);
    }
}
