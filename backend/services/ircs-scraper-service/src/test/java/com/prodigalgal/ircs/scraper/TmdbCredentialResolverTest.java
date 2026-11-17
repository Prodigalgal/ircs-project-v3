package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TmdbCredentialResolverTest {

    private final OutboundHttpClient httpClient = org.mockito.Mockito.mock(OutboundHttpClient.class);
    private final ScraperTrendConfigValues configValues = org.mockito.Mockito.mock(ScraperTrendConfigValues.class);
    private final TmdbCredentialResolver resolver =
            TmdbCredentialResolver.forTest(new ObjectMapper(), httpClient, configValues);

    @Test
    void leasesTmdbApiKeyFromCredentialServiceBeforeEnvFallback() throws Exception {
        when(configValues.tmdbCredentialServiceEnabled()).thenReturn(true);
        when(configValues.tmdbCredentialServiceBaseUrl()).thenReturn("http://credential-service");
        when(configValues.tmdbCredentialServiceLeaseLimit()).thenReturn(1);
        when(configValues.tmdbCredentialServiceRequestTimeout()).thenReturn(java.time.Duration.ofSeconds(10));
        when(configValues.tmdbCredentialServiceId()).thenReturn("scraper-service");
        when(configValues.tmdbCredentialServiceToken()).thenReturn("service-token");
        when(configValues.tmdbCredentialServiceScopes()).thenReturn("credential:lease");
        when(configValues.tmdbApiKey()).thenReturn("env-key");
        when(httpClient.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OutboundHttpResponse(
                        200,
                        Map.of(),
                        """
                                [{"secretPayload":{"api_key":"leased-key"}}]
                                """.getBytes(StandardCharsets.UTF_8)));

        assertThat(resolver.resolveApiKey()).contains("leased-key");
    }

    @Test
    void fallsBackToEnvApiKeyWhenCredentialServiceHasNoLease() throws Exception {
        when(configValues.tmdbCredentialServiceEnabled()).thenReturn(true);
        when(configValues.tmdbCredentialServiceBaseUrl()).thenReturn("http://credential-service");
        when(configValues.tmdbCredentialServiceLeaseLimit()).thenReturn(1);
        when(configValues.tmdbCredentialServiceRequestTimeout()).thenReturn(java.time.Duration.ofSeconds(10));
        when(configValues.tmdbCredentialServiceId()).thenReturn("scraper-service");
        when(configValues.tmdbCredentialServiceToken()).thenReturn("service-token");
        when(configValues.tmdbCredentialServiceScopes()).thenReturn("credential:lease");
        when(configValues.tmdbApiKey()).thenReturn("env-key");
        when(httpClient.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new OutboundHttpResponse(200, Map.of(), "[]".getBytes(StandardCharsets.UTF_8)));

        assertThat(resolver.resolveApiKey()).contains("env-key");
    }

    @Test
    void credentialTransportFailureDoesNotLogSecretBearingExceptionMessage() throws Exception {
        when(configValues.tmdbCredentialServiceEnabled()).thenReturn(true);
        when(configValues.tmdbCredentialServiceBaseUrl()).thenReturn("http://credential-service");
        when(configValues.tmdbCredentialServiceLeaseLimit()).thenReturn(1);
        when(configValues.tmdbCredentialServiceRequestTimeout()).thenReturn(java.time.Duration.ofSeconds(10));
        when(configValues.tmdbCredentialServiceId()).thenReturn("scraper-service");
        when(configValues.tmdbCredentialServiceToken()).thenReturn("service-token");
        when(configValues.tmdbCredentialServiceScopes()).thenReturn("credential:lease");
        when(configValues.tmdbApiKey()).thenReturn("env-key");
        when(httpClient.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IOException("Provider secretPayload api_key=must-not-leak"));

        var messages = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(TmdbCredentialResolver.class);
        var previousLevel = logger.getLevel();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("credential-redaction-test", null,
                org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override
            public void append(org.apache.logging.log4j.core.LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start(); logger.addAppender(appender); logger.setLevel(org.apache.logging.log4j.Level.WARN);
        try {
            assertThat(resolver.resolveApiKey()).contains("env-key");
            assertThat(String.join("\n", messages)).contains("CREDENTIAL_LEASE_FAILED", "IOException").doesNotContain("must-not-leak");
        } finally {
            logger.removeAppender(appender); logger.setLevel(previousLevel); appender.stop();
        }
    }
}
