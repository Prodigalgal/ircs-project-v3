package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.credential.ProviderCredentialLease;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.util.StringUtils;

@Component
@RegisterReflectionForBinding(ProviderCredentialLease.class)
@RequiredArgsConstructor
class GoogleDecisionCredentialResolver {
    private final DecisionSettings settings;
    private final ObjectMapper mapper;
    private final DecisionHttpTransport transport;
    private final AtomicLong cursor = new AtomicLong();

    Credential resolve() {
        if (!StringUtils.hasText(settings.getCredentialServiceToken())) {
            throw new DecisionUnavailableException("CREDENTIAL_MISSING", 60);
        }
        try {
            URI base = URI.create(settings.getCredentialServiceBaseUrl().replaceAll("/+$", "") + "/");
            if (!StringUtils.hasText(base.getHost()) || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                    || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))) {
                throw new DecisionUnavailableException("CONFIGURATION_ERROR", 60);
            }
            DecisionHttpTransport.Response response = transport.exchange("GET", base.resolve(
                    "internal/credentials/providers/GOOGLE_DECISIONS/leases?requiredPayloadKey=api_key&limit=100"),
                    Map.of("Accept", "application/json",
                            InternalServiceAuthHeaders.SERVICE_ID, settings.getCredentialServiceId(),
                            InternalServiceAuthHeaders.SERVICE_TOKEN, settings.getCredentialServiceToken(),
                            InternalServiceAuthHeaders.SERVICE_SCOPES, settings.getCredentialServiceScopes()),
                    null, settings.getRequestTimeout());
            if (response.status() != 200) {
                throw new DecisionUnavailableException("CREDENTIAL_SERVICE_UNAVAILABLE", 60);
            }
            List<ProviderCredentialLease> leases = mapper.readValue(response.body(), new TypeReference<>() {});
            List<ProviderCredentialLease> eligible = leases.stream().filter(lease -> lease != null
                    && DecisionSettings.PROVIDER.equals(lease.getProvider())
                    && lease.getExpiresAt() != null && lease.getExpiresAt().isAfter(Instant.now())
                    && lease.getSecretPayload() != null && StringUtils.hasText(lease.getSecretPayload().get("api_key"))).toList();
            if (eligible.isEmpty()) { throw new DecisionUnavailableException("CREDENTIAL_MISSING", 60); }
            ProviderCredentialLease lease = eligible.get((int) Math.floorMod(cursor.getAndIncrement(), (long) eligible.size()));
            return new Credential(lease.getSecretPayload().get("api_key").trim());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DecisionUnavailableException("CANCELLED", 60);
        } catch (IOException | IllegalArgumentException ex) {
            throw new DecisionUnavailableException("CREDENTIAL_SERVICE_UNAVAILABLE", 60);
        }
    }

    record Credential(String apiKey) {
        @Override public String toString() { return "GoogleDecisionCredential[redacted]"; }
    }
}
