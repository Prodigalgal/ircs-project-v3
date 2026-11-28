package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.search.SearchSyncWorkPublisher;
import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import com.prodigalgal.ircs.contracts.search.SyncOperation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MagnetQueryService {

    private final JdbcMagnetRepository magnetRepository;
    private final MagnetReadModelCache readModelCache;
    private final SearchSyncWorkPublisher searchSyncWorkPublisher;
    private final MagnetSearchExecutor searchExecutor;
    private final MagnetTrackerConfigValues trackerConfig;

    public MagnetQueryService(JdbcMagnetRepository magnetRepository, MagnetReadModelCache readModelCache,
            ObjectProvider<SearchSyncWorkPublisher> searchSyncWorkPublisherProvider, MagnetSearchExecutor searchExecutor,
            MagnetTrackerConfigValues trackerConfig) {
        this.magnetRepository = magnetRepository;
        this.readModelCache = readModelCache;
        this.searchSyncWorkPublisher = searchSyncWorkPublisherProvider == null
                ? null : searchSyncWorkPublisherProvider.getIfAvailable();
        this.searchExecutor = searchExecutor;
        this.trackerConfig = trackerConfig;
    }

    public List<MagnetProviderSummary> listProviders() {
        return readModelCache.listProviders(magnetRepository::listProviders);
    }

    public Optional<MagnetProviderSummary> findProvider(UUID id) {
        return readModelCache.findProvider(id, () -> magnetRepository.findProvider(id));
    }

    @Transactional
    public MagnetProviderSummary createProvider(MagnetProviderRequest request) {
        MagnetProviderRequest normalized = normalizeProviderRequest(request);
        if (normalized.id() != null) {
            throw badRequest("A new magnet provider cannot already have an ID");
        }
        try {
            MagnetProviderSummary result = magnetRepository.createProvider(normalized);
            readModelCache.evictProviders();
            return result;
        } catch (DataIntegrityViolationException ex) {
            throw conflict("Magnet provider code already exists", ex);
        }
    }

    @Transactional
    public MagnetProviderSummary updateProvider(UUID id, MagnetProviderRequest request) {
        MagnetProviderRequest required = requireProviderRequest(request);
        validatePathId(id, required.id());
        MagnetProviderRequest normalized = normalizeProviderRequest(required);
        try {
            MagnetProviderSummary result = magnetRepository.updateProvider(id, normalized)
                    .orElseThrow(() -> notFound("MagnetProvider not found"));
            readModelCache.evictProvider(id);
            return result;
        } catch (DataIntegrityViolationException ex) {
            throw badRequest("Invalid magnet provider payload", ex);
        }
    }

    public List<MagnetLinkSummary> findApprovedLinks(UUID unifiedVideoId) {
        List<MagnetLinkSummary> links = readModelCache.findApprovedLinks(
                unifiedVideoId, () -> magnetRepository.findApprovedLinks(unifiedVideoId));
        if (links.isEmpty()) {
            return links;
        }
        List<String> trackers = trackerConfig.trackers();
        if (trackers.isEmpty()) {
            return links;
        }
        return links.stream()
                .map(link -> link.withMagnetUri(MagnetUriUtils.ensureTrackers(link.magnetUri(), trackers)))
                .toList();
    }

    public List<MagnetLinkSummary> findLinks(UUID unifiedVideoId) {
        ensureUnifiedVideoExists(unifiedVideoId);
        return magnetRepository.findLinks(unifiedVideoId);
    }

    /** One page of links in a given status, so the review queue can actually be enumerated. */
    public List<MagnetLinkSummary> findLinksByStatus(String status, int limit, int offset) {
        return magnetRepository.findLinksByStatus(status, limit, offset);
    }

    public long countLinksByStatus(String status) {
        return magnetRepository.countLinksByStatus(status);
    }

    public List<MagnetSearchJobSummary> findSearchJobs(UUID unifiedVideoId, int limit) {
        ensureUnifiedVideoExists(unifiedVideoId);
        return magnetRepository.findSearchJobs(unifiedVideoId, limit);
    }

    public List<MagnetProviderRunSummary> findProviderRuns(UUID jobId) {
        return magnetRepository.findProviderRuns(jobId);
    }

    public MagnetSearchJobSummary triggerUnifiedSearch(UUID unifiedVideoId) {
        return searchExecutor.searchNow(unifiedVideoId, "ADMIN_MANUAL");
    }

    public MagnetSearchJobSummary triggerAutomaticSearch(UUID unifiedVideoId) {
        return searchExecutor.searchNow(unifiedVideoId, "AUTO_SCHEDULED");
    }

    public MagnetSearchJobSummary enqueueUnifiedSearch(UUID unifiedVideoId) {
        return searchExecutor.enqueue(unifiedVideoId, "ADMIN_MANUAL");
    }

    public MagnetSearchJobSummary enqueueAutomaticSearch(UUID unifiedVideoId) {
        return searchExecutor.enqueue(unifiedVideoId, "AUTO_SCHEDULED");
    }

    public MagnetSearchJobSummary executeQueuedSearch(UUID jobId, UUID unifiedVideoId, String triggerType) {
        return executeQueuedSearch(jobId, unifiedVideoId, triggerType, true);
    }

    public MagnetSearchJobSummary executeQueuedSearch(UUID jobId, UUID unifiedVideoId, String triggerType,
            boolean retryAllowed) {
        return searchExecutor.execute(jobId, unifiedVideoId, triggerType, retryAllowed);
    }

    @Transactional
    public void markQueuedSearchFailed(UUID jobId, String reason) {
        magnetRepository.markSearchJobFailed(jobId, reason);
    }

    @Transactional
    public MagnetLinkSummary updateLinkStatus(UUID unifiedVideoId, UUID linkId, MagnetLinkStatusRequest request) {
        ensureUnifiedVideoExists(unifiedVideoId);
        String status = normalizeLinkStatus(request == null ? null : request.status());
        MagnetLinkSummary link = magnetRepository.updateLinkStatus(unifiedVideoId, linkId, status)
                .orElseThrow(() -> notFound("Magnet link not found"));
        publishLinkChangeAfterCommit(unifiedVideoId);
        return link;
    }

    @Transactional
    public void deleteLink(UUID unifiedVideoId, UUID linkId) {
        ensureUnifiedVideoExists(unifiedVideoId);
        if (!magnetRepository.deleteLink(unifiedVideoId, linkId)) {
            throw notFound("Magnet link not found");
        }
        publishLinkChangeAfterCommit(unifiedVideoId);
    }

    private void publishLinkChangeAfterCommit(UUID unifiedVideoId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishLinkChange(unifiedVideoId);
                }
            });
            return;
        }
        publishLinkChange(unifiedVideoId);
    }

    private void publishLinkChange(UUID unifiedVideoId) {
        readModelCache.evictApprovedLinks(unifiedVideoId);
        enqueueSearchSync(unifiedVideoId);
    }

    private MagnetProviderRequest normalizeProviderRequest(MagnetProviderRequest request) {
        MagnetProviderRequest required = requireProviderRequest(request);
        String code = requireText(required.code(), "Provider code is required");
        String name = requireText(required.name(), "Provider name is required");
        String providerType = requireText(required.providerType(), "Provider type is required");
        String baseUrl = requireText(required.baseUrl(), "Provider baseUrl is required");
        Integer minDelayMs = defaultNonNegative(required.minDelayMs(), 1000, "minDelayMs");
        Integer maxDelayMs = defaultNonNegative(required.maxDelayMs(), 3000, "maxDelayMs");
        if (maxDelayMs < minDelayMs) {
            throw badRequest("maxDelayMs must be greater than or equal to minDelayMs");
        }

        return new MagnetProviderRequest(
                required.id(),
                code,
                name,
                providerType,
                baseUrl,
                defaultBoolean(required.enabled(), true),
                defaultNonNegative(required.priority(), 100, "priority"),
                trimToDefault(required.riskLevel(), "HIGH"),
                normalizeStringList(required.supportedExternalIds()),
                minDelayMs,
                maxDelayMs,
                defaultPositive(required.timeoutMs(), 10000, "timeoutMs"),
                defaultPositive(required.resultLimit(), 20, "resultLimit"),
                defaultBoolean(required.autoApproveAllowed(), false),
                trimToNull(required.contentPolicy()),
                required.lastHealthCheckAt(),
                trimToNull(required.lastHealthStatus()),
                trimToNull(required.lastErrorMessage()),
                required.createdAt(),
                required.updatedAt());
    }

    private MagnetProviderRequest requireProviderRequest(MagnetProviderRequest request) {
        if (request == null) {
            throw badRequest("Magnet provider payload is required");
        }
        return request;
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private String normalizeLinkStatus(String value) {
        String normalized = requireText(value, "Magnet link status is required").toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "CANDIDATE", "REJECTED", "HIDDEN" -> normalized;
            default -> throw badRequest("Unsupported magnet link status");
        };
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw badRequest(message);
        }
        return normalized;
    }

    private String trimToDefault(String value, String defaultValue) {
        String normalized = trimToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer defaultNonNegative(Integer value, int defaultValue, String field) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 0) {
            throw badRequest(field + " must not be negative");
        }
        return normalized;
    }

    private Integer defaultPositive(Integer value, int defaultValue, String field) {
        int normalized = value == null ? defaultValue : value;
        if (normalized <= 0) {
            throw badRequest(field + " must be positive");
        }
        return normalized;
    }

    private void validatePathId(UUID pathId, UUID bodyId) {
        if (bodyId != null && !Objects.equals(pathId, bodyId)) {
            throw badRequest("Invalid ID");
        }
    }

    private void ensureUnifiedVideoExists(UUID unifiedVideoId) {
        if (!magnetRepository.existsUnifiedVideo(unifiedVideoId)) {
            throw notFound("UnifiedVideo not found");
        }
    }

    private void enqueueSearchSync(UUID unifiedVideoId) {
        if (searchSyncWorkPublisher != null && unifiedVideoId != null) {
            searchSyncWorkPublisher.enqueue(unifiedVideoId, SearchEntityType.UNIFIED_VIDEO, SyncOperation.INDEX);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException badRequest(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message, cause);
    }

    private ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

}
