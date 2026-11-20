package com.prodigalgal.ircs.metadata.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.EnrichmentResultMsg;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.metadata.config.MetadataConfigValues;
import com.prodigalgal.ircs.metadata.dispatch.messaging.MetadataEnrichPublisher;
import com.prodigalgal.ircs.metadata.result.application.MetadataResultCollectorService;
import com.prodigalgal.ircs.metadata.result.infrastructure.RawVideoMetadataRepository;
import com.prodigalgal.ircs.metadata.result.messaging.RawSearchSyncPublisher;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class MetadataPipelineRunRepositoryIntegrationTest {

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private MetadataPipelineRunRepository repository;
    private TransactionTemplate transaction;
    private String schema;
    private MetadataResultCollectorService collector;
    private RawSearchSyncPublisher search;
    private AggregationWorkPublisher aggregation;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource adminSource = dataSource();
        admin = new JdbcTemplate(adminSource);
        schema = "provider_retry_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        DriverManagerDataSource scoped = dataSource();
        Properties properties = new Properties();
        properties.setProperty("currentSchema", schema);
        scoped.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(scoped);
        jdbc.execute("""
                create table raw_videos(id uuid primary key, title text, douban_id text, tmdb_id text, imdb_id text,
                    rotten_tomatoes_id text, description text default 'preserved description', score numeric,
                    year text, alias_title text, raw_metadata jsonb default '{}', locked_fields jsonb default '[]',
                    enrichment_status text default 'PENDING', enrichment_retry_count integer default 2,
                    aggregation_status text default 'PENDING', data_hash text, updated_at timestamptz default now(),
                    actor_names jsonb default '[]',director_names jsonb default '[]',area_codes jsonb default '[]',
                    language_codes jsonb default '[]',genre_codes jsonb default '[]')
                """);
        jdbc.execute("""
                create table raw_video_pipeline_runs (
                    raw_video_id uuid not null, pipeline_version text not null, step text not null,
                    status text not null, expected_count integer not null,
                    completed_count integer not null, success_count integer not null,
                    failure_count integer not null, retryable_failure_count integer not null,
                    permanent_failure_count integer not null,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    primary key (raw_video_id, pipeline_version, step)
                )
                """);
        jdbc.execute("""
                create table raw_video_enrichment_provider_runs (
                    raw_video_id uuid not null, pipeline_version text not null, provider_type text not null,
                    status text not null, attempt_count integer not null,
                    dispatched_at timestamptz, created_at timestamptz not null, updated_at timestamptz not null,
                    completed_at timestamptz, retryable boolean not null default false,
                    error_code text, error_message text,
                    primary key (raw_video_id, pipeline_version, provider_type)
                )
                """);
        repository = new MetadataPipelineRunRepository(new NamedParameterJdbcTemplate(scoped));
        var manager = new DataSourceTransactionManager(scoped);
        transaction = new TransactionTemplate(manager);
        var mapper = JsonMapper.builder().build();
        search = mock(RawSearchSyncPublisher.class);
        aggregation = mock(AggregationWorkPublisher.class);
        var target = new MetadataResultCollectorService(new RawVideoMetadataRepository(jdbc, mapper), search,
                repository, mock(MetadataEnrichPublisher.class), aggregation, mock(MetadataConfigValues.class), mapper);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        collector = (MetadataResultCollectorService) proxy.getProxy();
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            // The generated schema is unique to this test; no shared tables or schemas are touched.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void reconcilesTerminalSuccessWithoutChangingMetadataOrCountingProviderCompletionTwice() {
        UUID id = raw("hash-v1", "12345");
        complete(id, true);
        Map<String, Object> counts = pipeline(id);

        assertThat(collector.reconcileCompletedRun(id, "hash-v1").enrichmentStatus()).isEqualTo("SUCCESS");
        assertThat(collector.reconcileCompletedRun(id, "hash-v1").enrichmentStatus()).isEqualTo("SUCCESS");

        assertThat(jdbc.queryForObject("select enrichment_status from raw_videos where id=?", String.class, id))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("select description from raw_videos where id=?", String.class, id))
                .isEqualTo("preserved description");
        assertThat(jdbc.queryForObject("select tmdb_id from raw_videos where id=?", String.class, id)).isEqualTo("12345");
        assertThat(pipeline(id).get("completed_count")).isEqualTo(counts.get("completed_count"));
        assertThat(pipeline(id).get("success_count")).isEqualTo(counts.get("success_count"));
        verify(search, times(1)).publishIndex(id);
        verify(aggregation, times(1)).enqueue(id, "hash-v1", "metadata-completed-run-reconciled");
    }

    @Test
    void terminalNoMatchFinalizesFailureWithoutRepeatingTheProviderOrSpendingAnotherRetry() {
        UUID id = raw("hash-v1", null);
        complete(id, false);

        assertThat(collector.reconcileCompletedRun(id, "hash-v1").enrichmentStatus()).isEqualTo("PERMANENT_FAILURE");

        assertThat(jdbc.queryForObject("select enrichment_retry_count from raw_videos where id=?", Integer.class, id))
                .isEqualTo(2);
        assertThat(pipeline(id).get("completed_count")).isEqualTo(1);
        verify(aggregation).enqueue(id, "hash-v1", "metadata-completed-run-reconciled");
    }

    @Test
    void oldCompletedRunCannotOverwriteANewerRawInputVersion() {
        UUID id = raw("hash-v2", "12345");
        complete(id, true);

        assertThat(collector.reconcileCompletedRun(id, "hash-v1").enrichmentStatus()).isEqualTo("STALE");

        assertThat(jdbc.queryForObject("select enrichment_status from raw_videos where id=?", String.class, id))
                .isEqualTo("PENDING");
        verifyNoInteractions(search, aggregation);
    }

    @Test
    void pendingProviderRunCannotBeReconciledAsACompletedRun() {
        UUID id = raw("hash-v1", null);
        prepare(id, ProviderType.TMDB, ProviderType.DOUBAN);
        transaction.execute(ignored -> repository.completeProvider(id, "hash-v1", ProviderType.TMDB,
                true, false, null, null, 0));

        assertThat(collector.reconcileCompletedRun(id, "hash-v1").enrichmentStatus()).isEqualTo("PENDING");

        assertThat(jdbc.queryForObject("select enrichment_status from raw_videos where id=?", String.class, id))
                .isEqualTo("PENDING");
        verifyNoInteractions(search, aggregation);
    }

    @Test
    void failedLocalHandoffRollsBackBothRawFinalizationAndRunStatus() {
        UUID id = raw("hash-v1", "12345");
        complete(id, true);
        doThrow(new IllegalStateException("submit unavailable")).when(search).publishIndex(id);

        assertThrows(IllegalStateException.class, () -> collector.reconcileCompletedRun(id, "hash-v1"));

        assertThat(jdbc.queryForObject("select enrichment_status from raw_videos where id=?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(pipeline(id).get("status")).isEqualTo("PENDING");
        verifyNoInteractions(aggregation);
    }

    @Test
    void invalidSuccessIsDurablyPermanentFailureAndNeverAppliesProviderFields() {
        UUID id = raw("hash-v1", "0");
        jdbc.update("update raw_videos set raw_metadata='{\"sourcePayload\":{\"tmdbId\":0}}' where id=?", id);
        prepare(id, ProviderType.TMDB);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("0");
        metadata.setDescription("invalid provider description");
        metadata.addActor("invalid provider actor");

        assertThat(collector.process(EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB).success(true).retryable(true).metadata(metadata).build(), 0)
                .enrichmentStatus()).isEqualTo("PERMANENT_FAILURE");

        assertThat(providerStatus(id)).isEqualTo("PERMANENT_FAILURE");
        assertThat(jdbc.queryForObject("select error_code from raw_video_enrichment_provider_runs where raw_video_id=?", String.class, id)).isEqualTo("INVALID_PROVIDER_ID");
        assertThat(jdbc.queryForObject("select retryable from raw_video_enrichment_provider_runs where raw_video_id=?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("select tmdb_id from raw_videos where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select description from raw_videos where id=?", String.class, id)).isEqualTo("preserved description");
        assertThat(jdbc.queryForObject("select actor_names::text from raw_videos where id=?", String.class, id)).isEqualTo("[]");
        assertThat(jdbc.queryForObject("select raw_metadata->'sourcePayload'->'tmdbId' from raw_videos where id=?", String.class, id)).isEqualTo("0");
    }

    @Test
    void disjointSameScriptCastRejectsShortTitleCandidateDurablyAndKeepsSourceData() {
        UUID id = raw("hash-v1", null);
        jdbc.update("""
                update raw_videos set title='谎言',
                    actor_names='["柳演锡","徐玄振","玉子妍"]'
                where id=?
                """, id);
        prepare(id, ProviderType.TMDB);
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId("293607");
        metadata.setImdbId("tt39201866");
        metadata.setActorNames(java.util.Set.of("金裕贞", "朴珍荣", "金贤珠"));
        metadata.setDescription("Different work");
        EnrichmentResultMsg result = EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1")
                .providerType(ProviderType.TMDB).success(true).metadata(metadata).build();

        assertThat(collector.process(result, 0).enrichmentStatus()).isEqualTo("PERMANENT_FAILURE");
        assertThat(collector.process(result, 0).enrichmentStatus()).isEqualTo("PERMANENT_FAILURE");

        assertThat(jdbc.queryForMap("""
                select status, error_code, retryable, attempt_count
                from raw_video_enrichment_provider_runs where raw_video_id=?
                """, id)).containsEntry("status", "PERMANENT_FAILURE")
                .containsEntry("error_code", "PROVIDER_CAST_CONFLICT")
                .containsEntry("retryable", false).containsEntry("attempt_count", 1);
        assertThat(pipeline(id)).containsEntry("success_count", 0)
                .containsEntry("permanent_failure_count", 1).containsEntry("completed_count", 1);
        assertThat(jdbc.queryForMap("select tmdb_id,imdb_id,description,actor_names::text from raw_videos where id=?", id))
                .containsEntry("tmdb_id", null).containsEntry("imdb_id", null)
                .containsEntry("description", "preserved description")
                .containsEntry("actor_names", "[\"柳演锡\", \"徐玄振\", \"玉子妍\"]");
        verify(search, times(1)).publishIndex(id);
        verify(aggregation, times(1)).enqueue(id, "hash-v1", "metadata-finalized");
    }

    @Test
    void sequentialProvidersPreserveBothIdsAndStructuredPeopleAndIgnoreDuplicatePayload() {
        UUID id = raw("hash-v1", null);
        prepare(id, ProviderType.TMDB, ProviderType.DOUBAN);
        EnrichedMetadataDTO first = new EnrichedMetadataDTO();
        first.setTmdbId("101");
        first.addActor("罗予彤");
        EnrichedMetadataDTO second = new EnrichedMetadataDTO();
        second.setDoubanId("202");
        second.addActor("孙乾钊");
        assertThat(collector.process(providerMessage(id, ProviderType.TMDB, first), 0).enrichmentStatus()).isEqualTo("PENDING");
        assertThat(collector.process(providerMessage(id, ProviderType.DOUBAN, second), 0).enrichmentStatus()).isEqualTo("SUCCESS");
        second.addActor("duplicate must not apply");
        assertThat(collector.process(providerMessage(id, ProviderType.DOUBAN, second), 0).enrichmentStatus()).isEqualTo("SUCCESS");

        assertThat(jdbc.queryForObject("select tmdb_id from raw_videos where id=?", String.class, id)).isEqualTo("101");
        assertThat(jdbc.queryForObject("select douban_id from raw_videos where id=?", String.class, id)).isEqualTo("202");
        assertThat(jdbc.queryForObject("select actor_names::text from raw_videos where id=?", String.class, id))
                .contains("罗予彤", "孙乾钊").doesNotContain("duplicate must not apply");
        assertThat(pipeline(id).get("completed_count")).isEqualTo(2);
        assertThat(pipeline(id).get("success_count")).isEqualTo(2);
        verify(search, times(1)).publishIndex(id);
    }

    private EnrichmentResultMsg providerMessage(UUID id, ProviderType provider, EnrichedMetadataDTO metadata) {
        return EnrichmentResultMsg.builder().videoId(id).pipelineVersion("hash-v1").providerType(provider)
                .success(true).metadata(metadata).build();
    }

    private UUID raw(String hash, String tmdbId) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into raw_videos(id,data_hash,tmdb_id) values (?,?,?)", id, hash, tmdbId);
        return id;
    }

    private void complete(UUID id, boolean success) {
        prepare(id, ProviderType.TMDB);
        transaction.execute(ignored -> repository.completeProvider(id, "hash-v1", ProviderType.TMDB,
                success, false, success ? null : "NOT_FOUND", null, 0));
    }

    @Test
    void retryResetsCountersRejectsLateOldAttemptAndCountsNewSuccessOnlyOnce() {
        UUID id = UUID.randomUUID();
        assertThat(prepare(id, ProviderType.TMDB)).containsExactly(ProviderType.TMDB);
        var failed = transaction.execute(ignored -> repository.completeProvider(
                id, "hash-v1", ProviderType.TMDB, false, true, "TRANSIENT", "try again", 0));
        assertThat(failed.newlyCompleted()).isTrue();
        assertThat(failed.retryableFailureCount()).isEqualTo(1);
        transaction.executeWithoutResult(ignored -> repository.markRunFinished(id, "hash-v1", "FAILED"));

        assertThat(prepare(id, ProviderType.TMDB)).containsExactly(ProviderType.TMDB);
        Map<String, Object> reset = pipeline(id);
        assertThat(reset.get("status")).isEqualTo("PENDING");
        assertThat(reset.get("completed_count")).isEqualTo(0);
        assertThat(reset.get("success_count")).isEqualTo(0);
        assertThat(reset.get("failure_count")).isEqualTo(0);
        assertThat(reset.get("retryable_failure_count")).isEqualTo(0);
        assertThat(repository.findPendingProviderAttempt(id, "hash-v1", ProviderType.TMDB)).contains(1);

        var late = transaction.execute(ignored -> repository.completeProvider(
                id, "hash-v1", ProviderType.TMDB, true, false, null, null, 0));
        assertThat(late.newlyCompleted()).isFalse();
        assertThat(late.completedCount()).isZero();
        assertThat(providerStatus(id)).isEqualTo("PENDING");
        assertThat(repository.findPendingProviderAttempt(id, "hash-v1", ProviderType.TMDB)).contains(1);

        var succeeded = transaction.execute(ignored -> repository.completeProvider(
                id, "hash-v1", ProviderType.TMDB, true, false, null, null, 1));
        assertThat(succeeded.newlyCompleted()).isTrue();
        assertThat(succeeded.allCompleted()).isTrue();
        assertThat(succeeded.successCount()).isEqualTo(1);
        assertThat(succeeded.failureCount()).isZero();
        var duplicate = transaction.execute(ignored -> repository.completeProvider(
                id, "hash-v1", ProviderType.TMDB, true, false, null, null, 1));
        assertThat(duplicate.newlyCompleted()).isFalse();
        assertThat(duplicate.completedCount()).isEqualTo(1);
        assertThat(duplicate.successCount()).isEqualTo(1);
        assertThat(providerStatus(id)).isEqualTo("SUCCESS");
    }

    @Test
    void permanentProviderFailureIsNotResetOrRecountedDuringDispatch() {
        UUID id = UUID.randomUUID();
        prepare(id, ProviderType.DOUBAN);
        transaction.executeWithoutResult(ignored -> repository.completeProvider(
                id, "hash-v1", ProviderType.DOUBAN, false, false, "NOT_FOUND", "no match", 0));

        assertThat(prepare(id, ProviderType.DOUBAN)).isEmpty();
        assertThat(providerStatus(id)).isEqualTo("PERMANENT_FAILURE");
        Map<String, Object> run = pipeline(id);
        assertThat(run.get("completed_count")).isEqualTo(1);
        assertThat(run.get("failure_count")).isEqualTo(1);
        assertThat(run.get("permanent_failure_count")).isEqualTo(1);
        assertThat(run.get("retryable_failure_count")).isEqualTo(0);
    }

    private List<ProviderType> prepare(UUID id, ProviderType... providers) {
        return transaction.execute(ignored -> repository.prepareDispatch(id, "hash-v1", List.of(providers)));
    }

    private Map<String, Object> pipeline(UUID id) {
        return jdbc.queryForMap("select * from raw_video_pipeline_runs where raw_video_id = ?", id);
    }

    private String providerStatus(UUID id) {
        return jdbc.queryForObject("select status from raw_video_enrichment_provider_runs where raw_video_id = ?",
                String.class, id);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
    }
}
