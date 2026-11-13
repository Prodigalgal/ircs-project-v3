package com.prodigalgal.ircs.aggregation;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import com.prodigalgal.ircs.common.adult.AdultAssessment;
import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
import com.prodigalgal.ircs.common.adult.AdultAssessmentLevel;
import com.prodigalgal.ircs.common.adult.AdultAssessmentSignal;
import com.prodigalgal.ircs.common.adult.AdultContentAssessor;
import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentBatchRequest;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentBatchResponse;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentResult;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentSignalDto;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentSourceEvidence;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
class ContentSafetyAdultAssessmentClient {

    private static final String BATCH_PATH = "/internal/v1/content-safety/adult-assessments:batch";
    private static final int REMOTE_TEXT_LIMIT = 512;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String baseUrl;
    private final String serviceId;
    private final String serviceToken;
    private final String serviceScopes;
    private final Duration requestTimeout;

    ContentSafetyAdultAssessmentClient(
            ObjectMapper objectMapper,
            @Value("${app.aggregation.content-safety.enabled:true}") boolean enabled,
            @Value("${app.aggregation.content-safety.base-url:http://ircs-content-safety-service:8080}") String baseUrl,
            @Value("${app.aggregation.content-safety.service-id:${spring.application.name:ircs-aggregation-worker}}")
                    String serviceId,
            @Value("${app.aggregation.content-safety.service-token:${APP_CONTENT_SAFETY_SERVICE_TOKEN:}}")
                    String serviceToken,
            @Value("${app.aggregation.content-safety.scopes:content-safety:assess}") String serviceScopes,
            @Value("${app.aggregation.content-safety.request-timeout:PT30S}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.serviceId = serviceId;
        this.serviceToken = serviceToken;
        this.serviceScopes = serviceScopes;
        this.requestTimeout = normalizeTimeout(requestTimeout);
    }

    Map<UUID, AdultAssessment> assess(Map<UUID, AdultAssessmentInput> inputs) {
        if (!enabled || inputs == null || inputs.isEmpty() || !StringUtils.hasText(baseUrl)) {
            return Map.of();
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Content safety network calls require a released aggregation transaction");
        }
        try {
            AdultAssessmentBatchRequest request = new AdultAssessmentBatchRequest(inputs.entrySet().stream()
                    .map(entry -> toItem(entry.getKey(), entry.getValue()))
                    .toList());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + BATCH_PATH))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header(InternalServiceAuthHeaders.SERVICE_ID, safe(serviceId))
                    .header(InternalServiceAuthHeaders.SERVICE_TOKEN, safe(serviceToken))
                    .header(InternalServiceAuthHeaders.SERVICE_SCOPES, safe(serviceScopes))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(requestTimeout)
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("content-safety adult assessment failed: upstream status={}", response.statusCode());
                throw new AdultAssessmentDeferredException("CONTENT_SAFETY_HTTP_" + response.statusCode(), 60);
            }
            AdultAssessmentBatchResponse body =
                    objectMapper.readValue(response.body(), AdultAssessmentBatchResponse.class);
            Map<UUID, AdultAssessment> results = new LinkedHashMap<>();
            AdultAssessmentDeferredException deferred = null;
            if (body == null) {
                throw new AdultAssessmentDeferredException("CONTENT_SAFETY_INCOMPLETE", 60);
            }
            for (AdultAssessmentResult result : body.items()) {
                if (result == null || result.id() == null || !inputs.containsKey(result.id())) {
                    throw new AdultAssessmentDeferredException("CONTENT_SAFETY_INCOMPLETE", 60);
                }
                boolean definiteAdult = result.adultRestricted() || "ADULT".equalsIgnoreCase(result.level());
                if (!definiteAdult && result.model() != null && !result.model().available()
                        && "GOOGLE_DECISIONS".equals(result.model().raw().get("provider"))
                        && Boolean.TRUE.equals(result.model().raw().get("retryable"))) {
                    Object rawDelay = result.model().raw().get("retryAfterSeconds");
                    long seconds = rawDelay instanceof Number number ? number.longValue() : 60;
                    Object rawReason = result.model().raw().get("reason");
                    String reason = rawReason instanceof String value
                            && AdultAssessmentDeferredException.isWaitingReason(value) ? value : "GOOGLE_NSFW_PENDING";
                    var next = new AdultAssessmentDeferredException(reason, seconds);
                    if (deferred == null || (deferred.waiting() && !next.waiting())
                            || (deferred.waiting() == next.waiting()
                                    && next.retryAfter().compareTo(deferred.retryAfter()) > 0)) {
                        deferred = next;
                    }
                    continue;
                }
                if (result.id() != null) {
                    results.put(result.id(), toAssessment(result));
                }
            }
            if (deferred != null) { throw deferred; }
            if (!results.keySet().containsAll(inputs.keySet())) {
                throw new AdultAssessmentDeferredException("CONTENT_SAFETY_INCOMPLETE", 60);
            }
            return results;
        } catch (IOException | InterruptedException | IllegalArgumentException | JacksonException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("content-safety adult assessment unavailable: {}", ex.getClass().getSimpleName());
            throw new AdultAssessmentDeferredException("CONTENT_SAFETY_UNAVAILABLE", 60);
        }
    }

    private static AdultAssessmentItem toItem(UUID id, AdultAssessmentInput input) {
        if (input == null) {
            return new AdultAssessmentItem(
                    id,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        return new AdultAssessmentItem(
                id,
                truncate(input.title()),
                truncate(input.aliasTitle()),
                truncate(input.description()),
                truncate(input.remarks()),
                truncate(input.subtitle()),
                truncate(input.categoryCode()),
                truncate(input.categoryName()),
                truncateAll(input.actorNames()),
                truncateAll(input.directorNames()),
                truncateAll(input.genreCodes()),
                input.sources().stream()
                        .filter(Objects::nonNull)
                        .map(ContentSafetyAdultAssessmentClient::toSource)
                        .toList());
    }

    private static AdultAssessmentSourceEvidence toSource(AdultAssessmentInput.SourceEvidence source) {
        return new AdultAssessmentSourceEvidence(
                truncate(source.dataSourceName()),
                source.dataSourceAdultRestricted(),
                truncate(source.sourceCategoryCode()),
                truncate(source.sourceCategoryName()),
                truncate(source.sourceDomain()),
                null);
    }

    private static List<String> truncateAll(Iterable<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> truncated = new ArrayList<>();
        for (String value : values) {
            String item = truncate(value);
            if (StringUtils.hasText(item)) {
                truncated.add(item);
            }
        }
        return List.copyOf(truncated);
    }

    private static String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= REMOTE_TEXT_LIMIT ? trimmed : trimmed.substring(0, REMOTE_TEXT_LIMIT);
    }

    private static AdultAssessment toAssessment(AdultAssessmentResult result) {
        AdultAssessmentLevel level = parseLevel(result.level());
        return new AdultAssessment(
                StringUtils.hasText(result.ruleVersion()) ? result.ruleVersion() : AdultContentAssessor.RULE_VERSION,
                level,
                result.adultRestricted() || level == AdultAssessmentLevel.ADULT,
                Math.max(0, Math.min(100, result.confidence())),
                result.signals().stream()
                        .map(ContentSafetyAdultAssessmentClient::toSignal)
                        .toList());
    }

    private static AdultAssessmentSignal toSignal(AdultAssessmentSignalDto signal) {
        return new AdultAssessmentSignal(
                signal.source(),
                signal.field(),
                signal.matchedValue(),
                signal.score(),
                signal.reason());
    }

    private static AdultAssessmentLevel parseLevel(String value) {
        if (!StringUtils.hasText(value)) {
            return AdultAssessmentLevel.SUSPECT;
        }
        try {
            return AdultAssessmentLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AdultAssessmentLevel.SUSPECT;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Duration normalizeTimeout(Duration value) {
        return value == null || value.isNegative() || value.isZero() ? Duration.ofSeconds(5) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
