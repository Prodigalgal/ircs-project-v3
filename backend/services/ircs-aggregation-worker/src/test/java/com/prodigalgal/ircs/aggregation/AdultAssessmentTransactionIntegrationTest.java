package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.prodigalgal.ircs.common.adult.AdultAssessment;
import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** PostgreSQL-only CAS, row-lock and single-connection regression; CI supplies the real fixture. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class AdultAssessmentTransactionIntegrationTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private JdbcAggregationRepository repository;
    private DataSourceTransactionManager manager;
    private TransactionTemplate transaction;
    private AggregationService service;
    private SearchSyncWorkPublisher search;
    private AggregationWorkPublisher work;
    private String schema;
    private UUID id;
    private UUID rawId;
    private UUID sourceId;
    private UUID coverId;
    private UUID domainId;
    private Consumer<Map<UUID, AdultAssessmentInput>> remote = ignored -> {};

    @BeforeEach
    void setUp() {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "aggregation_adult_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(1000);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        jdbc.execute("""
                create table standard_category(id uuid primary key, slug text unique, name text);
                create table data_sources(id uuid primary key, name text, base_url text, adult_restricted boolean);
                create table source_domains(id uuid primary key, domain_value text);
                create table cover_images(id uuid primary key, source_domain_id uuid references source_domains(id));
                create table unified_videos(
                    id uuid primary key, version bigint default 0, title text, alias_title text,
                    description text, remarks text, subtitle text, category_code text,
                    actor_names jsonb default '[]', director_names jsonb default '[]', genre_codes jsonb default '[]',
                    locked_fields jsonb default '[]', adult_restricted boolean default false,
                    adult_assessment jsonb, adult_checked_at timestamptz, updated_at timestamptz default now());
                create table raw_videos(id uuid primary key, created_at timestamptz default now(), title text,
                    source_category_code text, source_category_name text, data_source_id uuid references data_sources(id),
                    cover_image_id uuid references cover_images(id), raw_metadata jsonb);
                create table raw_video_unified_video(
                    raw_video_id uuid references raw_videos(id),
                    unified_video_id uuid references unified_videos(id) on delete cascade,
                    primary key(raw_video_id, unified_video_id));
                """);
        id = UUID.randomUUID(); rawId = UUID.randomUUID(); sourceId = UUID.randomUUID();
        coverId = UUID.randomUUID(); domainId = UUID.randomUUID();
        jdbc.update("insert into standard_category values (?, 'movie', 'Movie')", UUID.randomUUID());
        jdbc.update("insert into source_domains values (?, 'source.example.test')", domainId);
        jdbc.update("insert into cover_images values (?, ?)", coverId, domainId);
        jdbc.update("insert into data_sources values (?, 'Source', 'https://source.example.test', false)", sourceId);
        jdbc.update("insert into unified_videos(id,title,category_code) values (?, 'Ordinary movie', 'movie')", id);
        jdbc.update("insert into raw_videos(id,title,source_category_code,source_category_name,data_source_id,cover_image_id) values (?, 'Raw movie', 'movie', 'Movie', ?, ?)", rawId, sourceId, coverId);
        jdbc.update("insert into raw_video_unified_video values (?, ?)", rawId, id);
        AdultAssessmentEvaluator evaluator = inputs -> {
            remote.accept(inputs);
            return Map.of(id, AdultAssessment.safe("google-test"));
        };
        repository = new JdbcAggregationRepository(new NamedParameterJdbcTemplate(pool),
                new AggregationMatchingStrategy(),
                (title, year) -> AggregationContextSearchClient.ContextSearchResult.notAttempted(),
                null, JsonMapper.builder().build(), evaluator, 5);
        manager = new DataSourceTransactionManager(pool);
        transaction = new TransactionTemplate(manager);
        search = mock(SearchSyncWorkPublisher.class);
        work = mock(AggregationWorkPublisher.class);
        service = new AggregationService(repository, new AggregationSearchSyncPublisher(search),
                mock(AggregationStorageCommandPublisher.class), mock(RawVideoGraphClusterer.class),
                mock(AggregationPipelineRuntime.class), mock(AggregationMatchKeyLock.class), work, manager);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) { pool.close(); }
        if (admin != null && schema != null) {
            // Only this test's randomly generated schema is removed.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void releasesSingleConnectionAndAdvisoryLockBeforeRemoteThenPublishesFinalResult() {
        remote = inputs -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(jdbc.queryForObject("select pg_try_advisory_xact_lock(194223, 1)", Boolean.class)).isTrue();
            assertThat(level()).isEqualTo("SUSPECT");
            assertThat(jdbc.queryForObject("select adult_checked_at is null from unified_videos where id=?", Boolean.class, id)).isTrue();
            verifyNoInteractions(search);
        };
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("SAFE");
        verify(search).enqueue(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        verifyNoInteractions(work);
    }

    @Test
    void committedLocalAdultRestrictionIsIndexedBeforeOtherItemPendingCanDeferBatch() {
        UUID pending = UUID.randomUUID();
        jdbc.update("update data_sources set adult_restricted=true where id=?", sourceId);
        jdbc.update("insert into unified_videos(id,title,category_code) values (?, 'Ordinary second movie', 'movie')", pending);
        remote = inputs -> {
            assertThat(inputs).containsKeys(id, pending);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select adult_restricted from unified_videos where id=?", Boolean.class, id)).isTrue();
            verify(search).enqueue(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
            org.mockito.Mockito.verify(search, org.mockito.Mockito.never())
                    .enqueue(pending, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
            throw new AdultAssessmentDeferredException("QUEUE_PENDING", 2);
        };
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("ADULT");
        assertThat(jdbc.queryForObject("select adult_assessment->>'level' from unified_videos where id=?", String.class, pending))
                .isEqualTo("SUSPECT");
        verify(search).enqueue(id, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        org.mockito.Mockito.verifyNoMoreInteractions(search);
        verify(work).enqueue(rawId, null, "adult-assessment-pending");
    }

    @Test
    void disabledSearchPublicationRemainsDisabledAfterRemoteAssessment() {
        service.backfillUnifiedAdultAssessments(10, false);
        assertThat(level()).isEqualTo("SAFE");
        verifyNoInteractions(search);
    }

    @Test
    void transientFailureLeavesReviewStateAndQueuesCommittedRawWithoutPublishingSafe() {
        remote = ignored -> { throw new AdultAssessmentDeferredException("GOOGLE_NSFW_PENDING", 86400); };
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("SUSPECT");
        assertThat(repository.findAdultAssessmentBackfillIds(10)).contains(id);
        verify(work).enqueue(rawId, null, "adult-assessment-pending");
        verifyNoInteractions(search);
        // Scope cleanup must permit the next operation on this same thread.
        remote = ignored -> {};
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("SAFE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "update unified_videos set title='New content'",
            "update unified_videos set locked_fields='[\"adult_restricted\"]', adult_restricted=true",
            "update unified_videos set adult_restricted=true, adult_assessment='{\"manual\":true}'",
            "update unified_videos set actor_names='[\"New actor\"]'",
            "update raw_videos set title='New raw content'",
            "update raw_videos set raw_metadata='{\"new\":true}'",
            "update data_sources set adult_restricted=true",
            "update cover_images set source_domain_id=null",
            "update source_domains set domain_value='new.example.test'",
            "update standard_category set name='New category'",
            "delete from raw_video_unified_video",
            "delete from unified_videos"
    })
    void refusesLateResultAfterContentSourceOverrideBindingOrDeletionChanges(String mutation) {
        AdultAssessmentSnapshot snapshot = capture();
        jdbc.update(mutation);
        assertThat(transaction.execute(status -> repository.applyAdultAssessment(snapshot, AdultAssessment.safe("late"))))
                .isFalse();
        assertThat(jdbc.queryForObject("select count(*) from unified_videos where adult_assessment->>'ruleVersion'='late'", Integer.class)).isZero();
    }

    @Test
    void rechecksOverrideAfterWaitingForConcurrentEditorRowLock() throws Exception {
        AdultAssessmentSnapshot snapshot = capture();
        DriverManagerDataSource independent = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        try (Connection editor = independent.getConnection(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            editor.setAutoCommit(false);
            try (var statement = editor.createStatement()) {
                statement.execute("set local search_path to " + schema);
                statement.executeUpdate("update unified_videos set locked_fields='[\"adultRestricted\"]', adult_restricted=true where id='" + id + "'");
            }
            var applied = executor.submit(() -> transaction.execute(status -> repository.applyAdultAssessment(snapshot, AdultAssessment.safe("late"))));
            assertThrows(TimeoutException.class, () -> applied.get(150, TimeUnit.MILLISECONDS));
            editor.commit();
            assertThat(applied.get(5, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(jdbc.queryForObject("select adult_restricted from unified_videos where id=?", Boolean.class, id)).isTrue();
    }

    @Test
    void blockedRemoteDoesNotBlockMaintenanceAndCannotOverwriteConcurrentManualOverride() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        remote = inputs -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("remote test timeout"); }
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = executor.submit(() -> service.backfillUnifiedAdultAssessments(10));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
                transaction.executeWithoutResult(status -> {
                    repository.lockAggregationMaintenance();
                    jdbc.update("update unified_videos set locked_fields='[\"adult_restricted\"]', adult_restricted=true where id=?", id);
                });
            } finally { release.countDown(); }
            run.get(5, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select adult_restricted from unified_videos where id=?", Boolean.class, id)).isTrue();
        verifyNoInteractions(search);
        verify(work).enqueue(rawId, null, "adult-assessment-pending");
    }

    private AdultAssessmentSnapshot capture() {
        TransactionTemplate read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> repository.captureAdultAssessment(id));
    }

    private String level() {
        return jdbc.queryForObject("select adult_assessment->>'level' from unified_videos where id=?", String.class, id);
    }
}
