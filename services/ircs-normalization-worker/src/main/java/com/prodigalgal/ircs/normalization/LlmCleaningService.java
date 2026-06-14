package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.worker.WorkerInstanceIds;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
class LlmCleaningService {

    private final NormalizationConfigValues configValues;
    private final LlmCleaningRepository repository;
    private final FakeLlmCleaningClient fakeClient;
    private final OpenAiCompatibleLlmCleaningClient liveClient;
    private final LlmCredentialResolver credentialResolver;
    private final String workerId;
    private final Duration claimTtl;
    LlmCleaningService(
            NormalizationConfigValues configValues,
            LlmCleaningRepository repository,
            FakeLlmCleaningClient fakeClient,
            OpenAiCompatibleLlmCleaningClient liveClient,
            LlmCredentialResolver credentialResolver,
            @Value("${spring.application.name:ircs-normalization-worker}") String applicationName,
            @Value("${app.normalization.llm-cleaning.worker-id:${APP_NORMALIZATION_LLM_CLEANING_WORKER_ID:}}")
                    String configuredWorkerId,
            @Value("${app.normalization.llm-cleaning.claim-ttl:PT10M}") Duration claimTtl) {
        this.configValues = configValues;
        this.repository = repository;
        this.fakeClient = fakeClient;
        this.liveClient = liveClient;
        this.credentialResolver = credentialResolver;
        this.workerId = WorkerInstanceIds.resolve(applicationName, configuredWorkerId);
        this.claimTtl = claimTtl == null || !claimTtl.isPositive() ? Duration.ofMinutes(10) : claimTtl;
    }

    LlmCleaningResult cleanBatch(LlmCleaningKind kind) {
        if (!configValues.llmCleaningEnabled()) {
            return LlmCleaningResult.skipped(kind, "PROVIDER_DISABLED");
        }

        String mode = configValues.llmCleaningMode();
        if ("dry-run".equals(mode) || "dryrun".equals(mode)) {
            return LlmCleaningResult.skipped(kind, "DRY_RUN_SKIPPED");
        }

        int batchSize = Math.max(1, configValues.llmCleaningBatchSize());
        List<LlmCleaningCandidate> candidates = repository.claimCandidates(kind, batchSize, workerId, claimTtl);
        if (candidates.isEmpty()) {
            return new LlmCleaningResult(kind, "SUCCESS", 0, 0, 0, "NO_CANDIDATES");
        }
        List<UUID> claimedIds = candidates.stream().map(LlmCleaningCandidate::id).toList();

        List<LlmCleaningStandard> standards = repository.findStandards(kind);
        Map<String, LlmCleaningStandard> standardsByName = standards.stream()
                .filter(standard -> StringUtils.hasText(standard.name()))
                .collect(Collectors.toMap(
                        standard -> standard.name().trim(),
                        Function.identity(),
                        (left, right) -> left));
        Map<String, LlmCleaningCandidate> candidatesByRaw = candidates.stream()
                .filter(candidate -> StringUtils.hasText(candidate.rawValue()))
                .collect(Collectors.toMap(
                        candidate -> candidate.rawValue().trim(),
                        Function.identity(),
                        (left, right) -> left));

        try {
            LlmCleaningClient client = resolveClient(mode);
            LlmCredential credential = credential(mode).orElse(null);
            if ("live".equals(mode) && credential == null) {
                repository.releaseClaims(kind, claimedIds, workerId);
                return LlmCleaningResult.skipped(kind, "CREDENTIAL_MISSING");
            }
            List<LlmCleaningDecision> decisions = client.analyzeAndMap(
                    candidates.stream().map(LlmCleaningCandidate::rawValue).toList(),
                    standardsByName.keySet(),
                    kind,
                    credential);
            LlmCleaningResult result = applyDecisions(kind, candidates.size(), candidatesByRaw, standardsByName, decisions);
            repository.releaseClaims(kind, claimedIds, workerId);
            return result;
        } catch (LlmCleaningException.ProviderTimeout ex) {
            log.warn("LLM cleaning {} skipped by provider timeout: {}", kind, ex.getMessage());
            safeReleaseClaims(kind, claimedIds);
            return LlmCleaningResult.skipped(kind, "PROVIDER_TIMEOUT");
        } catch (LlmCleaningException.ProviderError | IllegalArgumentException ex) {
            log.warn("LLM cleaning {} skipped by provider error: {}", kind, ex.getMessage());
            safeReleaseClaims(kind, claimedIds);
            return LlmCleaningResult.skipped(kind, "PROVIDER_ERROR");
        }
    }

    private void safeReleaseClaims(LlmCleaningKind kind, List<UUID> claimedIds) {
        try {
            repository.releaseClaims(kind, claimedIds, workerId);
        } catch (RuntimeException releaseError) {
            log.warn("LLM cleaning {} failed to release {} claims: {}",
                    kind,
                    claimedIds == null ? 0 : claimedIds.size(),
                    releaseError.getMessage());
        }
    }

    private LlmCleaningClient resolveClient(String mode) {
        return switch (mode) {
            case "fake" -> fakeClient;
            case "live" -> liveClient;
            default -> throw new IllegalArgumentException("Unsupported LLM cleaning mode: " + mode);
        };
    }

    private Optional<LlmCredential> credential(String mode) {
        if (!"live".equals(mode)) {
            return Optional.of(new LlmCredential(null, null, configValues.llmModel(), "fake"));
        }
        return credentialResolver.resolve();
    }

    private LlmCleaningResult applyDecisions(
            LlmCleaningKind kind,
            int candidates,
            Map<String, LlmCleaningCandidate> candidatesByRaw,
            Map<String, LlmCleaningStandard> standardsByName,
            List<LlmCleaningDecision> decisions) {
        int mapped = 0;
        int noise = 0;
        for (LlmCleaningDecision decision : decisions) {
            LlmCleaningCandidate candidate = resolveCandidate(candidatesByRaw, decision.raw());
            if (candidate == null) {
                continue;
            }
            if (decision.noise()) {
                if (repository.applyNoise(kind, candidate.id())) {
                    noise++;
                }
                continue;
            }
            if (!StringUtils.hasText(decision.standard())) {
                continue;
            }
            LlmCleaningStandard standard = standardsByName.get(decision.standard().trim());
            if (standard == null) {
                continue;
            }
            if (repository.applyMatch(kind, candidate.id(), standard.id())) {
                mapped++;
            }
        }
        return new LlmCleaningResult(kind, "SUCCESS", candidates, mapped, noise, "CLEANED");
    }

    private LlmCleaningCandidate resolveCandidate(Map<String, LlmCleaningCandidate> candidatesByRaw, String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        LlmCleaningCandidate exact = candidatesByRaw.get(raw);
        if (exact != null) {
            return exact;
        }
        return candidatesByRaw.get(raw.trim());
    }

    record LlmCleaningResult(
            LlmCleaningKind kind,
            String status,
            int candidates,
            int mapped,
            int noise,
            String reason) {

        static LlmCleaningResult skipped(LlmCleaningKind kind, String reason) {
            return new LlmCleaningResult(kind, "SKIPPED", 0, 0, 0, reason);
        }
    }
}
