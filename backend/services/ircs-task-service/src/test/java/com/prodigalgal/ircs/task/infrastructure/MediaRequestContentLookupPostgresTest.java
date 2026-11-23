package com.prodigalgal.ircs.task.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.task.domain.MediaRequestExistingVideo;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
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
                create table unified_videos(id uuid primary key, title text, alias_title text, year text, season integer,
                    category_code text, updated_at timestamptz, content_visibility text default 'PUBLIC',
                    adult_restricted boolean default false, adult_assessment jsonb);
                create table raw_videos(id uuid primary key, season integer, data_source_id uuid);
                create table data_sources(id uuid primary key, adult_restricted boolean default false);
                create table raw_video_unified_video(raw_video_id uuid, unified_video_id uuid);
                create table playlists(id uuid primary key, video_id uuid);
                create table episodes(id uuid primary key, playlist_id uuid, name text, url text, source_domain_id uuid);
                create table magnet_links(unified_video_id uuid, status text);
                """);
        var episodeNumber = Pattern.compile("CREATE FUNCTION catalog_episode_number[\\s\\S]*?\\$\\$;")
                .matcher(Files.readString(migration("16-02-global-rankings-and-discovery.sql")));
        assertThat(episodeNumber.find()).isTrue();
        jdbc.execute(episodeNumber.group());
        jdbc.execute(Files.readString(migration("22-01-catalog-target-resources.sql")));
        jdbc.execute(Files.readString(migration("23-01-authority-first-catalog.sql")));
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
        jdbc.update("insert into magnet_links values (?, 'APPROVED')", film);
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).contains(new MediaRequestExistingVideo(film, "UNIFIED"));

        addMovie(22, 2000);
        assertThat(lookup.findExistingVideo("Ａ： B", null)).isEmpty();
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).isPresent();
        jdbc.update("update unified_videos set content_visibility='HIDDEN' where id=?", film);
        assertThat(lookup.findExistingVideo("Ａ： B", 2025)).isEmpty();
    }

    @Test
    void aUniquePlayableSourceFirstWorkCanFulfillWhenAuthorityHasNoMatch() {
        UUID drama = addVideo("Short series", "short-drama", 2026);
        assertThat(lookup.findExistingVideo("Short series", 2026)).isEmpty();
        jdbc.update("insert into magnet_links values (?, 'APPROVED')", drama);
        assertThat(lookup.findExistingVideo("Short series", 2026)).contains(new MediaRequestExistingVideo(drama, "UNIFIED"));
        addVideo("Short series", "short-drama", 2026);
        assertThat(lookup.findExistingVideo("Short series", 2026)).isEmpty();
    }

    private UUID addMovie(long tmdbId, int year) {
        UUID work = UUID.randomUUID();
        UUID film = addVideo("A B", "movie", year);
        jdbc.update("""
                insert into authority_catalog_works(id,media_type,tmdb_id,title,title_key,release_year)
                values (?,'movie',?,'A B','ab',?)
                """, work, tmdbId, year);
        jdbc.update("""
                insert into authority_catalog_projections(work_id,media_type,season_number,unified_video_id)
                values (?,'movie',-1,?)
                """, work, film);
        return film;
    }

    private UUID addVideo(String title, String category, int year) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into unified_videos(id,title,category_code,year) values (?,?,?,?)",
                id, title, category, Integer.toString(year));
        return id;
    }

    private static Path migration(String fileName) {
        Path relative = Path.of("backend/platform/ircs-migrator/src/main/resources/db/changelog/2026/11", fileName);
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(relative))) return root.resolve(relative);
        }
        throw new IllegalStateException("Catalog migration missing: " + fileName);
    }
}
