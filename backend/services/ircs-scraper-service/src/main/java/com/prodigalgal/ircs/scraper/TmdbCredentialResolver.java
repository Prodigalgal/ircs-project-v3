package com.prodigalgal.ircs.scraper;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.credential.ProviderCredentialLease;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RegisterReflectionForBinding(ProviderCredentialLease.class)
class TmdbCredentialResolver {

    private static final String PROVIDER = "TMDB";
    private static final String API_KEY = "api_key";

    private final ObjectMapper objectMapper;
    private final OutboundHttpClient httpClient;
    private final ScraperTrendConfigValues configValues;

    TmdbCredentialResolver(
            ObjectMapper objectMapper,
            OutboundHttpClient httpClient,
            ScraperTrendConfigValues configValues) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.configValues = configValues;
    }

    static TmdbCredentialResolver forTest(
            ObjectMapper objectMapper,
            OutboundHttpClient httpClient,
            ScraperTrendConfigValues configValues) {
        return new TmdbCredentialResolver(objectMapper, httpClient, configValues);
    }

    Optional<String> resolveApiKey() {
        Optional<String> leased = leaseApiKey();
        if (leased.isPresent()) {
            return leased;
        }
        String fallback = configValues.tmdbApiKey();
        return StringUtils.hasText(fallback) ? Optional.of(fallback.trim()) : Optional.empty();
    }

    @PostConstruct
    void verifyCredentialBinding() {
        byte[] probe = """
                [{"provider":"TMDB","secretPayload":{"api_key":"binding-probe"},
                  "expiresAt":"2026-01-01T00:05:00Z"}]
                """.getBytes(StandardCharsets.UTF_8);
        if (!"binding-probe".equals(firstApiKey(probe).orElse(null))) {
            throw new IllegalStateException("Scraper credential JSON binding did not preserve the API key field");
        }
    }

    private Optional<String> leaseApiKey() {
        if (!configValues.tmdbCredentialServiceEnabled()) {
            return Optional.empty();
        }
        try {
            OutboundHttpPolicy policy = OutboundHttpPolicy.internalService(
                            configValues.tmdbCredentialServiceRequestTimeout())
                    .withCallerCircuitBreakerKey("scraper-tmdb-credential-service");
            OutboundHttpRequest request = OutboundHttpRequest.get(leaseUri(), policy)
                    .withHeader("Accept", "application/json");
            request = InternalServiceAuthHeaders.apply(
                    request,
                    configValues.tmdbCredentialServiceId(),
                    configValues.tmdbCredentialServiceToken(),
                    configValues.tmdbCredentialServiceScopes());
            OutboundHttpResponse response = httpClient.execute(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("TMDB credential lease skipped: credential-service returned HTTP {}", response.statusCode());
                return Optional.empty();
            }
            return firstApiKey(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("TMDB credential lease interrupted");
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("TMDB credential lease skipped: CREDENTIAL_LEASE_FAILED ({})", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> firstApiKey(byte[] body) {
        ProviderCredentialLease[] leases = objectMapper.readValue(body, ProviderCredentialLease[].class);
        if (leases == null) return Optional.empty();
        return Arrays.stream(leases).map(this::apiKey)
                .filter(StringUtils::hasText).map(String::trim).findFirst();
    }

    private URI leaseUri() {
        return UriComponentsBuilder.fromUriString(configValues.tmdbCredentialServiceBaseUrl())
                .path("/internal/credentials/providers/{provider}/leases")
                .queryParam("requiredPayloadKey", API_KEY)
                .queryParam("limit", configValues.tmdbCredentialServiceLeaseLimit())
                .build(PROVIDER);
    }

    private String apiKey(ProviderCredentialLease lease) {
        return lease == null || lease.getSecretPayload() == null ? null : lease.getSecretPayload().get(API_KEY);
    }
}
