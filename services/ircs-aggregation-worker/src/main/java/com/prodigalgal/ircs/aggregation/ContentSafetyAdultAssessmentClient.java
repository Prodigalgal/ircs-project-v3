package com.prodigalgal.ircs.aggregation;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
class ContentSafetyAdultAssessmentClient {

    private static final String BATCH_PATH = "/internal/v1/content-safety/adult-assessments:batch";

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
                return Map.of();
            }
            AdultAssessmentBatchResponse body =
                    objectMapper.readValue(response.body(), AdultAssessmentBatchResponse.class);
            Map<UUID, AdultAssessment> results = new LinkedHashMap<>();
            for (AdultAssessmentResult result : body.items()) {
                if (result.id() != null) {
                    results.put(result.id(), toAssessment(result));
                }
            }
            return results;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("content-safety adult assessment unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    private static AdultAssessmentItem toItem(UUID id, AdultAssessmentInput input) {
        return new AdultAssessmentItem(
                id,
                input.title(),
                input.aliasTitle(),
                input.description(),
                input.remarks(),
                input.subtitle(),
                input.categoryCode(),
                input.categoryName(),
                List.copyOf(input.actorNames()),
                List.copyOf(input.directorNames()),
                List.copyOf(input.genreCodes()),
                input.sources().stream()
                        .map(ContentSafetyAdultAssessmentClient::toSource)
                        .toList());
    }

    private static AdultAssessmentSourceEvidence toSource(AdultAssessmentInput.SourceEvidence source) {
        return new AdultAssessmentSourceEvidence(
                source.dataSourceName(),
                source.dataSourceAdultRestricted(),
                source.sourceCategoryCode(),
                source.sourceCategoryName(),
                source.sourceDomain(),
                source.rawMetadata());
    }

    private static AdultAssessment toAssessment(AdultAssessmentResult result) {
        AdultAssessmentLevel level = parseLevel(result.level());
        return new AdultAssessment(
                StringUtils.hasText(result.ruleVersion()) ? result.ruleVersion() : AdultContentAssessor.RULE_VERSION,
                level,
                result.adultRestricted(),
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
            return AdultAssessmentLevel.SAFE;
        }
        try {
            return AdultAssessmentLevel.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return AdultAssessmentLevel.SAFE;
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
