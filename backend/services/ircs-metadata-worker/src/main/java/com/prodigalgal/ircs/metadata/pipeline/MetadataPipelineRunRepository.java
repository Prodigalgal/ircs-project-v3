package com.prodigalgal.ircs.metadata.pipeline;

import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataSourceEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class MetadataPipelineRunRepository {

    private static final String STEP = "ENRICH_METADATA";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PERMANENT_FAILURE = "PERMANENT_FAILURE";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public List<ProviderType> prepareDispatch(UUID rawVideoId, String pipelineVersion, List<ProviderType> providers) {
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        String version = normalizeVersion(pipelineVersion);
        List<String> providerNames = providers.stream().map(Enum::name).distinct().sorted().toList();
        var parameters = new MapSqlParameterSource()
                .addValue("rawVideoId", rawVideoId)
                .addValue("pipelineVersion", version)
                .addValue("step", STEP)
                .addValue("providers", providerNames);
        // A new provider can become applicable to an already-dispatched revision. Only durable
        // additions increase the expected count, and callbacks cannot observe an uncounted provider.
        jdbcTemplate.update(
                """
                with added as (
                    insert into raw_video_enrichment_provider_runs (
                        raw_video_id, pipeline_version, provider_type, status,
                        attempt_count, dispatched_at, created_at, updated_at
                    )
                    select :rawVideoId, :pipelineVersion, provider_type, 'PENDING', 0, now(), now(), now()
                    from unnest(cast(array[:providers] as varchar[])) as provider(provider_type)
                    order by provider_type
                    on conflict (raw_video_id, pipeline_version, provider_type) do nothing
                    returning 1
                )
                insert into raw_video_pipeline_runs (
                    raw_video_id, pipeline_version, step, status, expected_count,
                    completed_count, success_count, failure_count,
                    retryable_failure_count, permanent_failure_count,
                    created_at, updated_at
                )
                select :rawVideoId, :pipelineVersion, :step, 'PENDING', count(*),
                       0, 0, 0, 0, 0, now(), now()
                from added having count(*) > 0
                on conflict (raw_video_id, pipeline_version, step) do update
                set expected_count=raw_video_pipeline_runs.expected_count+excluded.expected_count,
                    status='PENDING', updated_at=now()
                """,
                parameters);

        // Reset only retryable failures. Deduct their previous terminal contribution so a
        // successful retry can finish the same durable run without stale failure counters.
        jdbcTemplate.update(
                """
                with retried as (
                    update raw_video_enrichment_provider_runs
                    set status = 'PENDING', retryable = false,
                        error_code = null, error_message = null,
                        completed_at = null, dispatched_at = now(), updated_at = now()
                    where raw_video_id = :rawVideoId
                      and pipeline_version = :pipelineVersion
                      and provider_type in (:providers)
                      and status = 'FAILED' and retryable = true
                    returning provider_type
                )
                update raw_video_pipeline_runs
                set status = 'PENDING',
                    completed_count = greatest(0, completed_count - (select count(*) from retried)),
                    failure_count = greatest(0, failure_count - (select count(*) from retried)),
                    retryable_failure_count = greatest(0, retryable_failure_count - (select count(*) from retried)),
                    updated_at = now()
                where raw_video_id = :rawVideoId and pipeline_version = :pipelineVersion
                  and step = :step and exists (select 1 from retried)
                """,
                parameters);

        List<String> pendingProviderNames = jdbcTemplate.queryForList(
                """
                select provider_type
                from raw_video_enrichment_provider_runs
                where raw_video_id = :rawVideoId
                  and pipeline_version = :pipelineVersion
                  and provider_type in (:providers)
                  and status = 'PENDING'
                """,
                parameters,
                String.class);
        return providers.stream().distinct()
                .filter(provider -> pendingProviderNames.contains(provider.name()))
                .toList();
    }

    public Optional<PendingProviderAttempt> findPendingProviderAttempt(
            UUID rawVideoId, String pipelineVersion, ProviderType providerType) {
        return jdbcTemplate.query(
                """
                select p.attempt_count, r.title, r.raw_metadata ->> 'title' as original_title,
                       coalesce(nullif(r.raw_metadata ->> 'totalEpisodes', ''), r.total_episodes) as total_episodes
                from raw_video_enrichment_provider_runs p
                join raw_videos r on r.id = p.raw_video_id
                where p.raw_video_id = :rawVideoId and p.pipeline_version = :pipelineVersion
                  and p.provider_type = :providerType and p.status = 'PENDING'
                  and coalesce(nullif(btrim(r.data_hash), ''), '_') = p.pipeline_version
                """,
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("pipelineVersion", normalizeVersion(pipelineVersion))
                        .addValue("providerType", providerType.name()),
                (rs, row) -> new PendingProviderAttempt(rs.getInt("attempt_count"),
                        MetadataSourceEvidence.from(rs.getString("original_title"), rs.getString("title"),
                                rs.getString("total_episodes")))).stream().findFirst();
    }

    @Transactional
    public ProviderCompletion completeProvider(
            UUID rawVideoId,
            String pipelineVersion,
            ProviderType providerType,
            boolean success,
            boolean retryable,
            String errorCode,
            String errorMessage,
            int expectedAttempt) {
        if (expectedAttempt < 0) {
            throw new IllegalArgumentException("provider attempt must be non-negative");
        }
        if (providerType == null) {
            return ProviderCompletion.untracked();
        }
        String version = normalizeVersion(pipelineVersion);
        String providerStatus = success ? STATUS_SUCCESS : retryable ? STATUS_FAILED : STATUS_PERMANENT_FAILURE;
        int changed = jdbcTemplate.update(
                """
                update raw_video_enrichment_provider_runs
                set status = :status,
                    retryable = :retryable,
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    attempt_count = attempt_count + 1,
                    completed_at = now(),
                    updated_at = now()
                where raw_video_id = :rawVideoId
                  and pipeline_version = :pipelineVersion
                  and provider_type = :providerType
                  and status = 'PENDING'
                  and attempt_count = :expectedAttempt
                """,
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("pipelineVersion", version)
                        .addValue("providerType", providerType.name())
                        .addValue("status", providerStatus)
                        .addValue("retryable", retryable)
                        .addValue("errorCode", errorCode)
                        .addValue("errorMessage", errorMessage)
                        .addValue("expectedAttempt", expectedAttempt));
        if (changed == 0) {
            return findCompletion(rawVideoId, version, false).orElse(ProviderCompletion.untracked());
        }

        int successDelta = success ? 1 : 0;
        int retryableFailureDelta = !success && retryable ? 1 : 0;
        int permanentFailureDelta = !success && !retryable ? 1 : 0;
        int failureDelta = retryableFailureDelta + permanentFailureDelta;
        List<ProviderCompletion> completions = jdbcTemplate.query(
                """
                update raw_video_pipeline_runs
                set completed_count = least(expected_count, completed_count + 1),
                    success_count = success_count + :successDelta,
                    failure_count = failure_count + :failureDelta,
                    retryable_failure_count = retryable_failure_count + :retryableFailureDelta,
                    permanent_failure_count = permanent_failure_count + :permanentFailureDelta,
                    updated_at = now()
                where raw_video_id = :rawVideoId
                  and pipeline_version = :pipelineVersion
                  and step = :step
                returning expected_count, completed_count, success_count, failure_count,
                          retryable_failure_count, permanent_failure_count
                """,
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("pipelineVersion", version)
                        .addValue("step", STEP)
                        .addValue("successDelta", successDelta)
                        .addValue("failureDelta", failureDelta)
                        .addValue("retryableFailureDelta", retryableFailureDelta)
                        .addValue("permanentFailureDelta", permanentFailureDelta),
                (rs, rowNum) -> ProviderCompletion.tracked(
                        true,
                        rs.getInt("expected_count"),
                        rs.getInt("completed_count"),
                        rs.getInt("success_count"),
                        rs.getInt("failure_count"),
                        rs.getInt("retryable_failure_count"),
                        rs.getInt("permanent_failure_count")));
        if (completions.isEmpty()) {
            return ProviderCompletion.untracked();
        }
        return completions.get(0);
    }

    public int markRunFinished(UUID rawVideoId, String pipelineVersion, String status) {
        return jdbcTemplate.update(
                """
                update raw_video_pipeline_runs
                set status = :status,
                    updated_at = now()
                where raw_video_id = :rawVideoId
                  and pipeline_version = :pipelineVersion
                  and step = :step
                """,
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("pipelineVersion", normalizeVersion(pipelineVersion))
                        .addValue("step", STEP)
                        .addValue("status", status));
    }

    private Optional<ProviderCompletion> findCompletion(
            UUID rawVideoId,
            String pipelineVersion,
            boolean newlyCompleted) {
        return findCompletion(rawVideoId, pipelineVersion, newlyCompleted, false);
    }

    public Optional<ProviderCompletion> findCompletionForUpdate(UUID rawVideoId, String pipelineVersion) {
        return findCompletion(rawVideoId, normalizeVersion(pipelineVersion), false, true);
    }

    private Optional<ProviderCompletion> findCompletion(
            UUID rawVideoId, String pipelineVersion, boolean newlyCompleted, boolean forUpdate) {
        List<ProviderCompletion> completions = jdbcTemplate.query(
                """
                select expected_count, completed_count, success_count, failure_count,
                       retryable_failure_count, permanent_failure_count
                from raw_video_pipeline_runs
                where raw_video_id = :rawVideoId
                  and pipeline_version = :pipelineVersion
                  and step = :step
                """ + (forUpdate ? "\nfor update" : ""),
                new MapSqlParameterSource()
                        .addValue("rawVideoId", rawVideoId)
                        .addValue("pipelineVersion", pipelineVersion)
                        .addValue("step", STEP),
                (rs, rowNum) -> ProviderCompletion.tracked(
                        newlyCompleted,
                        rs.getInt("expected_count"),
                        rs.getInt("completed_count"),
                        rs.getInt("success_count"),
                        rs.getInt("failure_count"),
                        rs.getInt("retryable_failure_count"),
                        rs.getInt("permanent_failure_count")));
        return completions.stream().findFirst();
    }

    private static String normalizeVersion(String pipelineVersion) {
        return StringUtils.hasText(pipelineVersion) ? pipelineVersion.trim() : "_";
    }

    public record PendingProviderAttempt(int attempt, MetadataSourceEvidence evidence) {}

    public record ProviderCompletion(
            boolean tracked,
            boolean newlyCompleted,
            int expectedCount,
            int completedCount,
            int successCount,
            int failureCount,
            int retryableFailureCount,
            int permanentFailureCount) {

        public static ProviderCompletion tracked(
                boolean newlyCompleted,
                int expectedCount,
                int completedCount,
                int successCount,
                int failureCount,
                int retryableFailureCount,
                int permanentFailureCount) {
            return new ProviderCompletion(
                    true,
                    newlyCompleted,
                    expectedCount,
                    completedCount,
                    successCount,
                    failureCount,
                    retryableFailureCount,
                    permanentFailureCount);
        }

        public static ProviderCompletion untracked() {
            return new ProviderCompletion(false, false, 0, 0, 0, 0, 0, 0);
        }

        public boolean allCompleted() {
            return tracked && expectedCount > 0 && completedCount >= expectedCount;
        }
    }
}
