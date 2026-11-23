package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Exercises the authority recall query with PostgreSQL's real prepared-statement parameter typing. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class AuthorityCatalogProjectionQueryIntegrationTest {

    private CatalogTestDatabase database;
    private JdbcAggregationRepository repository;
    private UUID projectionId;

    @BeforeEach
    void setUp() throws Exception {
        database = CatalogTestDatabase.open();
        var jdbc = new JdbcTemplate(database.dataSource());
        UUID workId = UUID.randomUUID();
        projectionId = UUID.randomUUID();
        jdbc.update("""
                insert into catalog_works(id,media_type,tmdb_id,title,title_key,release_year,last_verified_at)
                values(?,'movie',603,'The Matrix','thematrix',1999,now())
                """, workId);
        jdbc.update("""
                insert into catalog_entries(id,work_id,title,created_at,updated_at)
                values(?,?,'The Matrix',now(),now())
                """, projectionId, workId);
        repository = JdbcAggregationRepository.forTest(
                new NamedParameterJdbcTemplate(database.dataSource()), AggregationTestFixtures.matchingStrategy());
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    @Test
    void recallsVerifiedProjectionWithNullOptionalIdsAndKeepsTitleKeysExact() {
        var byTitle = repository.findAuthorityCatalogProjections("movie", Set.of("thematrix"), null, null);
        var byId = repository.findAuthorityCatalogProjections("movie", Set.of("wrongtitle"), 603L, null);

        assertThat(byTitle).hasSize(1);
        assertThat(byTitle.getFirst().catalogEntryId()).isEqualTo(projectionId);
        assertThat(byId).hasSize(1);
        assertThat(byId.getFirst().catalogEntryId()).isEqualTo(projectionId);
        assertThat(new AuthorityCatalogBindingPolicy().decide(
                new SourceRecordAggregationRecord(UUID.randomUUID(), "Wrong Title", null, null, "1999",
                        null, null, null, null, null, null, null, "movie", "movie",
                        null, "603", null, null, "READY", "SUCCESS", "PENDING"),
                "movie", byId).status()).isEqualTo("WAITING_AUTHORITY");
    }
}
