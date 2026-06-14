package com.prodigalgal.ircs.notification.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import com.prodigalgal.ircs.contracts.credential.ProviderCredentialLease;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

public class CredentialServiceMailCredentialRepository {

    private static final String PROVIDER = "MAIL";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";

    private final OutboundHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final NotificationMailProperties properties;

    CredentialServiceMailCredentialRepository(
            OutboundHttpClient httpClient,
            ObjectMapper objectMapper,
            NotificationMailProperties properties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    MailCredential leaseRequired() {
        try {
            OutboundHttpRequest request = OutboundHttpRequest.get(
                    credentialLeaseUri(),
                    OutboundHttpPolicy.internalService(properties.getCredentialService().getRequestTimeout()));
            request = InternalServiceAuthHeaders.apply(
                    request,
                    properties.getCredentialService().getServiceId(),
                    properties.getCredentialService().getToken(),
                    properties.getCredentialService().getScopes());

            OutboundHttpResponse response = httpClient.execute(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OutboundHttpException("Credential-service returned status " + response.statusCode());
            }
            ProviderCredentialLease[] leases = objectMapper.readValue(response.body(), ProviderCredentialLease[].class);
            if (leases == null) {
                throw new MailCredentialLeaseException("MAIL credential is not available");
            }
            return Arrays.stream(leases)
                    .map(this::mapLease)
                    .filter(this::hasRequiredSecrets)
                    .findFirst()
                    .orElseThrow(() -> new MailCredentialLeaseException("MAIL credential is not available"));
        } catch (MailCredentialLeaseException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new MailCredentialLeaseException(
                    "Unable to lease MAIL credentials from credential-service",
                    ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MailCredentialLeaseException(
                    "Unable to lease MAIL credentials from credential-service",
                    ex);
        }
    }

    private URI credentialLeaseUri() {
        return UriComponentsBuilder.fromUriString(properties.getCredentialService().getBaseUrl())
                .path("/internal/credentials/providers/{provider}/leases")
                .queryParam("requiredPayloadKey", USERNAME_KEY)
                .queryParam("limit", properties.getCredentialService().getLeaseLimit())
                .build(PROVIDER);
    }

    private MailCredential mapLease(ProviderCredentialLease lease) {
        Map<String, String> secretPayload = lease.getSecretPayload();
        return new MailCredential(
                lease.getId(),
                secretPayload == null ? null : secretPayload.get(USERNAME_KEY),
                secretPayload == null ? null : secretPayload.get(PASSWORD_KEY));
    }

    private boolean hasRequiredSecrets(MailCredential credential) {
        return StringUtils.hasText(credential.username()) && StringUtils.hasText(credential.password());
    }
}
