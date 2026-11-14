package com.prodigalgal.ircs.metadata.provider.credential;

import com.prodigalgal.ircs.contracts.credential.ProviderCredentialLease;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Fail before claiming provider work if the Native credential wire contract cannot be read. */
@Component
@RegisterReflectionForBinding(ProviderCredentialLease.class)
class MetadataCredentialBindingVerifier {
    private static final UUID PROBE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final ObjectMapper mapper;

    MetadataCredentialBindingVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void verify() {
        ProviderCredentialLease[] leases = mapper.readValue("""
                [{"id":"00000000-0000-0000-0000-000000000001","revision":7,
                  "provider":"TMDB","name":"binding-probe","secretPayload":{"api_key":"binding-probe"},
                  "priority":3,"rateLimit":200,"rateLimitUnit":"MINUTE","dayLimit":10000,"monthLimit":300000,
                  "leasedAt":"2026-01-01T00:00:00Z","expiresAt":"2026-01-01T00:05:00Z"}]
                """, ProviderCredentialLease[].class);
        if (leases == null || leases.length != 1 || !valid(leases[0])) {
            throw new IllegalStateException("Metadata credential JSON binding did not preserve the required fields");
        }
    }

    private boolean valid(ProviderCredentialLease lease) {
        return lease != null && PROBE_ID.equals(lease.getId()) && Long.valueOf(7).equals(lease.getRevision())
                && "TMDB".equals(lease.getProvider()) && "binding-probe".equals(lease.getName())
                && lease.getSecretPayload() != null && "binding-probe".equals(lease.getSecretPayload().get("api_key"))
                && Integer.valueOf(3).equals(lease.getPriority()) && Integer.valueOf(200).equals(lease.getRateLimit())
                && "MINUTE".equals(lease.getRateLimitUnit()) && Long.valueOf(10000).equals(lease.getDayLimit())
                && Long.valueOf(300000).equals(lease.getMonthLimit())
                && Instant.parse("2026-01-01T00:00:00Z").equals(lease.getLeasedAt())
                && Instant.parse("2026-01-01T00:05:00Z").equals(lease.getExpiresAt());
    }
}
