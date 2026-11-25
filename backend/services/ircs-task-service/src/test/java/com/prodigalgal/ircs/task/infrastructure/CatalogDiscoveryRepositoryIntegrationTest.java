package com.prodigalgal.ircs.task.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.task.domain.CatalogDiscoveryCandidate;
import com.prodigalgal.ircs.task.domain.ValidatedCreateTask;
import com.prodigalgal.ircs.migrator.CatalogTestDatabase;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class CatalogDiscoveryRepositoryIntegrationTest {

    private static final Duration LEASE = Duration.ofMinutes(10);
    private CatalogTestDatabase database;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private CatalogDiscoveryRepository requests;
    private JdbcCollectionTaskRepository tasks;
    private UUID workId;

    @BeforeEach
    void setUp() throws Exception {
        database = CatalogTestDatabase.open();
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        pool = new HikariDataSource();
        pool.setJdbcUrl(database.url());
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(6);
        pool.setMinimumIdle(0);
        jdbc = new JdbcTemplate(pool);
        requests = new CatalogDiscoveryRepository(new NamedParameterJdbcTemplate(pool));
        tasks = new JdbcCollectionTaskRepository(jdbc);
        workId = UUID.randomUUID();
        jdbc.update("insert into catalog_entries(id,season,title,created_at,updated_at) values(?,2,'Fixture',now(),now())", workId);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) pool.close();
        if (database != null) database.close();
    }

    @Test
    void persistsAndQueuesAPlanForAClaimedCatalogDiscovery() {
        insertRequest();
        CatalogDiscoveryCandidate candidate = requests.claimDue(1, LEASE).getFirst();
        UUID source = insertSource();

        UUID plan = tasks.create(new ValidatedCreateTask(
                "Catalog discovery fixture", source, "BY_PAGE", true, null, "UTC", 1, 1,
                null, null, "Fixture", "RANDOM", 0, 1000, 3000, 10000, 1, null,
                true, false, null, null, null, null, null, discoveryHeaders(candidate.requestKey())));

        var expected = tasks.findRuntimeState(plan).orElseThrow();
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(pool));
        UUID run = transactions.execute(status -> tasks.queueRun(expected, false, true).orElseThrow());

        assertThat(jdbc.queryForMap("""
                select r.collection_source_id,r.status,r.exclusive_source,
                       r.execution_parameters->>'filter_keywords' as keyword,
                       p.headers->'ircsCatalogDiscovery'->>'requestKey' as request_key
                  from collection_runs r join collection_plans p on p.current_run_id=r.id
                 where r.id=?
                """, run)).containsEntry("collection_source_id", source)
                .containsEntry("status", "QUEUED")
                .containsEntry("exclusive_source", true)
                .containsEntry("keyword", "Fixture")
                .containsEntry("request_key", candidate.requestKey());
        assertThat(requests.hasActiveTasks(candidate)).isTrue();
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
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isZero();
    }

    @Test
    void expiredClaimIsRecoverableButOldOwnerCannotRenewCompleteOrDeferIt() {
        insertRequest();
        CatalogDiscoveryCandidate expired = requests.claimDue(1, LEASE).getFirst();
        jdbc.update("update catalog_resource_discovery_jobs set lease_until = now() - interval '1 second'");
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();

        assertThat(current.leaseToken()).isNotEqualTo(expired.leaseToken());
        assertThat(current.attempts()).isZero();
        assertThat(requests.renew(expired, LEASE)).isFalse();
        assertThat(requests.markSubmitted(expired)).isFalse();
        assertThat(requests.markFulfilled(expired)).isFalse();
        assertThat(requests.retry(expired, Duration.ofHours(6), "STALE")).isFalse();
        assertThat(requests.markSubmitted(current)).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isZero();
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
    }

    @Test
    void retryRetainsRequestAndCannotBeReclaimedBeforeItsBackoff() {
        insertRequest();
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();

        assertThat(requests.retry(current, Duration.ofMinutes(5), "NO_ELIGIBLE_SOURCE")).isTrue();
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        assertThat(jdbc.queryForObject("select last_error from catalog_resource_discovery_jobs", String.class))
                .isEqualTo("NO_ELIGIBLE_SOURCE");
        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '1 second'");
        assertThat(requests.claimDue(1, LEASE)).hasSize(1);
    }

    @Test
    void noEligibleSourceRetryDoesNotCountAClaimOrBypassItsBackoff() {
        insertRequest();
        CatalogDiscoveryCandidate current = requests.claimDue(1, LEASE).getFirst();
        assertThat(requests.retry(current, Duration.ofHours(6), "NO_ELIGIBLE_SOURCE")).isTrue();
        assertThat(requests.claimDue(1, LEASE)).isEmpty();

        jdbc.update("update catalog_resource_discovery_jobs set updated_at = now() - interval '6 minutes'");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isZero();

        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '1 second'");
        assertThat(requests.claimDue(1, LEASE)).hasSize(1);
    }

    @Test
    void oldestDueRequestGetsTheSlotBeforeNewerHighPriorityWork() {
        insertRequest(2, 0, "older", 0);
        insertRequest(2, 1, "newer", 100);
        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '2 hours' "
                + "where target_episode = 0");
        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '1 minute' "
                + "where target_episode = 1");

        assertThat(requests.claimDue(1, LEASE).getFirst().targetEpisode()).isZero();
    }

    @Test
    void recentDueRequestsKeepPriorityAheadOfSlightlyOlderWork() {
        insertRequest(2, 0, "older", 0);
        insertRequest(2, 1, "higher-priority", 100);
        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '2 minutes' "
                + "where target_episode = 0");
        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '1 minute' "
                + "where target_episode = 1");

        assertThat(requests.claimDue(1, LEASE).getFirst().targetEpisode()).isEqualTo(1);
    }

    @Test
    void acceptedDispatchIsCountedButFulfillmentAndLeaseReclaimAreNot() {
        insertRequest();
        CatalogDiscoveryCandidate candidate = requests.claimDue(1, LEASE).getFirst();

        assertThat(requests.markFulfilled(candidate)).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isZero();

        insertRequest(2, 13, "Another", 0);
        CatalogDiscoveryCandidate dispatched = requests.claimDue(1, LEASE).getFirst();
        assertThat(requests.markDispatchAccepted(dispatched)).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs "
                + "where target_episode = 13", Integer.class)).isEqualTo(1);
    }

    @Test
    void realDispatchFailureIncrementsAttemptsButNoSourceDeferralPreservesHistory() {
        insertRequest();
        jdbc.update("update catalog_resource_discovery_jobs set attempts = 680");
        CatalogDiscoveryCandidate candidate = requests.claimDue(1, LEASE).getFirst();

        assertThat(requests.retry(candidate, Duration.ofMinutes(30), "NO_ELIGIBLE_SOURCE")).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(680);

        jdbc.update("update catalog_resource_discovery_jobs set next_attempt_at = now() - interval '1 second'");
        CatalogDiscoveryCandidate retryCandidate = requests.claimDue(1, LEASE).getFirst();
        assertThat(requests.retry(retryCandidate, Duration.ofMinutes(5), "DISCOVERY_DISPATCH_FAILED")).isTrue();
        assertThat(jdbc.queryForObject("select attempts from catalog_resource_discovery_jobs", Integer.class)).isEqualTo(681);
    }

    @Test
    void operatorsCanPauseReconciliationAndPublishFailureIsRetriedAfterResume() {
        CatalogDiscoveryCandidate current = acceptedRequest();
        UUID task = insertTask(insertSource(), "PAUSED", true, discoveryHeaders(current.requestKey()));
        assertThat(requests.isPausedByOperator(current)).isTrue();
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "SUBMITTED").containsEntry("last_error", "OPERATOR_PAUSED");
        assertThat(requests.reconcileSubmitted(5)).isZero();

        setRunStatus(task, "FAILED");
        makeReconciliationDue();
        assertThat(requests.isPausedByOperator(current)).isFalse();
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        assertThat(requestState()).containsEntry("status", "RETRY")
                .containsEntry("last_error", "DISPATCH_FAILED_AFTER_ACCEPT");
    }

    @Test
    void reconcilesCompletionAfterThePlaylistHandoffGracePeriod() {
        CatalogDiscoveryCandidate current = acceptedRequest();
        UUID task = insertTask(insertSource(), "RUNNING", true, discoveryHeaders(current.requestKey()));
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "SUBMITTED");

        setRunStatus(task, "COMPLETED");
        makeReconciliationDue();
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "SUBMITTED");

        jdbc.update("update collection_runs set finished_at = now() - interval '6 minutes', updated_at = now() - interval '6 minutes' where collection_plan_id = ?", task);
        makeReconciliationDue();
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "RETRY").containsEntry("last_error", "COLLECTION_NO_RESOURCE");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
    }

    @Test
    void confirmsExistingTargetResourcesEvenWhenNoChildRemains() {
        acceptedRequest();
        UUID raw = UUID.randomUUID();
        UUID playlist = UUID.randomUUID();
        insertPlaybackGraph(raw, playlist);
        insertPlaybackItem(raw, playlist, "S02E14", "https://source.example/14");

        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "RETRY").containsEntry("fulfilled", false);

        insertPlaybackItem(raw, playlist, "S02E12", "https://source.example/12");
        jdbc.update("update catalog_resource_discovery_jobs set status = 'SUBMITTED', fulfilled_at = null");
        makeReconciliationDue();
        assertThat(requests.reconcileSubmitted(5)).isEqualTo(1);
        assertThat(requestState()).containsEntry("status", "SUBMITTED").containsEntry("fulfilled", true);
        assertThat(requests.reconcileSubmitted(5)).isZero();
    }

    @Test
    void wholeWorkMayUseApprovedMagnetButEpisodeRequestMayNot() {
        jdbc.update("""
                insert into catalog_magnet_links(id,catalog_entry_id,status,provider_code,info_hash,magnet_uri,
                    title,matched_external_id_type,matched_external_id_value)
                values(uuidv7(),?,'CANDIDATE','fixture',repeat('f',40),'magnet:?xt=urn:btih:' || repeat('f',40),
                    'Fixture','TMDB','123')
                """, workId);
        CatalogDiscoveryCandidate wholeWork = new CatalogDiscoveryCandidate(workId, 0, 0, "Example", 1, UUID.randomUUID());
        CatalogDiscoveryCandidate episode = new CatalogDiscoveryCandidate(workId, 2, 12, "Example", 1, UUID.randomUUID());
        assertThat(requests.hasRequestedResources(wholeWork)).isFalse();
        jdbc.update("update catalog_magnet_links set status = 'APPROVED'");
        assertThat(requests.hasRequestedResources(wholeWork)).isTrue();
        assertThat(requests.hasRequestedResources(episode)).isFalse();
        jdbc.update("update catalog_entries set content_visibility = 'HIDDEN'");
        assertThat(requests.hasRequestedResources(wholeWork)).isFalse();
        jdbc.update("update catalog_entries set content_visibility = 'PUBLIC', adult_restricted = true");
        assertThat(requests.hasRequestedResources(wholeWork)).isFalse();
    }

    @Test
    void bindingAndMirrorCountsCannotPretendToFulfillAnEpisodeRequest() {
        CatalogDiscoveryCandidate target = new CatalogDiscoveryCandidate(workId, 2, 12, "Example", 1, UUID.randomUUID());
        UUID rawId = UUID.randomUUID();
        UUID playlistId = UUID.randomUUID();
        insertPlaybackGraph(rawId, playlistId);
        assertThat(requests.hasRequestedResources(target)).isFalse();
        insertPlaybackItem(rawId, playlistId, "第12集", "");
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update source_playback_items set name = 'HD', url = 'https://source.example/video.m3u8'");
        reconcilePlayback(rawId);
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update source_playback_items set name = '第12集'");
        reconcilePlayback(rawId);
        assertThat(requests.hasRequestedResources(target)).isTrue();
        jdbc.update("update source_playback_items set name = 'S01E12'");
        reconcilePlayback(rawId);
        assertThat(requests.hasRequestedResources(target)).isFalse();
        jdbc.update("update source_playback_items set name = 'S02E12'");
        jdbc.update("update source_records set season = null");
        reconcilePlayback(rawId);
        assertThat(requests.hasRequestedResources(target)).isTrue();
        jdbc.update("update catalog_entries set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb");
        assertThat(requests.hasRequestedResources(target)).isFalse();
    }

    @Test
    void publicCatalogDispatchDoesNotClaimAdultOrPrivateWorks() {
        insertRequest();
        jdbc.update("update catalog_entries set adult_assessment = '{\"level\":\"ADULT\"}'::jsonb");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
        jdbc.update("update catalog_entries set adult_assessment = '{}'::jsonb, content_visibility = 'HIDDEN'");
        assertThat(requests.claimDue(1, LEASE)).isEmpty();
    }

    @Test
    void catalogSourcesRespectOperatorOptOutAdultIsolationBusySourcesAndFailureCooldown() {
        UUID source = insertSource();
        UUID configuredTask = insertTask(source, "IDLE", true, "{}");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 100, List.of())).extracting(DataSourceSeedCandidate::id)
                .containsExactly(source);
        jdbc.update("update collection_plans set enabled = false where id = ?", configuredTask);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).isEmpty();
        jdbc.update("update collection_plans set enabled = true where id = ?", configuredTask);
        jdbc.update("update collection_sources set adult_restricted = true");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).isEmpty();
        jdbc.update("update collection_sources set adult_restricted = false");
        UUID busy = insertTask(source, "RUNNING", true, "{}");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).isEmpty();
        setRunStatus(busy, "FAILED");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).isEmpty();
        jdbc.update("update collection_plans set updated_at=now()-interval '31 minutes' where id=?",busy);
        jdbc.update("update collection_runs set updated_at=now()-interval '31 minutes' where collection_plan_id=?",busy);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).hasSize(1);
        jdbc.update("update collection_plans set headers = '{\"ircsCatalogDiscovery\":{\"requestKey\":\"request-key\"}}'::jsonb where id = ?", busy);
        setRunStatus(busy, "RUNNING");
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of())).hasSize(1);
        assertThat(tasks.findCatalogDiscoveryDataSources("other-work", 3, List.of())).isEmpty();
    }

    @Test
    void explicitAuthoritySourcesCanSearchWhilePeriodicPlansArePaused() {
        UUID selected = insertSource();
        UUID other = insertSource();
        UUID selectedPlan = insertTask(selected, "IDLE", false, "{}");
        insertTask(other, "IDLE", true, "{}");

        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of(selected)))
                .extracting(DataSourceSeedCandidate::id).containsExactly(selected);
        assertThat(jdbc.queryForObject("select enabled from collection_plans where id=?", Boolean.class, selectedPlan))
                .isFalse();
        jdbc.update("update collection_sources set adult_restricted=true where id=?", selected);
        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 3, List.of(selected))).isEmpty();
    }

    @Test
    void untriedSourceIsPreferredForThisRequestEvenWhenEarlierSourceHasYieldedOtherResults() {
        UUID earlier = insertSource();
        UUID untried = insertSource();
        insertTask(earlier, "IDLE", true, "{}");
        insertTask(untried, "IDLE", true, "{}");
        UUID attempt = insertTask(earlier, "COMPLETED", true, discoveryHeaders("request-key"));
        jdbc.update("update collection_runs set success_count = 10 where collection_plan_id = ?", attempt);

        assertThat(tasks.findCatalogDiscoveryDataSources("request-key", 1, List.of())).extracting(DataSourceSeedCandidate::id)
                .containsExactly(untried);
        assertThat(tasks.findCatalogDiscoveryDataSources("unrelated-request", 1, List.of())).extracting(DataSourceSeedCandidate::id)
                .containsExactly(earlier);
    }

    private CatalogDiscoveryCandidate acceptedRequest() {
        insertRequest();
        CatalogDiscoveryCandidate candidate = requests.claimDue(1, LEASE).getFirst();
        assertThat(requests.markDispatchAccepted(candidate)).isTrue();
        makeReconciliationDue();
        return candidate;
    }

    private void makeReconciliationDue() {
        jdbc.update("update catalog_resource_discovery_jobs set submitted_at = now() - interval '6 minutes', next_attempt_at = now()");
    }

    private Map<String, Object> requestState() {
        return jdbc.queryForMap("select status, last_error, fulfilled_at is not null as fulfilled from catalog_resource_discovery_jobs");
    }

    private static String discoveryHeaders(String requestKey) {
        return "{\"ircsCatalogDiscovery\":{\"requestKey\":\"" + requestKey + "\"}}";
    }

    private void insertRequest() {
        insertRequest(2, 12, "Example", 0);
    }

    private void insertRequest(int season, int episode, String keyword, int priority) {
        jdbc.update("insert into catalog_resource_discovery_jobs(catalog_entry_id, target_season, target_episode, keyword, priority) "
                + "values (?, ?, ?, ?, ?)", workId, season, episode, keyword, priority);
    }

    private UUID insertSource() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into collection_sources(id,created_at,updated_at,name,base_url,list_path,detail_path,field_mapping)
                values(?,now(),now(),?,'https://source.example','/api.php','/api.php','{"title":"vod_name"}')
                """, id,"Source " + id);
        return id;
    }

    private UUID insertTask(UUID source, String status, boolean enabled, String headers) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into collection_plans(id,collection_source_id,created_at,updated_at,name,enabled,headers,task_type,
                    request_delay_type,fixed_delay_ms,random_delay_min_ms,random_delay_max_ms,timeout_ms,max_retries,
                    enable_random_ua,use_custom_proxy)
                values(?,?,now(),now(),?,?,cast(? as jsonb),'COLLECT_ALL','FIXED',0,0,0,5000,0,false,false)
                """, id, source, "Plan " + id, enabled, headers);
        UUID runId = UUID.randomUUID();
        String runStatus = status.equals("IDLE") ? "STOPPED" : status.equals("STOPPING") ? "RUNNING" : status;
        jdbc.update("insert into collection_runs(id, collection_plan_id, collection_source_id, exclusive_source, "
                + "execution_parameters, status, start_page, checkpoint_page, started_at, finished_at) "
                + "values (?, ?, ?, false, '{}'::jsonb, ?, 1, 1, now(), case when ? in ('COMPLETED','FAILED','PAUSED','STOPPED') then now() end)",
                runId, id, source, runStatus, runStatus);
        jdbc.update("update collection_plans set current_run_id = ? where id = ?", runId, id);
        return id;
    }

    private void setRunStatus(UUID planId, String status) {
        jdbc.update("update collection_runs set status = ?, finished_at = case when ? in ('COMPLETED','FAILED','PAUSED','STOPPED') then now() else null end where collection_plan_id = ?",
                status, status, planId);
    }

    private void insertPlaybackGraph(UUID sourceRecordId, UUID playlistId) {
        jdbc.update("""
                insert into source_records(id,created_at,updated_at,source_vid,source_hash,title,season,
                    enrichment_status,normalization_status)
                values(?,now(),now(),?,?,'Fixture',2,'PENDING','PENDING')
                """, sourceRecordId, sourceRecordId.toString(), sourceRecordId.toString());
        jdbc.update("""
                insert into catalog_source_bindings(source_record_id,catalog_entry_id) values(?,?)
                """,sourceRecordId,workId);
        jdbc.update("""
                insert into source_playlists(id,source_record_id,created_at,updated_at,name)
                values(?,?,now(),now(),'Main')
                """,playlistId,sourceRecordId);
    }

    private void insertPlaybackItem(UUID sourceRecordId, UUID playlistId, String name, String url) {
        jdbc.update("""
                insert into source_playback_items(id,source_record_id,playlist_id,created_at,updated_at,name,url)
                values(uuidv7(),?,?,now(),now(),?,?)
                """,sourceRecordId,playlistId,name,url);
        reconcilePlayback(sourceRecordId);
    }

    private void reconcilePlayback(UUID sourceRecordId) {
        jdbc.query("select reconcile_catalog_playback(?)", (rs,row) -> null, sourceRecordId);
    }
}
