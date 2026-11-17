package com.prodigalgal.ircs.task.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class CatalogDiscoveryRepositoryIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private String schema;
    private CatalogDiscoveryRepository requests;
    private JdbcCollectionTaskRepository tasks;
    private UUID workId;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "catalog_discovery_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(6);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        requests = new CatalogDiscoveryRepository(new NamedParameterJdbcTemplate(pool));
        tasks = new JdbcCollectionTaskRepository(jdbc);
        createSchema();
        workId = UUID.randomUUID();
        jdbc.update("insert into unified_videos(id, season) values (?, 2)", workId);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (admin != null && schema != null) admin.execute("drop schema if exists " + schema + " cascade");
    }

    @Test
    void simultaneousClaimsOwnEachRequestOnlyOnce() throws Exception {
        insertRequest();
        List<Callable<List<CatalogDiscoveryCandidate>>> calls = new ArrayList<>();
        for (int i = 0; i < 12; i++) calls.add(() -> requests.claimDue(1, LEASE));
        int claimed = 0;
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(calls, 10, TimeUnit.SECONDS)) claimed += result.get().size();
        }
        assertThat(claimed).isEqualTo(1);
        assertThat(jdbc.queryForObject("select attempts from catalog_discovery_requests", Integer.class)).isEqualTo(1);
    }

    @Test
    void expiredClaimIsRecoverableButOldOwnerCannotRenewCompleteOrDeferIt() {
        insertRequest();
        CatalogDiscoveryCandidate expired = requests.claimDue(1, LEASE).getFirst();
        jdbc.update("update catalog_discovery_requests set lease_until = now() - interval '1 second'");
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();

        assertThat(current.leaseToken()).isNotEqualTo(expired.leaseToken());
        assertThat(current.attempts()).isEqualTo(2);
        assertThat(requests.renew(expired, LEASE)).isFalse();
        assertThat(requests.markSubmitted(expired)).isFalse();
        assertThat(requests.retry(expired, Duration.ofHours(6), "STALE")).isFalse();
        assertThat(requests.markSubmitted(current)).isTrue();
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
    }

    @Test
    void retryRetainsRequestAndCannotBeReclaimedBeforeItsBackoff() {
        insertRequest();
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();

        assertThat(requests.retry(current, Duration.ofMinutes(5), "NO_ELIGIBLE_SOURCE")).isTrue();
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        assertThat(jdbc.queryForObject("select last_error from catalog_discovery_requests", String.class))
                .isEqualTo("NO_ELIGIBLE_SOURCE");
        jdbc.update("update catalog_discovery_requests set next_attempt_at = now() - interval '1 second'");
        assertThat(requests.claimDue(1, LEASE)).hasSize(1);
    }

    @Test
    void postAcceptancePublishFailureRecoversButPausedAndCompletedTasksAreNotBypassed() {
        insertRequest();
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();
        requests.markSubmitted(current);
        jdbc.update("update catalog_discovery_requests set submitted_at = now() - interval '6 minutes'");
        UUID source = insertSource();
        UUID task = insertTask(source, "PAUSED", true,
                "{\"ircsCatalogDiscovery\":{\"requestKey\":\"" + current.requestKey() + "\"}}");
        assertThat(requests.isPausedByOperator(current)).isTrue();
        assertThat(requests.recoverFailedSubmissions(5)).isZero();
        jdbc.update("update collection_tasks set status = 'COMPLETED' where id = ?", task);
        assertThat(requests.isPausedByOperator(current)).isFalse();
        assertThat(requests.recoverFailedSubmissions(5)).isZero();
        jdbc.update("update collection_tasks set status = 'FAILED' where id = ?", task);
        assertThat(requests.recoverFailedSubmissions(5)).isEqualTo(1);
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        assertThat(jdbc.queryForObject("select last_error from catalog_discovery_requests", String.class))
                .isEqualTo("DISPATCH_FAILED_AFTER_ACCEPT");
    }

    @Test
    void bindingAndMirrorCountsCannotPretendToFulfillAnEpisodeRequest() {
        CatalogDiscoveryCandidate target = new CatalogDiscoveryCandidate(workId, 2, 12, "Example", 1, UUID.randomUUID());
        UUID rawId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        UUID episodeId = UUID.randomUUID();
        jdbc.update("insert into raw_videos(id, season) values (?, 2)", rawId);
        jdbc.update("insert into raw_video_unified_video values (?, ?)", rawId, workId);
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("insert into playlists values (?, ?)", playlistId, rawId);
        jdbc.update("insert into episodes(id, playlist_id, name, url) values (?, ?, '第12集', '')", episodeId, playlistId);
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update episodes set name = 'HD', url = 'https://source.example/video.m3u8'");
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update episodes set name = '第12集'");
        assertThat(requests.hasRequestedResources(target)).isTrue();
        jdbc.update("update episodes set name = 'S01E12'");
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update episodes set name = 'S02E12'");
        jdbc.update("update raw_videos set season = null");
        assertThat(requests.hasRequestedResources(target)).isTrue();
        jdbc.update("update raw_videos set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb");
        assertThat(requests.hasRequestedResources(target)).isFalse();
    }

    @Test
    void publicCatalogDispatchDoesNotClaimAdultOrPrivateWorks() {
        insertRequest();
        jdbc.update("update unified_videos set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        jdbc.update("update unified_videos set adult_assessment = null, content_visibility = 'PRIVATE'");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
    }

    @Test
    void catalogSourcesRespectOperatorOptOutAdultIsolationBusySourcesAndFailureCooldown() {
        UUID source = insertSource();
        UUID configuredTask = insertTask(source, "IDLE", true, "{}");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 100)).extracting(DataSourceSeedCandidate::id)
                .containsExactly(source);
        jdbc.update("update collection_tasks set enabled = false where id = ?", configuredTask);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).isEmpty();
        jdbc.update("update collection_tasks set enabled = true where id = ?", configuredTask);
        jdbc.update("update data_sources set adult_restricted = true");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).isEmpty();
        jdbc.update("update data_sources set adult_restricted = false");
        UUID busy = insertTask(source, "RUNNING", true, "{}");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).isEmpty();
        jdbc.update("update collection_tasks set status = 'FAILED' where id = ?", busy);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).isEmpty();
        jdbc.update("update collection_tasks set updated_at = now() - interval '31 minutes' where id = ?", busy);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).hasSize(1);
        jdbc.update("update collection_tasks set status = 'RUNNING', "
                + "headers = '{\"ircsCatalogDiscovery\":{\"requestKey\":\"request-key\"}}'::jsonb where id = ?", busy);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3)).hasSize(1);
        assertThat(tasks.findCatalogDiscoveryDataSources("other-work", 3)).isEmpty();
    }

    private void insertRequest() {
        jdbc.update("insert into catalog_discovery_requests(unified_video_id, target_season, target_episode, keyword) "
                + "values (?, 2, 12, 'Example')", workId);
    }

    private UUID insertSource() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into data_sources values (?, 'Source', 'https://source.example', '/api.php', '/api.php', "
                + "'{\"title\":\"vod_name\"}'::jsonb, false)", id);
        return id;
    }

    private UUID insertTask(UUID source, String status, boolean enabled, String headers) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into collection_tasks(id, data_source_id, status, enabled, headers, updated_at) "
                + "values (?, ?, ?, ?, cast(? as jsonb), now())", id, source, status, enabled, headers);
        return id;
    }

    private void createSchema() throws Exception {
        jdbc.execute("create table unified_videos(id uuid primary key, season integer, adult_restricted boolean default false, "
                + "adult_assessment jsonb, content_visibility text default 'PUBLIC')");
        jdbc.execute("create table raw_videos(id uuid primary key, season integer, data_source_id uuid)");
        jdbc.execute("create table raw_video_unified_video(raw_video_id uuid, unified_video_id uuid)");
        jdbc.execute("create table playlists(id uuid primary key, video_id uuid)");
        jdbc.execute("create table episodes(id uuid primary key, playlist_id uuid, name text, url text, source_domain_id uuid)");
        jdbc.execute("create table data_sources(id uuid primary key, name text, base_url text, list_path text, detail_path text, "
                + "field_mapping jsonb, adult_restricted boolean default false)");
        jdbc.execute("create table collection_tasks(id uuid primary key, data_source_id uuid, status text, enabled boolean, "
                + "headers jsonb, updated_at timestamp, stat_success bigint)");
        String migration = Files.readString(migrationPath());
        for (String pattern : List.of("CREATE TABLE catalog_discovery_requests\\s*\\([\\s\\S]*?\\n\\);",
                "CREATE FUNCTION catalog_episode_number[\\s\\S]*?\\$\\$;",
                "CREATE FUNCTION catalog_resource_inventory[\\s\\S]*?\\$\\$;")) {
            var matcher = Pattern.compile(pattern).matcher(migration);
            if (!matcher.find()) throw new IllegalStateException("Catalog migration contract was not found");
            jdbc.execute(matcher.group());
        }
    }

    private static Path migrationPath() {
        Path relative = Path.of("platform/ircs-migrator/src/main/resources/db/changelog/2026/11/16-02-global-rankings-and-discovery.sql");
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            if (Files.isRegularFile(root.resolve(relative))) return root.resolve(relative);
            if (Files.isRegularFile(root.resolve("backend").resolve(relative))) return root.resolve("backend").resolve(relative);
        }
        throw new IllegalStateException("Cannot locate catalog migration");
    }
}
