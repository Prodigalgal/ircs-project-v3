package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import com.prodigalgal.ircs.common.magnet.MagnetWorkTypes;
import com.prodigalgal.ircs.common.work.RuntimeWorkItem;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL-only locking/upsert coverage; every test owns and drops its isolated schema. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class MagnetSearchJobCoordinatorTest {

    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<MagnetExternalIdQuery> QUERIES = List.of(new MagnetExternalIdQuery("TITLE_YEAR", "Example 2026"));
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private String schema;
    private JdbcMagnetRepository repository;
    private MagnetSearchJobCoordinator coordinator;
    private UUID videoId;

    @BeforeEach
    void setUp() {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "magnet_coord_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(6);
        pool.setMinimumIdle(0);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        repository = new JdbcMagnetRepository(jdbc, new NamedParameterJdbcTemplate(pool), new ObjectMapper());
        coordinator = new MagnetSearchJobCoordinator(repository, jdbc, new DataSourceTransactionManager(pool));
        createSchema();
        videoId = UUID.randomUUID();
        jdbc.update("insert into unified_videos (id, title) values (?, 'Example')", videoId);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (admin != null && schema != null) admin.execute("drop schema if exists " + schema + " cascade");
    }

    @Test
    void concurrentEnqueueReusesOnePendingJob() throws Exception {
        List<Callable<MagnetSearchJobCoordinator.Enqueued>> requests = new ArrayList<>();
        for (int i = 0; i < 12; i++) requests.add(() -> coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES));
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(requests, 10, TimeUnit.SECONDS);
            UUID canonical = results.getFirst().get().job().id();
            int created = 0;
            for (var result : results) {
                var enqueued = result.get();
                assertEquals(canonical, enqueued.job().id());
                if (enqueued.created()) created++;
            }
            assertEquals(1, created);
        }
        assertEquals(1, count("magnet_search_jobs"));
    }

    @Test
    void firstDeliveredJobExecutesAndTerminalDuplicateReplayDoesNotAcquire() {
        var older = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        var delivered = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        var claim = coordinator.claim(delivered.id(), videoId, QUERIES, LEASE);
        assertTrue(claim.acquired());
        assertEquals(delivered.id(), claim.job().id());
        var skipped = repository.findSearchJob(older.id()).orElseThrow();
        assertEquals("SKIPPED", skipped.status());
        assertEquals("DUPLICATE_OF:" + delivered.id(), skipped.skippedReason());
        assertFalse(coordinator.claim(older.id(), videoId, QUERIES, LEASE).acquired());
        assertThrows(MagnetSearchJobCoordinator.MagnetJobBusyException.class,
                () -> coordinator.claim(delivered.id(), videoId, QUERIES, LEASE));
        coordinator.finish(lease(claim), List.of(), 0, 0, 0);
        assertFalse(coordinator.claim(delivered.id(), videoId, QUERIES, LEASE).acquired());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void enqueuePrefersExistingRunningJobToOlderPendingHistory() {
        repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        var running = repository.createRunningSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        assertEquals(running.id(), coordinator.enqueue(videoId, "ADMIN_MANUAL", QUERIES).job().id());
    }

    @Test
    void claimingPendingJobFencesOtherExpiredRunningJobsWithoutDeletingHistory() {
        var stale = repository.createRunningSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        jdbc.update("insert into magnet_provider_runs (id, job_id, status) values (?, ?, 'SUCCESS')", UUID.randomUUID(), stale.id());
        expire(stale.id());
        long oldGeneration = generation(stale.id());
        var delivered = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        coordinator.claim(delivered.id(), videoId, QUERIES, LEASE);
        assertEquals("SKIPPED", repository.findSearchJob(stale.id()).orElseThrow().status());
        assertTrue(generation(stale.id()) > oldGeneration);
        assertEquals(1, count("magnet_provider_runs"));
        assertFalse(coordinator.claim(stale.id(), videoId, QUERIES, LEASE).acquired());
    }

    @Test
    void concurrentClaimsAllowExactlyOneGeneration() throws Exception {
        var job = coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES).job();
        List<Callable<Boolean>> claims = List.of(() -> tryClaim(job.id()), () -> tryClaim(job.id()));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(claims, 10, TimeUnit.SECONDS);
            int acquired = 0;
            for (var result : results) if (result.get()) acquired++;
            assertEquals(1, acquired);
        }
    }

    @Test
    void duplicateCoalescingPreservesJobsWithExistingProviderHistory() {
        var resumable = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        jdbc.update("insert into magnet_provider_runs (id, job_id, status) values (?, ?, 'SUCCESS')",
                UUID.randomUUID(), resumable.id());
        var delivered = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        coordinator.claim(delivered.id(), videoId, QUERIES, LEASE);
        assertEquals("PENDING", repository.findSearchJob(resumable.id()).orElseThrow().status());
        assertEquals(1, count("magnet_provider_runs"));
    }

    @Test
    void busyDeliveryCannotCoalesceWhileAnotherWorkerOwnsExecution() {
        var job = coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES).job();
        var claim = coordinator.claim(job.id(), videoId, QUERIES, LEASE);
        var later = repository.createPendingSearchJob(videoId, "AUTO_SCHEDULED", QUERIES);
        assertThrows(MagnetSearchJobCoordinator.MagnetJobBusyException.class,
                () -> coordinator.claim(later.id(), videoId, QUERIES, LEASE));
        assertEquals("PENDING", repository.findSearchJob(later.id()).orElseThrow().status());
        assertEquals("RUNNING", repository.findSearchJob(claim.job().id()).orElseThrow().status());
    }

    @Test
    void duplicateCoalescingUsesBoundedBatch() {
        var job = coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES).job();
        jdbc.update("""
                insert into magnet_search_jobs (id, unified_video_id, trigger_type, status)
                select md5('duplicate-' || n)::uuid, ?, 'AUTO_SCHEDULED', 'PENDING'
                from generate_series(1, 1001) n
                """, videoId);
        coordinator.claim(job.id(), videoId, QUERIES, LEASE);
        assertEquals(1000, jdbc.queryForObject("select count(*) from magnet_search_jobs where status = 'SKIPPED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from magnet_search_jobs where status = 'PENDING'", Integer.class));
    }

    @Test
    void renewMaintainsGenerationAndCannotReviveExpiredExecution() {
        var claim = claimNew();
        long generation = generation(claim.job().id());
        jdbc.update("update magnet_search_jobs set updated_at = clock_timestamp() - interval '1 second' where id = ?", claim.job().id());
        Instant before = heartbeat(claim.job().id());
        assertTrue(coordinator.renew(lease(claim)));
        assertTrue(heartbeat(claim.job().id()).isAfter(before));
        assertEquals(generation, generation(claim.job().id()));
        expire(claim.job().id());
        assertFalse(coordinator.renew(lease(claim)));
        assertEquals(generation, generation(claim.job().id()));
    }

    @Test
    void expiredGenerationCannotPersistResultsAfterReclaim() {
        var first = claimNew();
        expire(first.job().id());
        var second = coordinator.claim(first.job().id(), videoId, QUERIES, LEASE);
        assertTrue(second.acquired());
        assertNotEquals(first.generation(), second.generation());
        assertThrows(MagnetSearchJobCoordinator.MagnetLeaseLostException.class,
                () -> coordinator.withinLease(lease(first), () -> jdbc.update("update unified_videos set title = 'stale' where id = ?", videoId)));
        assertEquals("Example", jdbc.queryForObject("select title from unified_videos where id = ?", String.class, videoId));
        assertFalse(coordinator.renew(lease(first)));
    }

    @Test
    void leaseExpiryDuringDatabaseWriteRollsBackEntireWrite() {
        var claim = claimNew();
        var brief = new MagnetSearchJobCoordinator.Lease(claim.job().id(), videoId, claim.generation(), Duration.ofMillis(200));
        assertThrows(MagnetSearchJobCoordinator.MagnetLeaseLostException.class,
                () -> coordinator.withinLease(brief, () -> {
                    jdbc.update("update unified_videos set title = 'expired' where id = ?", videoId);
                    jdbc.execute("select pg_sleep(0.3)");
                    return null;
                }));
        assertEquals("Example", jdbc.queryForObject("select title from unified_videos where id = ?", String.class, videoId));
        assertEquals("RUNNING", repository.findSearchJob(claim.job().id()).orElseThrow().status());
    }

    @Test
    void queueOwnershipLossBeforeCommitRollsBackAndPreventsFinish() {
        var claim = claimNew();
        AtomicBoolean queueOwned = new AtomicBoolean(true);
        MagnetWorkLeaseStore store = new MagnetWorkLeaseStore() {
            @Override
            public boolean renew(RuntimeWorkItem item, Duration visibility) { return queueOwned.get(); }
            @Override
            public boolean defer(RuntimeWorkItem item, Duration delay, String reason) { return false; }
        };
        Instant now = Instant.now();
        RuntimeWorkItem item = new RuntimeWorkItem(MagnetWorkTypes.SEARCH,
                MagnetWorkTypes.searchTaskId(claim.job().id()), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                "v1", "{}", "PROCESSING", 1, now, now, now, now.plusSeconds(60), "worker-test", null);
        try (var guard = new MagnetQueueClaimGuard(store, List.of(item), Duration.ofMinutes(1), () -> {})) {
            assertThrows(IllegalStateException.class, () -> coordinator.withinLease(lease(claim), () -> {
                jdbc.update("update unified_videos set title = 'queue-lost' where id = ?", videoId);
                queueOwned.set(false);
                guard.renewNow();
                return null;
            }));
            assertThrows(IllegalStateException.class, () -> coordinator.finish(lease(claim), List.of(), 0, 0, 0));
        }
        assertEquals("Example", jdbc.queryForObject("select title from unified_videos where id = ?", String.class, videoId));
        assertEquals("RUNNING", repository.findSearchJob(claim.job().id()).orElseThrow().status());
    }

    @Test
    void mismatchedVideoAndUnfencedFailureCannotMutateRunningJob() {
        var claim = claimNew();
        UUID otherVideo = UUID.randomUUID();
        jdbc.update("insert into unified_videos (id, title) values (?, 'Other')", otherVideo);
        assertThrows(IllegalArgumentException.class, () -> coordinator.claim(claim.job().id(), otherVideo, QUERIES, LEASE));
        var wrongLease = new MagnetSearchJobCoordinator.Lease(claim.job().id(), otherVideo, claim.generation(), LEASE);
        assertThrows(MagnetSearchJobCoordinator.MagnetLeaseLostException.class,
                () -> coordinator.finish(wrongLease, List.of(), 0, 0, 0));
        assertTrue(repository.markSearchJobFailed(claim.job().id(), "unfenced").isEmpty());
        assertEquals("RUNNING", repository.findSearchJob(claim.job().id()).orElseThrow().status());
    }

    @Test
    void retryReleasesExecutionAndKeepsSameJobAndProviderHistory() {
        var first = claimNew();
        jdbc.update("insert into magnet_provider_runs (id, job_id, status) values (?, ?, 'SUCCESS')", UUID.randomUUID(), first.job().id());
        assertEquals("PENDING", coordinator.retry(lease(first), "UPSTREAM_429").status());
        var second = coordinator.claim(first.job().id(), videoId, QUERIES, LEASE);
        assertTrue(second.acquired());
        assertEquals(first.job().id(), second.job().id());
        assertTrue(second.generation() > first.generation());
        assertEquals(1, count("magnet_provider_runs"));
        assertThrows(MagnetSearchJobCoordinator.MagnetLeaseLostException.class,
                () -> coordinator.finish(lease(first), List.of(), 0, 0, 0));
    }

    @Test
    void autoSchedulerExcludesOldPendingJobs() {
        var enqueued = coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES);
        jdbc.update("update magnet_search_jobs set created_at = clock_timestamp() - interval '30 days' where id = ?", enqueued.job().id());
        assertTrue(repository.findAutoSearchCandidates(10, Instant.now().minus(Duration.ofHours(12))).isEmpty());
    }

    @Test
    void atomicConcurrentLinkUpsertPreservesHumanStatusesAndApproval() throws Exception {
        var provider = provider(true);
        var candidate = candidate();
        List<Callable<UUID>> writes = new ArrayList<>();
        for (int i = 0; i < 8; i++) writes.add(() -> repository.upsertLink(videoId, provider, candidate, false).id());
        UUID linkId;
        try (var executor = Executors.newFixedThreadPool(4)) {
            var results = executor.invokeAll(writes, 10, TimeUnit.SECONDS);
            linkId = results.getFirst().get();
            for (var result : results) assertEquals(linkId, result.get());
        }
        assertEquals(1, count("magnet_links"));
        assertEquals("CANDIDATE", repository.findLinks(videoId).getFirst().status());
        assertEquals("APPROVED", repository.upsertLink(videoId, provider, candidate, true).status());
        assertEquals("APPROVED", repository.upsertLink(videoId, provider, candidate, false).status());
        for (String status : List.of("REJECTED", "HIDDEN")) {
            repository.updateLinkStatus(videoId, linkId, status);
            assertEquals(status, repository.upsertLink(videoId, provider, candidate, true).status());
        }
    }

    @Test
    void providerHealthIsRecordedWithoutMutatingConfigurationOrVersion() {
        var provider = provider(false);
        assertEquals("CANDIDATE", repository.upsertLink(videoId, provider, candidate(), true).status());
        repository.updateProviderHealth(provider.id(), "FAILED", "HTTP_429");
        var refreshed = repository.findProvider(provider.id()).orElseThrow();
        assertEquals("FAILED", refreshed.lastHealthStatus());
        assertEquals("HTTP_429", refreshed.lastErrorMessage());
        assertEquals(false, refreshed.autoApproveAllowed());
        assertEquals(0L, jdbc.queryForObject("select version from magnet_providers where id = ?", Long.class, provider.id()));
    }

    private boolean tryClaim(UUID jobId) {
        try { return coordinator.claim(jobId, videoId, QUERIES, LEASE).acquired(); }
        catch (MagnetSearchJobCoordinator.MagnetJobBusyException busy) { return false; }
    }

    @Test
    void providerExecutionReleasesConnectionsAndPersistsRealPostgresOutcome() {
        var source = provider(true);
        jdbc.update("update magnet_providers set supported_external_ids = '[\"TITLE_YEAR\"]'::jsonb where id = ?", source.id());
        jdbc.update("update unified_videos set \"year\" = '2026', category_code = 'movie' where id = ?", videoId);
        MagnetProviderSearchRunner runner = (provider, query, id) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            return new MagnetProviderSearchResult(null, 200, List.of(candidate()));
        };
        var executor = new MagnetSearchExecutor(repository, coordinator, runner, MagnetReadModelCache.disabled(),
                null, null, LEASE);
        var job = executor.searchNow(videoId, "ADMIN_MANUAL");
        assertEquals("SUCCESS", job.status());
        assertEquals(1, job.acceptedCount());
        assertEquals("APPROVED", job.links().getFirst().status());
        assertEquals(1, count("magnet_provider_runs"));
        assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
    }

    @Test
    void realProviderRetryPersistsPreviousAttemptAndDoesNotStrandRunningJob() {
        var source = provider(false);
        jdbc.update("update magnet_providers set supported_external_ids = '[\"TITLE\"]'::jsonb where id = ?", source.id());
        AtomicBoolean first = new AtomicBoolean(true);
        MagnetProviderSearchRunner runner = (provider, query, id) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (first.getAndSet(false)) throw new MagnetProviderRunnerException("GENERIC_MAGNET_TIMEOUT", null, null);
            return new MagnetProviderSearchResult(null, 200, List.of());
        };
        var executor = new MagnetSearchExecutor(repository, coordinator, runner, MagnetReadModelCache.disabled(),
                null, null, LEASE);
        var job = coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES).job();
        assertTrue(assertThrows(MagnetSearchExecutionException.class,
                () -> executor.execute(job.id(), videoId, "AUTO_SCHEDULED", true)).retryable());
        assertEquals("PENDING", repository.findSearchJob(job.id()).orElseThrow().status());
        assertEquals("SUCCESS", executor.execute(job.id(), videoId, "AUTO_SCHEDULED", true).status());
        assertEquals(2, count("magnet_provider_runs"));
    }

    @Test
    void terminalQueueReconciliationCannotOverrideNewGenerationOrLiveLease() {
        var claim = claimNew();
        Instant cutoff = Instant.now().minus(LEASE);
        assertFalse(coordinator.failAbandoned(claim.job().id(), videoId, claim.generation(), cutoff, "DLQ"));
        expire(claim.job().id());
        assertFalse(coordinator.failAbandoned(claim.job().id(), videoId, claim.generation() - 1, cutoff, "DLQ"));
        assertTrue(coordinator.failAbandoned(claim.job().id(), videoId, claim.generation(), cutoff, "DLQ"));
        assertEquals("FAILED", repository.findSearchJob(claim.job().id()).orElseThrow().status());
        assertFalse(coordinator.renew(lease(claim)));
    }

    private MagnetSearchJobCoordinator.Claim claimNew() {
        return coordinator.claim(coordinator.enqueue(videoId, "AUTO_SCHEDULED", QUERIES).job().id(), videoId, QUERIES, LEASE);
    }

    private MagnetSearchJobCoordinator.Lease lease(MagnetSearchJobCoordinator.Claim claim) {
        return new MagnetSearchJobCoordinator.Lease(claim.job().id(), videoId, claim.generation(), LEASE);
    }

    private void expire(UUID jobId) {
        jdbc.update("update magnet_search_jobs set updated_at = clock_timestamp() - interval '3 minutes' where id = ?", jobId);
    }

    private Instant heartbeat(UUID jobId) {
        return jdbc.queryForObject("select updated_at from magnet_search_jobs where id = ?", Timestamp.class, jobId).toInstant();
    }

    private long generation(UUID jobId) {
        return jdbc.queryForObject("select version from magnet_search_jobs where id = ?", Long.class, jobId);
    }

    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    private MagnetProviderSummary provider(boolean autoApprove) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into magnet_providers (id, code, name, provider_type, base_url, auto_approve_allowed)
                values (?, 'test', 'Test', 'TEST', 'https://example.invalid', ?)
                """, id, autoApprove);
        return repository.findProvider(id).orElseThrow();
    }

    private MagnetProviderCandidate candidate() {
        return new MagnetProviderCandidate("ABCDEF1234567890ABCDEF1234567890ABCDEF12",
                "magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12", "Example 2026",
                null, null, null, 1, 0, "WEB", "1080p", "TITLE_YEAR", "Example 2026", 95,
                null, List.of(), Map.of("titleSource", "CANDIDATE"));
    }

    private void createSchema() {
        jdbc.execute("""
                create table unified_videos (
                    id uuid primary key, title text, alias_title text, subtitle text, season integer, category_code text,
                    "year" text, imdb_id text, tmdb_id text, douban_id text, metadata_status text default 'SYNCED',
                    created_at timestamptz default now(), updated_at timestamptz default now());
                create table magnet_search_jobs (
                    id uuid primary key, unified_video_id uuid not null references unified_videos(id),
                    version bigint default 0, trigger_type text not null, status text not null,
                    provider_codes jsonb default '[]', external_id_plan jsonb default '[]',
                    started_at timestamptz, finished_at timestamptz,
                    total_candidates integer default 0, accepted_count integer default 0, rejected_count integer default 0,
                    skipped_reason text, error_message text, created_at timestamptz default now(), updated_at timestamptz default now());
                create table magnet_provider_runs (
                    id uuid primary key, job_id uuid not null references magnet_search_jobs(id), status text not null,
                    provider_id uuid, provider_code text, external_id_type text, external_id_value text,
                    request_url text, http_status integer, candidate_count integer default 0, accepted_count integer default 0,
                    duration_ms bigint, error_message text, version bigint default 0,
                    created_at timestamptz default now(), updated_at timestamptz default now());
                create table magnet_providers (
                    id uuid primary key, code text unique, name text, provider_type text, base_url text,
                    enabled boolean default true, priority integer default 1, risk_level text default 'HIGH',
                    supported_external_ids jsonb default '[]', min_delay_ms integer default 0, max_delay_ms integer default 0,
                    timeout_ms integer default 1000, result_limit integer default 20, auto_approve_allowed boolean,
                    content_policy text, last_health_check_at timestamptz, last_health_status text, last_error_message text,
                    version bigint default 0, created_at timestamptz default now(), updated_at timestamptz default now());
                create table magnet_links (
                    id uuid primary key, unified_video_id uuid references unified_videos(id), provider_id uuid references magnet_providers(id),
                    provider_code text, info_hash text, magnet_uri text, title text, size_bytes bigint, size_label text,
                    published_at timestamptz, seeders integer, leechers integer, quality text, resolution text,
                    matched_external_id_type text, matched_external_id_value text, match_score integer, status text,
                    source_url text, tags jsonb, provider_evidence jsonb, first_seen_at timestamptz, last_seen_at timestamptz,
                    version bigint default 0, created_at timestamptz default now(), updated_at timestamptz default now(),
                    unique (unified_video_id, info_hash));
                """);
    }
}
