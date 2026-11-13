package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GoogleDecisionCredentialResolverTest {
    @Test
    void rotatesEligibleLeasesAndDoesNotExposeKeysInDiagnosticString() throws Exception {
        DecisionSettings settings = new DecisionSettings(); settings.setCredentialServiceToken("internal-test");
        DecisionHttpTransport transport = mock(DecisionHttpTransport.class);
        String body = """
                [{"provider":"GOOGLE_DECISIONS","expiresAt":"%s","secretPayload":{"api_key":"first-key"}},
                 {"provider":"GOOGLE_DECISIONS","expiresAt":"%s","secretPayload":{"api_key":"second-key"}},
                 {"provider":"GOOGLE_DECISIONS","expiresAt":"2000-01-01T00:00:00Z","secretPayload":{"api_key":"expired"}},
                 {"provider":"OPENAI","expiresAt":"%s","secretPayload":{"api_key":"wrong-provider"}}]
                """.formatted(Instant.now().plusSeconds(60), Instant.now().plusSeconds(60), Instant.now().plusSeconds(60));
        when(transport.exchange(any(), any(), any(), any(), any())).thenReturn(new DecisionHttpTransport.Response(200, body, Map.of()));
        var resolver = new GoogleDecisionCredentialResolver(settings, new ObjectMapper(), transport);
        assertThat(resolver.resolve().apiKey()).isEqualTo("first-key");
        assertThat(resolver.resolve().apiKey()).isEqualTo("second-key");
        assertThat(resolver.resolve().apiKey()).isEqualTo("first-key");
        assertThat(resolver.resolve().toString()).isEqualTo("GoogleDecisionCredential[redacted]");
        verify(transport, times(4)).exchange(eq("GET"),
                eq(java.net.URI.create("http://ircs-credential-service:8080/internal/credentials/providers/GOOGLE_DECISIONS/leases?requiredPayloadKey=api_key&limit=100")),
                any(), isNull(), any());
    }

    @Test
    void missingInternalTokenAndUnsafeBaseUrlDoNotRequestCredentials() {
        var settings = new DecisionSettings();
        var transport = mock(DecisionHttpTransport.class);
        var resolver = new GoogleDecisionCredentialResolver(settings, new ObjectMapper(), transport);
        assertThatThrownBy(resolver::resolve).hasMessage("CREDENTIAL_MISSING");
        settings.setCredentialServiceToken("internal-test");
        settings.setCredentialServiceBaseUrl("https://user:password@example.test");
        assertThatThrownBy(resolver::resolve).hasMessage("CONFIGURATION_ERROR");
        verifyNoInteractions(transport);
    }

    @Test
    void emptyEligiblePoolFailsClosedAndInternalAuthenticationHeadersAreExplicit() throws Exception {
        var settings = new DecisionSettings(); settings.setCredentialServiceToken("internal-test");
        var transport = mock(DecisionHttpTransport.class);
        when(transport.exchange(any(), any(), any(), any(), any()))
                .thenReturn(new DecisionHttpTransport.Response(200, "[]", Map.of()));
        var resolver = new GoogleDecisionCredentialResolver(settings, new ObjectMapper(), transport);
        assertThatThrownBy(resolver::resolve).hasMessage("CREDENTIAL_MISSING");
        var headers = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(transport).exchange(eq("GET"), any(), headers.capture(), isNull(), any());
        assertThat(headers.getValue()).containsEntry(
                com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders.SERVICE_ID, "ircs-content-safety-service");
        assertThat(headers.getValue()).containsEntry(
                com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders.SERVICE_TOKEN, "internal-test");
        assertThat(headers.getValue()).containsEntry(
                com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders.SERVICE_SCOPES, "credential:lease");
    }
}
