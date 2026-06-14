package com.prodigalgal.ircs.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.outbound.OutboundCircuitOpenException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.normalization.LlmCleaningRunResponse;
import com.prodigalgal.ircs.contracts.normalization.NormalizationMaintenanceRunResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class NormalizationLlmMaintenanceClient implements MaintenanceLlmCleaningClient {

    private static final String PATH_PREFIX = "/internal/v1/normalization/llm-cleaning/";
    private static final String PATH_SUFFIX = "/run";
    private static final String RESET_NORMALIZATION_PATH =
            "/internal/v1/normalization/maintenance/raw-videos/reset-normalization";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final ObjectMapper objectMapper;
    private final OutboundHttpClient httpClient;
    private final OpsConfigValues configValues;

    NormalizationLlmMaintenanceClient(
            ObjectMapper objectMapper,
            OutboundHttpClient httpClient,
            OpsConfigValues configValues) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.configValues = configValues;
    }

    @Override
    public MaintenanceRunResult run(MaintenanceLlmCleaningTask task, String correlationId) {
        try {
            OutboundHttpResponse response = post(
                    runUri(task),
                    correlationId,
                    true,
                    configValues.normalizationLlmOwnerRequestTimeout(),
                    "ops-normalization-llm-maintenance",
                    "normalization-worker LLM cleaning failed");
            LlmCleaningRunResponse body = objectMapper.readValue(response.body(), LlmCleaningRunResponse.class);
            return new MaintenanceRunResult(
                    task.taskName(),
                    Math.max(0, body.candidates()),
                    Math.max(0, body.mapped()) + Math.max(0, body.noise()),
                    List.of());
        } catch (OutboundCircuitOpenException ex) {
            throw new IllegalStateException("normalization-worker LLM cleaning failed: outbound circuit open", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("normalization-worker LLM cleaning failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("normalization-worker LLM cleaning failed: interrupted", ex);
        }
    }

    @Override
    public MaintenanceRunResult resetAllNormalization(String correlationId) {
        try {
            OutboundHttpResponse response = post(
                    resetNormalizationUri(),
                    correlationId,
                    false,
                    configValues.normalizationOwnerRequestTimeout(),
                    "ops-normalization-maintenance",
                    "normalization-worker maintenance failed");
            NormalizationMaintenanceRunResponse body =
                    objectMapper.readValue(response.body(), NormalizationMaintenanceRunResponse.class);
            return new MaintenanceRunResult(
                    "sanitize",
                    safeInt(body.rawVideoCount()),
                    safeInt(body.changedRows()),
                    ids(body.sampleRawVideoIds()));
        } catch (OutboundCircuitOpenException ex) {
            throw new IllegalStateException("normalization-worker maintenance failed: outbound circuit open", ex);
        } catch (IOException | IllegalArgumentException ex) {
            throw new IllegalStateException("normalization-worker maintenance failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("normalization-worker maintenance failed: interrupted", ex);
        }
    }

    private OutboundHttpResponse post(
            URI uri,
            String correlationId,
            boolean withBody,
            java.time.Duration requestTimeout,
            String circuitBreakerKey,
            String failurePrefix)
            throws IOException, InterruptedException {
        OutboundHttpPolicy policy = OutboundHttpPolicy.internalService(requestTimeout)
                .withCallerCircuitBreakerKey(circuitBreakerKey);
        OutboundHttpRequest request = OutboundHttpRequest.post(uri, policy)
                .withHeader("Accept", "application/json");
        if (withBody) {
            request = request.withHeader("Content-Type", "application/json")
                    .withBody("{}".getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(correlationId)) {
            request = request.withHeader(CORRELATION_HEADER, correlationId.trim());
        }
        request = InternalServiceAuthHeaders.apply(
                request,
                configValues.normalizationOwnerServiceId(),
                configValues.normalizationOwnerServiceToken(),
                configValues.normalizationOwnerScopes());

        OutboundHttpResponse response = httpClient.execute(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(failurePrefix + ": upstream status " + response.statusCode());
        }
        return response;
    }

    private URI runUri(MaintenanceLlmCleaningTask task) {
        String baseUrl = configValues.normalizationOwnerBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + PATH_PREFIX + task.ownerKind() + PATH_SUFFIX);
    }

    private URI resetNormalizationUri() {
        String baseUrl = configValues.normalizationOwnerBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + RESET_NORMALIZATION_PATH
                + "?sampleLimit=" + configValues.normalizationMaintenanceDevLimit());
    }

    private int safeInt(long value) {
        if (value <= 0) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private List<UUID> ids(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }
}
