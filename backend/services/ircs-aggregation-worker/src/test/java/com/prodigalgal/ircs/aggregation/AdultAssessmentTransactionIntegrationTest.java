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
        jdbc.execute("create table catalog_metadata_enrichment_jobs(catalog_entry_id uuid primary key,last_success_at timestamptz)");
        jdbc.execute("""
                create table catalog_categories(id uuid primary key, slug text unique, name text);
                create table collection_sources(id uuid primary key, name text, base_url text, adult_restricted boolean);
                create table source_origins(id uuid primary key, domain_value text);
                create table media_image_sources(id uuid primary key, source_origin_id uuid references source_origins(id));
                create table catalog_entries(
                    id uuid primary key, version bigint default 0, title text, alias_title text,
                    description text, remarks text, subtitle text, category_code text,
                    category_id uuid, year text, score numeric, published_at date, total_episodes text,
                    duration text, season integer, douban_id text, tmdb_id text, imdb_id text, rotten_tomatoes_id text,
                    metadata_status text default 'SYNCED', area_codes jsonb default '[]',
                    actor_names jsonb default '[]', director_names jsonb default '[]', genre_codes jsonb default '[]',
                    locked_fields jsonb default '[]', adult_restricted boolean default false,
                    adult_assessment jsonb, adult_checked_at timestamptz, updated_at timestamptz default now());
                create table source_records(id uuid primary key, created_at timestamptz default now(), title text,
                    source_category_code text, source_category_name text, collection_source_id uuid references collection_sources(id),
                    media_image_source_id uuid references media_image_sources(id), raw_metadata jsonb,
                    alias_title text, description text, year text, score numeric, published_at date,
                    total_episodes text, duration text, remarks text, subtitle text, season integer,
                    category_code text default 'movie', douban_id text, tmdb_id text, imdb_id text, rotten_tomatoes_id text,
                    normalization_status text default 'READY', enrichment_status text default 'SUCCESS',
                    aggregation_status text default 'BOUND', aggregation_status_updated_at timestamptz default now(),
                    updated_at timestamptz default now(), actor_names jsonb default '[]',
                    director_names jsonb default '[]', area_codes jsonb default '[]');
                create table catalog_source_bindings(
                    source_record_id uuid references source_records(id),
                    catalog_entry_id uuid references catalog_entries(id) on delete cascade,
                    primary key(source_record_id, catalog_entry_id));
                """);
        id = UUID.randomUUID(); rawId = UUID.randomUUID(); sourceId = UUID.randomUUID();
        coverId = UUID.randomUUID(); domainId = UUID.randomUUID();
        jdbc.update("insert into catalog_categories values (?, 'movie', 'Movie')", UUID.randomUUID());
        jdbc.update("insert into source_origins values (?, 'source.example.test')", domainId);
        jdbc.update("insert into media_image_sources values (?, ?)", coverId, domainId);
        jdbc.update("insert into collection_sources values (?, 'Source', 'https://source.example.test', false)", sourceId);
        jdbc.update("insert into catalog_entries(id,title,category_code) values (?, 'Ordinary movie', 'movie')", id);
        jdbc.update("insert into source_records(id,title,source_category_code,source_category_name,collection_source_id,media_image_source_id) values (?, 'Raw movie', 'movie', 'Movie', ?, ?)", rawId, sourceId, coverId);
        jdbc.update("insert into catalog_source_bindings values (?, ?)", rawId, id);
        AdultAssessmentEvaluator evaluator = inputs -> {
            remote.accept(inputs);
            return Map.of(id, AdultAssessment.safe("google-test"));
        };
        repository = new JdbcAggregationRepository(new NamedParameterJdbcTemplate(pool),
                AggregationTestFixtures.matchingStrategy(),
                (title, year) -> AggregationContextSearchClient.ContextSearchResult.notAttempted(),
                null, JsonMapper.builder().build(), evaluator, 5);
        manager = new DataSourceTransactionManager(pool);
        transaction = new TransactionTemplate(manager);
        search = mock(SearchSyncWorkPublisher.class);
        work = mock(AggregationWorkPublisher.class);
        service = new AggregationService(repository, new AggregationSearchSyncPublisher(search),
                mock(AggregationStorageCommandPublisher.class), mock(SourceRecordGraphClusterer.class),
                mock(AggregationPipelineRuntime.class), mock(AggregationMatchKeyLock.class), work, manager,
                AggregationTestFixtures.matchingStrategy(), AggregationTestFixtures.safetyPolicy());
        org.springframework.aop.framework.ProxyFactory proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        service = (AggregationService) proxy.getProxy();
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
            assertThat(jdbc.queryForObject("select adult_checked_at is null from catalog_entries where id=?", Boolean.class, id)).isTrue();
            verifyNoInteractions(search);
        };
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("SAFE");
        verify(search).enqueue(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
        verifyNoInteractions(work);
    }

    @Test
    void dependencyWaitLeavesPostgresRowVersionAndTimestampUntouched() {
        jdbc.update("update source_records set normalization_status='PENDING',enrichment_status='PENDING',"
                + "aggregation_status='PENDING',updated_at='2026-01-01T00:00:00Z' where id=?", rawId);
        String before = jdbc.queryForObject("select xmin::text||':'||xmax::text||':'||updated_at::text "
                + "from source_records where id=?", String.class, rawId);

        assertThat(service.aggregateRuntimeWork(rawId).status()).isEqualTo("WAITING_DEPENDENCIES");

        assertThat(jdbc.queryForObject("select xmin::text||':'||xmax::text||':'||updated_at::text "
                + "from source_records where id=?", String.class, rawId)).isEqualTo(before);
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        verifyNoInteractions(search, work);
    }

    @Test
    void committedLocalAdultRestrictionIsIndexedBeforeOtherItemPendingCanDeferBatch() {
        UUID pending = UUID.randomUUID();
        jdbc.update("update collection_sources set adult_restricted=true where id=?", sourceId);
        jdbc.update("insert into catalog_entries(id,title,category_code) values (?, 'Ordinary second movie', 'movie')", pending);
        remote = inputs -> {
            assertThat(inputs).containsKeys(id, pending);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(jdbc.queryForObject("select adult_restricted from catalog_entries where id=?", Boolean.class, id)).isTrue();
            verify(search).enqueue(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
            org.mockito.Mockito.verify(search, org.mockito.Mockito.never())
                    .enqueue(pending, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
            throw new AdultAssessmentDeferredException("QUEUE_PENDING", 2);
        };
        service.backfillUnifiedAdultAssessments(10);
        assertThat(level()).isEqualTo("ADULT");
        assertThat(jdbc.queryForObject("select adult_assessment->>'level' from catalog_entries where id=?", String.class, pending))
                .isEqualTo("SUSPECT");
        verify(search).enqueue(id, SearchEntityType.CATALOG_ENTRY, SyncOperation.INDEX);
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
            "update catalog_entries set title='New content'",
            "update catalog_entries set locked_fields='[\"adult_restricted\"]', adult_restricted=true",
            "update catalog_entries set adult_restricted=true, adult_assessment='{\"manual\":true}'",
            "update catalog_entries set actor_names='[\"New actor\"]'",
            "update source_records set title='New raw content'",
            "update source_records set raw_metadata='{\"new\":true}'",
            "update collection_sources set adult_restricted=true",
            "update media_image_sources set source_origin_id=null",
            "update source_origins set domain_value='new.example.test'",
            "update catalog_categories set name='New category'",
            "delete from catalog_source_bindings",
            "delete from catalog_entries"
    })
    void refusesLateResultAfterContentSourceOverrideBindingOrDeletionChanges(String mutation) {
        AdultAssessmentSnapshot snapshot = capture();
        jdbc.update(mutation);
        Boolean applied = transaction.execute(
                status -> repository.applyAdultAssessment(snapshot, AdultAssessment.safe("late")));
        assertThat(applied).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from catalog_entries where adult_assessment->>'ruleVersion'='late'", Integer.class)).isZero();
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
                statement.executeUpdate("update catalog_entries set locked_fields='[\"adultRestricted\"]', adult_restricted=true where id='" + id + "'");
            }
            var applied = executor.submit(() -> transaction.execute(status -> repository.applyAdultAssessment(snapshot, AdultAssessment.safe("late"))));
            assertThrows(TimeoutException.class, () -> applied.get(150, TimeUnit.MILLISECONDS));
            editor.commit();
            assertThat(applied.get(5, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(jdbc.queryForObject("select adult_restricted from catalog_entries where id=?", Boolean.class, id)).isTrue();
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
                    jdbc.update("update catalog_entries set locked_fields='[\"adult_restricted\"]', adult_restricted=true where id=?", id);
                });
            } finally { release.countDown(); }
            run.get(5, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select adult_restricted from catalog_entries where id=?", Boolean.class, id)).isTrue();
        verifyNoInteractions(search);
        verify(work).enqueue(rawId, null, "adult-assessment-pending");
    }

    @Test
    void boundAdultRetryUsesReleasedConnectionWithoutDemotingOrRebuildingMembership() {
        remote = inputs -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
        };
        AggregationResult result = service.aggregateRuntimeWork(rawId);
        assertThat(result.status()).isEqualTo("BOUND");
        assertThat(level()).isEqualTo("SAFE");
        assertThat(jdbc.queryForObject("select aggregation_status from source_records where id=?", String.class, rawId))
                .isEqualTo("BOUND");
        assertThat(jdbc.queryForObject("select count(*) from catalog_source_bindings", Integer.class)).isEqualTo(1);
        verifyNoInteractions(work);
    }

    @Test
    void completedBoundDuplicateDoesNotWriteFieldsOrPublishIndexes() {
        service.backfillUnifiedAdultAssessments(10);
        org.mockito.Mockito.clearInvocations(search);
        Long version = jdbc.queryForObject("select version from catalog_entries where id=?", Long.class, id);
        AggregationResult result = service.aggregateRuntimeWork(rawId);
        assertThat(result.status()).isEqualTo("ALREADY_BOUND");
        assertThat(jdbc.queryForObject("select version from catalog_entries where id=?", Long.class, id)).isEqualTo(version);
        verifyNoInteractions(search, work);
    }

    @Test
    void adultWriteYieldsImmediatelyWhenBindingWriterAlreadyOwnsRawRow() throws Exception {
        AdultAssessmentSnapshot snapshot = capture();
        DriverManagerDataSource independent = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        try (Connection writer = independent.getConnection(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            writer.setAutoCommit(false);
            try (var statement = writer.createStatement()) {
                statement.execute("set local search_path to " + schema);
                statement.executeQuery("select id from source_records where id='" + rawId + "' for update");
            }
            var attempt = executor.submit(() -> {
                AdultAssessmentDeferredException deferred = assertThrows(AdultAssessmentDeferredException.class,
                        () -> transaction.execute(status -> repository.applyAdultAssessment(snapshot, AdultAssessment.safe("late"))));
                return deferred.getMessage();
            });
            assertThat(attempt.get(2, TimeUnit.SECONDS)).isEqualTo("CONTENT_SAFETY_SNAPSHOT_CHANGED");
            assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
            writer.rollback();
        }
        assertThat(jdbc.queryForObject("select adult_checked_at is null from catalog_entries where id=?", Boolean.class, id))
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "123", "unknown"})
    void invalidExternalIdIsReplacedWithUsableSourceEvidence(String initialId) {
        jdbc.update("update catalog_entries set douban_id=? where id=?", initialId, id);
        jdbc.update("update source_records set douban_id='987654', tmdb_id='123' where id=?", rawId);
        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));
        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id))
                .isEqualTo("987654");
        assertThat(jdbc.queryForObject("select tmdb_id from catalog_entries where id=?", String.class, id))
                .isEqualTo("123");
    }

    @Test
    void rejectedProviderIdCannotBeFilledIntoAnEmptyUnifiedField() {
        jdbc.update("update source_records set douban_id='123', tmdb_id='123' where id=?", rawId);
        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));
        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select tmdb_id from catalog_entries where id=?", String.class, id)).isEqualTo("123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "456"})
    void rejectedLegacyUnifiedIdIsClearedWhenNoUsableMemberReplacementExists(String rejectedId) {
        jdbc.update("update catalog_entries set douban_id=? where id=?", rejectedId, id);
        jdbc.update("update source_records set douban_id=? where id=?", rejectedId, rawId);

        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));

        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select douban_id from source_records where id=?", String.class, rawId)).isEqualTo(rejectedId);
    }

    @Test
    void unrelatedLegacyUnifiedIdIsPreservedWhenNoUsableMemberReplacementExists() {
        jdbc.update("update catalog_entries set douban_id='legacy-value' where id=?", id);
        jdbc.update("update source_records set douban_id='123' where id=?", rawId);

        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));

        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id))
                .isEqualTo("legacy-value");
    }

    @Test
    void conflictedHistoricalClusterClearsRejectedIdWithoutCopyingMemberIds() {
        UUID other = UUID.randomUUID();
        jdbc.update("update catalog_entries set douban_id='123' where id=?", id);
        jdbc.update("update source_records set douban_id='987654', tmdb_id='101' where id=?", rawId);
        jdbc.update("insert into source_records(id,title,douban_id,tmdb_id) values (?,?,?,?)",
                other, "Another film", "876543", "102");
        jdbc.update("insert into catalog_source_bindings values (?, ?)", other, id);

        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));

        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select tmdb_id from catalog_entries where id=?", String.class, id)).isNull();
        transaction.executeWithoutResult(status -> repository.rebuildUnifiedPipelineExternalIds(id));
        assertThat(jdbc.queryForObject("select douban_id from catalog_entries where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select tmdb_id from catalog_entries where id=?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select douban_id from source_records where id=?", String.class, rawId))
                .isEqualTo("987654");
    }

    @Test
    void pendingBackfillSelectsReadyInputsWithoutDemotingExistingBoundMembers() {
        jdbc.update("update source_records set aggregation_status='PENDING' where id=?", rawId);
        jdbc.update("insert into source_records(id,title,normalization_status,aggregation_status) values (?, 'Not ready', 'PENDING', 'PENDING')",
                UUID.randomUUID());
        jdbc.update("insert into source_records(id,title,enrichment_status,aggregation_status) values (?, 'Waiting metadata', 'PENDING', 'PENDING')",
                UUID.randomUUID());
        jdbc.update("insert into source_records(id,title,aggregation_status) values (?, ' ', 'PENDING')", UUID.randomUUID());
        jdbc.update("insert into source_records(id,title,aggregation_status) values (?, 'Already bound', 'BOUND')", UUID.randomUUID());
        assertThat(repository.findEligiblePendingRawIds(10)).containsExactly(rawId);
    }

    private AdultAssessmentSnapshot capture() {
        TransactionTemplate read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return read.execute(status -> repository.captureAdultAssessment(id));
    }

    private String level() {
        return jdbc.queryForObject("select adult_assessment->>'level' from catalog_entries where id=?", String.class, id);
    }
}
