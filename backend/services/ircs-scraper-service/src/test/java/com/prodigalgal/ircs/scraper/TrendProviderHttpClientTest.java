package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicyType;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TrendProviderHttpClientTest {

    private final OutboundHttpClient outbound = Mockito.mock(OutboundHttpClient.class);
    private final ScraperTrendConfigValues config = Mockito.mock(ScraperTrendConfigValues.class);
    private final TrendProviderHttpClient client = new TrendProviderHttpClient(outbound, config);

    @Test
    void usesInjectedPublicOutboundTransportRatherThanCreatingDirectFallback() throws Exception {
        when(config.providerTimeout()).thenReturn(Duration.ofSeconds(20));
        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8)));

        assertThat(client.get("https://api.example/trend")).isEqualTo("{}");

        var request = ArgumentCaptor.forClass(OutboundHttpRequest.class);
        verify(outbound).execute(request.capture());
        assertThat(request.getValue().policy().type()).isEqualTo(OutboundHttpPolicyType.PUBLIC_FETCH);
        assertThat(request.getValue().policy().timeout()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void secretBearingTransportExceptionsAreReplacedByFixedErrorCodes() throws Exception {
        when(config.providerTimeout()).thenReturn(Duration.ofSeconds(20));
        when(outbound.execute(any())).thenThrow(new IOException("Failed https://api.example/?api_key=must-not-leak"));

        assertThatThrownBy(() -> client.get("https://api.example/?api_key=must-not-leak"))
                .isInstanceOf(TrendProviderFetchException.class).hasMessage("FETCH_FAILED").hasNoCause();
    }

    @Test
    void statusCodeAndResponseSizeFailuresRemainExplicit() throws Exception {
        when(config.providerTimeout()).thenReturn(Duration.ofSeconds(20));
        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(429, Map.of(), new byte[0]));
        assertThatThrownBy(() -> client.get("https://api.example/trend")).hasMessage("RATE_LIMITED");

        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(503, Map.of(), new byte[0]));
        assertThatThrownBy(() -> client.get("https://api.example/trend")).hasMessage("HTTP_503");

        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(200, Map.of(), new byte[2_000_001]));
        assertThatThrownBy(() -> client.get("https://api.example/trend")).hasMessage("RESPONSE_TOO_LARGE");
    }

    @Test
    void antiAutomationAnswersBecomeTheirOwnProviderCodes() throws Exception {
        when(config.providerTimeout()).thenReturn(Duration.ofSeconds(20));

        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(202,
                Map.of("x-amzn-waf-action", java.util.List.of("challenge")), new byte[0]));
        assertThatThrownBy(() -> client.get("https://www.imdb.com/title/tt0133093/")).hasMessage("WAF_CHALLENGE");

        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(302,
                Map.of("Location", java.util.List.of("https://sec.douban.com/c?r=x&a=1")), new byte[0]));
        assertThatThrownBy(() -> client.get("https://movie.douban.com/subject/1291843/"))
                .hasMessage("SECURITY_REDIRECT");

        when(outbound.execute(any())).thenReturn(new OutboundHttpResponse(403,
                Map.of("Akamai-GRN", java.util.List.of("0.826e2317.1.2")), new byte[0]));
        assertThatThrownBy(() -> client.get("https://www.rottentomatoes.com/search?search=x"))
                .hasMessage("BOT_MANAGER_DENY");
    }

    @Test
    void interruptionIsPreservedForSchedulerCancellation() throws Exception {
        when(config.providerTimeout()).thenReturn(Duration.ofSeconds(20));
        when(outbound.execute(any())).thenThrow(new InterruptedException("cancelled"));

        try {
            assertThatThrownBy(() -> client.get("https://api.example/trend")).hasMessage("FETCH_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
