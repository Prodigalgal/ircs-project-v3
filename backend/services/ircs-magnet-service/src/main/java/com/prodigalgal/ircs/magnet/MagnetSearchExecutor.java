package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Network orchestration. Only the coordinator's bounded persistence callbacks open transactions. */
@Service
final class MagnetSearchExecutor {
    private final JdbcMagnetRepository repository;
    private final MagnetSearchJobCoordinator coordinator;
    private final MagnetProviderSearchRunner runner;
    private final MagnetReadModelCache cache;
    private final MagnetWorkPublisher publisher;
    private final SearchSyncWorkPublisher searchSync;
    private final Duration leaseDuration;

    MagnetSearchExecutor(JdbcMagnetRepository repository, MagnetSearchJobCoordinator coordinator,
            MagnetProviderSearchRunner runner, MagnetReadModelCache cache,
            ObjectProvider<MagnetWorkPublisher> publisher, ObjectProvider<SearchSyncWorkPublisher> searchSync,
            @Value("${app.magnet.execution.lease:PT2M}") Duration leaseDuration) {
        this.repository = repository;
        this.coordinator = coordinator;
        this.runner = runner;
        this.cache = cache;
        this.publisher = publisher == null ? null : publisher.getIfAvailable();
        this.searchSync = searchSync == null ? null : searchSync.getIfAvailable();
        this.leaseDuration = leaseDuration == null || leaseDuration.compareTo(Duration.ofSeconds(10)) < 0
                ? Duration.ofMinutes(2) : leaseDuration;
    }

    MagnetSearchJobSummary enqueue(UUID unifiedVideoId, String trigger) {
        requireNoTransaction();
        MagnetUnifiedVideoSearchTarget target = target(unifiedVideoId);
        List<MagnetExternalIdQuery> queries = queries(target);
        String skipped = skipReason(target, queries);
        if (skipped != null) {
            return repository.createSkippedSearchJob(unifiedVideoId, trigger, skipped, queries, List.of());
        }
        if (publisher == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Magnet runtime work queue is unavailable");
        }
        MagnetSearchJobCoordinator.Enqueued result = coordinator.enqueue(unifiedVideoId, trigger, queries);
        // A retry of the same API call also repairs a previous commit/publish gap, with the same queue task ID.
        publisher.enqueue(result.job());
        return result.job();
    }

    MagnetSearchJobSummary searchNow(UUID unifiedVideoId, String trigger) {
        requireNoTransaction();
        MagnetUnifiedVideoSearchTarget target = target(unifiedVideoId);
        List<MagnetExternalIdQuery> queries = queries(target);
        String skipped = skipReason(target, queries);
        if (skipped != null) {
            return repository.createSkippedSearchJob(unifiedVideoId, trigger, skipped, queries, List.of());
        }
        MagnetSearchJobSummary job = coordinator.enqueue(unifiedVideoId, trigger, queries).job();
        return execute(job.id(), unifiedVideoId, trigger, false);
    }

    MagnetSearchJobSummary execute(UUID jobId, UUID unifiedVideoId, String trigger, boolean retryAllowed) {
        requireNoTransaction();
        MagnetQueueClaimGuard.checkCurrentOwnership();
        MagnetUnifiedVideoSearchTarget target = target(unifiedVideoId);
        List<MagnetExternalIdQuery> queries = queries(target);
        MagnetSearchJobCoordinator.Claim claim = coordinator.claim(jobId, unifiedVideoId, queries, leaseDuration);
        if (!claim.acquired()) {
            if ("FAILED".equals(claim.job().status()) || "PARTIAL_SUCCESS".equals(claim.job().status())) {
                throw new MagnetSearchExecutionException(false, "MAGNET_SEARCH_ALREADY_FINISHED_WITH_FAILURE");
            }
            return claim.job();
        }
        var lease = new MagnetSearchJobCoordinator.Lease(jobId, unifiedVideoId, claim.generation(), leaseDuration);
        try (var guard = new MagnetJobExecutionGuard(coordinator, lease)) {
            List<MagnetProviderSummary> providers = cache.listEnabledProviders(repository::listEnabledProviders);
            List<String> providerCodes = providers.stream().map(MagnetProviderSummary::code).toList();
            String skipped = skipReason(target, queries);
            if (skipped == null && providers.isEmpty()) { skipped = "没有已启用的 magnet provider"; }
            List<ProviderQuery> plan = plan(target, providers, queries);
            if (skipped == null && plan.isEmpty()) { skipped = "没有支持当前查询类型的 magnet provider"; }
            if (skipped != null) {
                String reason = skipped;
                return coordinator.withinLease(lease, () -> {
                    guard.checkOwnership();
                    MagnetSearchJobSummary result = repository.markSearchJobSkipped(jobId, reason, queries, providerCodes)
                            .orElseThrow();
                    guard.checkOwnership();
                    return result;
                });
            }
            Map<String, MagnetProviderRunSummary> outcomes = latestRuns(jobId);
            List<MagnetLinkSummary> links = new ArrayList<>();
            Map<String, Boolean> blockedProviders = new LinkedHashMap<>();
            for (ProviderQuery step : plan) {
                guard.checkOwnership();
                MagnetProviderRunSummary previous = outcomes.get(step.key());
                if (previous != null && ("SUCCESS".equals(previous.status()) || !retryable(previous))) { continue; }
                if (blockedProviders.containsKey(step.provider().code())) { continue; }
                Instant started = Instant.now();
                MagnetProviderSearchResult result;
                try {
                    result = runner.search(step.provider(), step.query(), unifiedVideoId);
                    if (result == null || result.candidates() == null) {
                        throw new MagnetProviderRunnerException("MAGNET_EMPTY_PROVIDER_RESPONSE", null, null);
                    }
                } catch (MagnetProviderRunnerException failure) {
                    guard.checkOwnership();
                    coordinator.withinLease(lease, () -> {
                        guard.checkOwnership();
                        repository.createProviderRun(jobId, step.provider(), step.query(), "FAILED", failure.requestUrl(),
                                failure.httpStatus(), 0, 0, elapsed(started), failure.failureType());
                        repository.updateProviderHealth(step.provider().id(), "FAILED", failure.failureType());
                        guard.checkOwnership();
                        return null;
                    });
                    // Rate limits and provider outages apply to every alternate query for this source.
                    blockedProviders.put(step.provider().code(), true);
                    continue;
                }
                guard.checkOwnership();
                MagnetProviderSearchResult response = result;
                List<MagnetLinkSummary> saved = coordinator.withinLease(lease, () -> {
                    guard.checkOwnership();
                    if (!target.equals(repository.findUnifiedVideoSearchTarget(unifiedVideoId).orElse(null))) {
                        throw new MagnetSearchJobCoordinator.MagnetJobBusyException("MAGNET_TARGET_CHANGED_DURING_SEARCH");
                    }
                    List<MagnetLinkSummary> accepted = persistCandidates(target, step.provider(), response.candidates());
                    repository.createProviderRun(jobId, step.provider(), step.query(), "SUCCESS", response.requestUrl(),
                            response.httpStatus(), response.candidates().size(), accepted.size(), elapsed(started), null);
                    repository.updateProviderHealth(step.provider().id(), "SUCCESS", null);
                    guard.checkOwnership();
                    return accepted;
                });
                links.addAll(saved);
                if (!saved.isEmpty()) {
                    cache.evictApprovedLinks(unifiedVideoId);
                    if (searchSync != null) { searchSync.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX); }
                }
            }
            guard.checkOwnership();
            Map<String, MagnetProviderRunSummary> finalRuns = latestRuns(jobId);
            List<MagnetProviderRunSummary> relevant = plan.stream().map(step -> finalRuns.get(step.key()))
                    .filter(Objects::nonNull).toList();
            int total = relevant.stream().mapToInt(run -> safeCount(run.candidateCount())).sum();
            int accepted = relevant.stream().mapToInt(run -> safeCount(run.acceptedCount())).sum();
            int failed = (int) relevant.stream().filter(run -> "FAILED".equals(run.status())).count();
            boolean retryableFailure = relevant.stream().anyMatch(MagnetSearchExecutor::retryable);
            if (retryableFailure && retryAllowed) {
                guard.checkOwnership();
                coordinator.retry(lease, "MAGNET_PROVIDER_RETRY_PENDING");
                throw new MagnetSearchExecutionException(true, "MAGNET_PROVIDER_RETRY_PENDING");
            }
            MagnetSearchJobSummary finished = coordinator.withinLease(lease, () -> {
                guard.checkOwnership();
                return repository.finishSearchJob(jobId, providerCodes, total, accepted, failed);
            });
            if (failed > 0) {
                throw new MagnetSearchExecutionException(false,
                        retryableFailure ? "MAGNET_PROVIDER_RETRIES_EXHAUSTED" : "MAGNET_PROVIDER_PERMANENT_FAILURE");
            }
            return withLinks(finished, links);
        } catch (MagnetSearchExecutionException | MagnetSearchJobCoordinator.MagnetLeaseLostException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // A cache, index-publish or persistence failure must not strand a valid database lease.
            MagnetQueueClaimGuard.checkCurrentOwnership();
            try {
                if (retryAllowed) { coordinator.retry(lease, "MAGNET_EXECUTION_RETRY_PENDING"); }
                else { coordinator.fail(lease, "MAGNET_EXECUTION_RETRIES_EXHAUSTED"); }
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }

    private List<MagnetLinkSummary> persistCandidates(MagnetUnifiedVideoSearchTarget target,
            MagnetProviderSummary provider, List<MagnetProviderCandidate> candidates) {
        List<MagnetLinkSummary> links = new ArrayList<>();
        for (MagnetProviderCandidate candidate : candidates) {
            if (candidate == null || candidate.title() == null || candidate.title().isBlank()) { continue; }
            String hash = MagnetUriUtils.normalizeInfoHash(candidate.infoHash()).orElse(null);
            String uriHash = MagnetUriUtils.normalizeInfoHash(candidate.magnetUri()).orElse(null);
            if (hash == null || !hash.equals(uriHash)) { continue; }
            var identity = new MagnetCandidateIdentityPolicy.Target(target.title(), target.subtitle(), target.aliasTitle(),
                    year(target.year()), target.season(), target.categoryCode(), target.imdbId(), target.tmdbId(), target.doubanId());
            var decision = MagnetCandidateIdentityPolicy.evaluate(identity, candidate);
            if (decision.disposition() == MagnetCandidateIdentityPolicy.Disposition.REJECT) { continue; }
            Map<String, Object> evidence = new LinkedHashMap<>();
            if (candidate.providerEvidence() != null) { evidence.putAll(candidate.providerEvidence()); }
            evidence.put("identityPolicy", "v1");
            evidence.put("identityDisposition", decision.disposition().name());
            evidence.put("identityReason", decision.reason());
            MagnetProviderCandidate verified = new MagnetProviderCandidate(hash, candidate.magnetUri(), candidate.title(),
                    candidate.sizeBytes(), candidate.sizeLabel(), candidate.publishedAt(), candidate.seeders(), candidate.leechers(),
                    candidate.quality(), candidate.resolution(), candidate.matchedExternalIdType(), candidate.matchedExternalIdValue(),
                    decision.score(), candidate.sourceUrl(), candidate.tags(), evidence);
            links.add(repository.upsertLink(target.id(), provider, verified,
                    decision.disposition() == MagnetCandidateIdentityPolicy.Disposition.AUTO_APPROVE));
        }
        return links;
    }

    private Map<String, MagnetProviderRunSummary> latestRuns(UUID jobId) {
        Map<String, MagnetProviderRunSummary> result = new LinkedHashMap<>();
        repository.findProviderRuns(jobId).stream().sorted(Comparator.comparing(MagnetProviderRunSummary::createdAt)
                .thenComparing(MagnetProviderRunSummary::id)).forEach(run -> result.put(key(run.providerCode(),
                        run.externalIdType(), run.externalIdValue()), run));
        return result;
    }

    static boolean retryable(MagnetProviderRunSummary run) {
        if (!"FAILED".equals(run.status())) { return false; }
        Integer status = run.httpStatus();
        if (status != null && (status == 429 || status == 408 || status >= 500)) { return true; }
        if (status != null && status >= 400) { return false; }
        String code = run.errorMessage() == null ? "" : run.errorMessage().toUpperCase(Locale.ROOT);
        return code.endsWith("TIMEOUT") || code.endsWith("HTTP_ERROR") || code.equals(MagnetProviderTrafficLimiter.FAILURE_TYPE);
    }

    private List<ProviderQuery> plan(MagnetUnifiedVideoSearchTarget target, List<MagnetProviderSummary> providers,
            List<MagnetExternalIdQuery> queries) {
        List<ProviderQuery> result = new ArrayList<>();
        for (MagnetProviderSummary provider : providers) {
            if (!Boolean.TRUE.equals(provider.enabled()) || provider.supportedExternalIds() == null) { continue; }
            String type = provider.providerType() == null ? "" : provider.providerType().toUpperCase(Locale.ROOT);
            String category = target.categoryCode() == null ? "" : target.categoryCode().toLowerCase(Locale.ROOT);
            if (type.equals("YTS_BZ") && !category.equals("movie")) { continue; }
            for (MagnetExternalIdQuery query : queries.stream().filter(query -> provider.supportedExternalIds().stream()
                    .anyMatch(supported -> query.type().equalsIgnoreCase(supported))).limit(3).toList()) {
                result.add(new ProviderQuery(provider, query));
            }
        }
        return result;
    }

    static List<MagnetExternalIdQuery> queries(MagnetUnifiedVideoSearchTarget target) {
        List<MagnetExternalIdQuery> result = new ArrayList<>();
        String imdb = text(target.imdbId()).toLowerCase(Locale.ROOT);
        if (imdb.matches("tt[0-9]{7,10}") && !imdb.substring(2).matches("0+")) { add(result, "IMDB", imdb); }
        if (text(target.tmdbId()).matches("[1-9][0-9]{0,11}")) { add(result, "TMDB", target.tmdbId()); }
        if (text(target.doubanId()).matches("[1-9][0-9]{0,11}")) { add(result, "DOUBAN", target.doubanId()); }
        List<String> names = new ArrayList<>();
        if (!text(target.title()).isBlank()) {
            String title = target.title().trim();
            if (!text(target.subtitle()).isBlank()) { title += " " + target.subtitle().trim(); }
            if (target.season() != null && target.season() > 0) { title += " S%02d".formatted(target.season()); }
            names.add(title);
        }
        for (String alias : text(target.aliasTitle()).split("[|/;；\\n]")) {
            if (!alias.isBlank() && names.size() < 4) { names.add(alias.trim()); }
        }
        for (String name : names) {
            if (year(target.year()) != null) { add(result, "TITLE_YEAR", name + " " + target.year().trim()); }
            add(result, "TITLE", name);
        }
        return result;
    }

    private static void add(List<MagnetExternalIdQuery> queries, String type, String value) {
        if (queries.stream().noneMatch(query -> query.type().equals(type) && query.value().equalsIgnoreCase(value))) {
            queries.add(new MagnetExternalIdQuery(type, value));
        }
    }

    private MagnetUnifiedVideoSearchTarget target(UUID id) {
        return repository.findUnifiedVideoSearchTarget(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UnifiedVideo not found"));
    }

    private String skipReason(MagnetUnifiedVideoSearchTarget target, List<MagnetExternalIdQuery> queries) {
        if (!"SYNCED".equalsIgnoreCase(target.metadataStatus())) { return "聚合视频富化未完成，暂不补充磁链"; }
        return queries.isEmpty() ? "没有可用于搜刮的外部 ID 或标题" : null;
    }

    private static Integer year(String raw) {
        return text(raw).matches("(?:18|19|20)[0-9]{2}") ? Integer.valueOf(raw.trim()) : null;
    }
    private static String text(String raw) { return raw == null ? "" : raw.trim(); }
    private static int safeCount(Integer count) { return count == null ? 0 : Math.max(0, count); }
    private static long elapsed(Instant started) { return Duration.between(started, Instant.now()).toMillis(); }
    private static String key(String provider, String type, String value) { return provider + "\n" + type + "\n" + value; }
    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Magnet provider orchestration must run outside a database transaction");
        }
    }
    private static MagnetSearchJobSummary withLinks(MagnetSearchJobSummary job, List<MagnetLinkSummary> links) {
        return new MagnetSearchJobSummary(job.id(), job.unifiedVideoId(), job.triggerType(), job.status(), job.providerCodes(),
                job.externalIdPlan(), job.startedAt(), job.finishedAt(), job.totalCandidates(), job.acceptedCount(), job.rejectedCount(),
                job.skippedReason(), job.errorMessage(), List.copyOf(links), job.createdAt(), job.updatedAt());
    }
    private record ProviderQuery(MagnetProviderSummary provider, MagnetExternalIdQuery query) {
        String key() { return MagnetSearchExecutor.key(provider.code(), query.type(), query.value()); }
    }
}
