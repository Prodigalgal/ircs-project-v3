package com.prodigalgal.ircs.metadata.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
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
        transaction = new TransactionTemplate(new DataSourceTransactionManager(scoped));
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            // The generated schema is unique to this test; no shared tables or schemas are touched.
            admin.execute("drop schema " + schema + " cascade");
        }
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

    private List<ProviderType> prepare(UUID id, ProviderType provider) {
        return transaction.execute(ignored -> repository.prepareDispatch(id, "hash-v1", List.of(provider)));
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
