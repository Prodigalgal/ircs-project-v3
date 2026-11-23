package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Exercises the authority recall query with PostgreSQL's real prepared-statement parameter typing. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class AuthorityCatalogProjectionQueryIntegrationTest {

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private String schema;
    private JdbcAggregationRepository repository;
    private UUID workId;
    private UUID projectionId;

    @BeforeEach
    void setUp() {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "authority_projection_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        jdbc.execute("""
                create table authority_catalog_works(
                    id uuid primary key, media_type varchar(8) not null, tmdb_id bigint not null,
                    imdb_id varchar(32), title_key varchar(500) not null, original_title_key varchar(500),
                    release_year smallint, released_on date, regular_season_count integer not null default 0);
                create table authority_catalog_projections(
                    work_id uuid not null, season_number integer not null, unified_video_id uuid not null);
                create table authority_catalog_seasons(
                    work_id uuid not null, season_number integer not null, released_on date);
                create table unified_videos(
                    id uuid primary key, content_visibility varchar(32) not null, adult_restricted boolean);
                """);
        workId = UUID.randomUUID();
        projectionId = UUID.randomUUID();
        jdbc.update("insert into authority_catalog_works(id,media_type,tmdb_id,title_key,release_year) values (?,?,?,?,?)",
                workId, "movie", 603L, "thematrix", 1999);
        jdbc.update("insert into authority_catalog_projections(work_id,season_number,unified_video_id) values (?,?,?)",
                workId, -1, projectionId);
        jdbc.update("insert into unified_videos(id,content_visibility,adult_restricted) values (?,?,?)",
                projectionId, "PUBLIC", false);
        repository = JdbcAggregationRepository.forTest(
                new NamedParameterJdbcTemplate(pool), AggregationTestFixtures.matchingStrategy());
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
        if (admin != null && schema != null) {
            admin.execute("drop schema if exists " + schema + " cascade");
        }
    }

    @Test
    void recallsVerifiedProjectionWithNullOptionalIdsAndKeepsTitleKeysExact() {
        var byTitle = repository.findAuthorityCatalogProjections("movie", Set.of("thematrix"), null, null);
        var byId = repository.findAuthorityCatalogProjections("movie", Set.of("wrongtitle"), 603L, null);

        assertThat(byTitle).hasSize(1);
        assertThat(byTitle.getFirst().unifiedVideoId()).isEqualTo(projectionId);
        assertThat(byId).hasSize(1);
        assertThat(byId.getFirst().unifiedVideoId()).isEqualTo(projectionId);
        assertThat(AuthorityCatalogBindingPolicy.decide(
                new RawVideoAggregationRecord(UUID.randomUUID(), "Wrong Title", null, null, "1999",
                        null, null, null, null, null, null, null, "movie", "movie",
                        null, "603", null, null, "READY", "SUCCESS", "PENDING"),
                "movie", byId).status()).isEqualTo("WAITING_AUTHORITY");
    }
}
