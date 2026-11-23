package com.prodigalgal.ircs.task.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.task.domain.MediaRequestExistingVideo;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class MediaRequestContentLookupPostgresTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private MediaRequestContentLookupRepository lookup;
    private String schema;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "media_request_lookup_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(2);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        lookup = new MediaRequestContentLookupRepository(jdbc);
        ReflectionTestUtils.setField(lookup, "authorityFirstEnabled", true);

        jdbc.execute("""
                create table catalog_works(id uuid primary key, media_type text, tmdb_id bigint, title text,
                    title_key text, original_title_key text, release_year integer, identity_status text);
                create table catalog_seasons(id uuid primary key, work_id uuid, season_number integer);
                create table catalog_entries(id uuid primary key, work_id uuid not null, season_id uuid,
                    title text, alias_title text, year text, season integer, category_code text, updated_at timestamptz,
                    content_visibility text default 'PUBLIC', adult_restricted boolean default false, adult_assessment jsonb);
                create table catalog_magnet_links(catalog_entry_id uuid, status text);
                create function catalog_has_target_resources(uuid, integer, integer) returns boolean
                    language sql stable as $$ select exists (select 1 from catalog_magnet_links where catalog_entry_id=$1 and status='APPROVED') $$;
                """);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (admin != null && schema != null) admin.execute("drop schema " + schema + " cascade");
    }

    @Test
    void metadataOnlyAndAmbiguousAuthorityMatchesKeepTheRequestOpen() {
        UUID film = addMovie(11, 2025);
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).isEmpty();
        jdbc.update("insert into catalog_magnet_links values (?, 'APPROVED')", film);
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).contains(new MediaRequestExistingVideo(film, "UNIFIED"));

        addMovie(22, 2000);
        assertThat(lookup.findExistingVideo("Ａ： B", null)).isEmpty();
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).isPresent();
        jdbc.update("update catalog_entries set content_visibility='HIDDEN' where id=?", film);
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).isEmpty();
    }

    @Test
    void aUniquePlayableSourceFirstWorkCanFulfillWhenAuthorityHasNoMatch() {
        UUID drama = addVideo("Short series", "short-drama", 2026);
        assertThat(lookup.findExistingVideo("Short series", 2026)).isEmpty();
        jdbc.update("insert into catalog_magnet_links values (?, 'APPROVED')", drama);
        assertThat(lookup.findExistingVideo("Short series", 2026)).contains(new MediaRequestExistingVideo(drama, "UNIFIED"));
        addVideo("Short series", "short-drama", 2026);
        assertThat(lookup.findExistingVideo("Short series", 2026)).isEmpty();
    }

    private UUID addMovie(long tmdbId, int year) {
        UUID work = UUID.randomUUID();
        UUID film = UUID.randomUUID();
        jdbc.update("""
                insert into catalog_works(id,media_type,tmdb_id,title,title_key,release_year,identity_status)
                values (?,'movie',?,'A B','ab',?,'VERIFIED')
                """, work, tmdbId, year);
        jdbc.update("insert into catalog_entries(id,work_id,title,category_code,year) values (?,?,'A B','movie',?)",
                film, work, Integer.toString(year));
        return film;
    }

    private UUID addVideo(String title, String category, int year) {
        UUID id = UUID.randomUUID();
        UUID work = UUID.randomUUID();
        jdbc.update("insert into catalog_works(id,media_type,title,title_key,release_year,identity_status) values (?, 'tv', ?, ?, ?, 'PROVISIONAL')",
                work, title, title.toLowerCase().replaceAll("[^a-z0-9]", ""), year);
        jdbc.update("insert into catalog_entries(id,work_id,title,category_code,year) values (?,?,?,?,?)",
                id, work, title, category, Integer.toString(year));
        return id;
    }
}
