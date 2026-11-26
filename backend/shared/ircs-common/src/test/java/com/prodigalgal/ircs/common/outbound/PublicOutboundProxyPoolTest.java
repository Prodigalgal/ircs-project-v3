package com.prodigalgal.ircs.common.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PublicOutboundProxyPoolTest {

    private final RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
    private final List<OutboundHttpRequest> sent = new ArrayList<>();
    private final OutboundUrlPolicy urls = new OutboundUrlPolicy(
            host -> List.of(InetAddress.getByName("93.184.216.34")));
    private final OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(Duration.ofSeconds(1))
            .withCircuitBreaker(OutboundCircuitBreakerConfig.disabled());

    @Test
    void disabledPoolKeepsExistingDirectBehavior() throws Exception {
        OutboundHttpClient client = client(request -> ok());
        client.execute(OutboundHttpRequest.get("https://public.test/api", policy));
        assertThat(sent.getFirst().policy().proxy().enabled()).isFalse();
    }

    @Test
    void publicAndStrictPublicImageRequestsUsePoolButInternalAndExplicitProxyDoNot() throws Exception {
        enabled();
        OutboundHttpClient client = client(request -> ok());
        client.execute(OutboundHttpRequest.get("https://public.test/a", policy));
        client.execute(OutboundHttpRequest.get("https://public.test/b", policy));
        client.execute(OutboundHttpRequest.get("https://public.test/custom", policy.withProxy(
                OutboundProxy.http("custom.proxy", 8080, "u", "p"))));
        client.execute(OutboundHttpRequest.get("http://10.0.0.1/internal", OutboundHttpPolicy.internalService(Duration.ofSeconds(1))));
        client.execute(OutboundHttpRequest.get("http://10.0.0.1/gateway", OutboundHttpPolicy.apiGatewayProxy(Duration.ofSeconds(1))));
        client.execute(OutboundHttpRequest.get("https://public.test/image", OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1))));

        assertThat(sent.get(0).policy().proxy().host()).isEqualTo("oracle.gateway");
        assertThat(sent.get(1).policy().proxy().host()).isEqualTo("cf.gateway");
        assertThat(sent.get(2).policy().proxy().host()).isEqualTo("custom.proxy");
        assertThat(sent.get(3).policy().proxy().enabled()).isFalse();
        assertThat(sent.get(4).policy().proxy().enabled()).isFalse();
        assertThat(sent.get(5).policy().proxy().host()).isEqualTo("oracle.gateway");
        assertThat(sent.get(5).policy().type()).isEqualTo(OutboundHttpPolicyType.IMAGE_DOWNLOAD_STRICT);
    }

    @Test
    void strictImageRequestAllowingLocalAddressesRemainsDirect() throws Exception {
        enabled();
        OutboundHttpClient client = client(request -> ok());
        client.execute(OutboundHttpRequest.get("http://127.0.0.1/image",
                OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1), true)));
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().policy().proxy().enabled()).isFalse();
    }

    @Test
    void streamingImageDownloadsUsePoolAndFailClosed() throws Exception {
        enabled();
        List<OutboundHttpRequest> streamingRequests = new ArrayList<>();
        OutboundStreamingHttpClient streamingClient = new OutboundStreamingHttpClient(urls, request -> {
            streamingRequests.add(request);
            return new OutboundStreamingHttpResponse(200, Map.of(), new java.io.ByteArrayInputStream(new byte[0]));
        }, new PublicOutboundProxyPool(config));
        try (OutboundStreamingHttpResponse ignored = streamingClient.execute(OutboundHttpRequest.get(
                "https://public.test/image", OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1))))) {
            assertThat(streamingRequests.getFirst().policy().proxy().host()).isEqualTo("oracle.gateway");
        }

        List<OutboundHttpRequest> failedRequests = new ArrayList<>();
        PublicOutboundProxyPool failingPool = new PublicOutboundProxyPool(config);
        OutboundStreamingHttpClient failingClient = new OutboundStreamingHttpClient(urls, request -> {
            failedRequests.add(request);
            throw new IOException("gateway unavailable");
        }, failingPool);
        assertThatThrownBy(() -> failingClient.execute(OutboundHttpRequest.get(
                        "https://public.test/image", OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1)))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> failingClient.execute(OutboundHttpRequest.get(
                        "https://public.test/image", OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1)))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> failingClient.execute(OutboundHttpRequest.get(
                        "https://public.test/image", OutboundHttpPolicy.imageDownloadStrict(Duration.ofSeconds(1)))))
                .isInstanceOf(OutboundHttpException.class)
                .hasMessageContaining("direct fallback is disabled");
        assertThat(failedRequests).hasSize(2).allMatch(request -> request.policy().proxy().enabled());
    }

    @Test
    void safeRequestFailsOverInsideOriginalRetryBudgetAndCoolsFailedGateway() throws Exception {
        enabled();
        AtomicInteger attempts = new AtomicInteger();
        OutboundHttpClient client = client(request -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IOException("gateway unavailable");
            }
            return ok();
        });
        client.execute(OutboundHttpRequest.get("https://public.test/api", policy.withMaxRetries(1)));
        client.execute(OutboundHttpRequest.get("https://public.test/next", policy.withMaxRetries(1)));
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).policy().proxy().host()).isEqualTo("oracle.gateway");
        assertThat(sent.get(1).policy().proxy().host()).isEqualTo("cf.gateway");
        assertThat(sent.get(2).policy().proxy().host()).isEqualTo("cf.gateway");
    }

    @Test
    void allCoolingGatewaysFailClosedRatherThanWaitingOrConnectingDirectly() {
        enabled();
        OutboundHttpClient client = client(request -> { throw new IOException("failed"); });
        assertThatThrownBy(() -> client.execute(OutboundHttpRequest.get("https://public.test/api", policy.withMaxRetries(5))))
                .isInstanceOf(OutboundHttpException.class).hasMessageContaining("direct fallback is disabled");
        assertThat(sent).hasSize(2).allMatch(request -> request.policy().proxy().enabled());
    }

    @Test
    void postDoesNotGainAFailoverRetryAndZeroRetryBudgetStaysZero() {
        enabled();
        OutboundHttpClient postClient = client(request -> { throw new IOException("failed"); });
        assertThatThrownBy(() -> postClient.execute(OutboundHttpRequest.post("https://public.test/api", policy)))
                .isInstanceOf(IOException.class);
        assertThat(sent).hasSize(1);
        sent.clear();
        OutboundHttpClient getClient = client(request -> { throw new IOException("failed"); });
        assertThatThrownBy(() -> getClient.execute(OutboundHttpRequest.get("https://public.test/api", policy.withMaxRetries(0))))
                .isInstanceOf(IOException.class);
        assertThat(sent).hasSize(1);
    }

    @Test
    void disappearingEnabledConfigurationCannotSilentlyEnableDirectFallback() throws Exception {
        enabled();
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"), Optional.empty());
        OutboundHttpClient client = client(request -> ok());
        client.execute(OutboundHttpRequest.get("https://public.test/a", policy));
        assertThatThrownBy(() -> client.execute(OutboundHttpRequest.get("https://public.test/b", policy)))
                .isInstanceOf(OutboundHttpException.class).hasMessageContaining("configuration unavailable");
        assertThat(sent).hasSize(1);
    }

    @Test
    void invalidEndpointsAreRejectedWithoutExposingEmbeddedCredentials() {
        when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                .thenReturn("http://private-user:secret@proxy.invalid:8080");
        assertThatThrownBy(() -> new PublicOutboundProxyPool(config).select())
                .isInstanceOf(OutboundHttpException.class).hasMessageNotContaining("secret");
    }

    @Test
    void explicitProviderProxyConfigurationIsPreservedAcrossTemporaryConfigMiss() throws Exception {
        String prefix = "app.metadata.tmdb.proxy.";
        when(config.value(prefix + "enabled")).thenReturn(Optional.of("true"), Optional.empty());
        when(config.stringValue(prefix + "type", "HTTP")).thenReturn("HTTP");
        when(config.stringValue(prefix + "host", "")).thenReturn("custom.proxy");
        when(config.intValue(prefix + "port", 0)).thenReturn(8080);
        when(config.stringValue(prefix + "username", "")).thenReturn("private-user");
        when(config.stringValue(prefix + "password", "")).thenReturn("secret");
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        OutboundProxy custom = pool.customProxy("app.metadata.tmdb");
        assertThat(custom.enabled()).isTrue();
        assertThat(custom.host()).isEqualTo("custom.proxy");
        assertThat(pool.customProxy("app.metadata.tmdb")).isEqualTo(custom);
    }

    @Test
    void authorityRequestsUseTheirConfiguredPoolAndOrdinaryRequestsUseThePublicPool() throws Exception {
        enabled();
        when(config.stringValue(PublicOutboundProxyPool.AUTHORITY_ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS))
                .thenReturn("http://authority.gateway:17890");
        OutboundHttpClient client = client(request -> ok());

        for (int index = 0; index < 4; index++) {
            client.execute(OutboundHttpRequest.get("https://www.rottentomatoes.com/browse/" + index, policy));
        }
        assertThat(sent).hasSize(4)
                .allMatch(request -> "authority.gateway".equals(request.policy().proxy().host()));
        sent.clear();

        for (int index = 0; index < 4; index++) {
            client.execute(OutboundHttpRequest.get("https://public.test/api/" + index, policy));
        }
        assertThat(sent).extracting(request -> request.policy().proxy().host())
                .containsExactly("oracle.gateway", "cf.gateway", "oracle.gateway", "cf.gateway");
    }

    @Test
    void authorityRequestsFailClosedWhenTheirAssignedGatewayIsCoolingDown() throws Exception {
        enabled();
        when(config.stringValue(PublicOutboundProxyPool.AUTHORITY_ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS))
                .thenReturn("http://oracle.gateway:17890");
        PublicOutboundProxyPool pool = new PublicOutboundProxyPool(config);
        pool.recordFailure(OutboundProxy.http("oracle.gateway", 17890, null, null));

        assertThatThrownBy(() -> pool.select(URI.create("https://www.imdb.com/title/tt0133093/")))
                .isInstanceOf(OutboundHttpException.class)
                .hasMessageContaining("direct fallback is disabled");
        assertThat(pool.select(URI.create("https://public.test/api")).host())
                .isEqualTo("cf.gateway");
    }

    @Test
    void blankAuthorityGatewaySettingUsesOracleEvenWithoutTheGeneralPool() throws Exception {
        when(config.stringValue(PublicOutboundProxyPool.AUTHORITY_ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS))
                .thenReturn("");
        OutboundHttpClient client = client(request -> ok());
        client.execute(OutboundHttpRequest.get("https://movie.douban.com/chart", policy));
        client.execute(OutboundHttpRequest.get("https://movie.douban.com/chart", policy));

        assertThat(sent).extracting(request -> request.policy().proxy().host())
                .containsExactly(
                        URI.create(PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS).getHost(),
                        URI.create(PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS).getHost());
    }

    @Test
    void invalidAuthoritySettingsCannotBreakTheOrdinaryPublicPool() throws Exception {
        enabled();
        when(config.stringValue(PublicOutboundProxyPool.AUTHORITY_ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_AUTHORITY_ENDPOINTS))
                .thenReturn("invalid setting");
        OutboundHttpClient client = client(request -> ok());

        assertThatThrownBy(() -> client.execute(OutboundHttpRequest.get("https://www.imdb.com/chart/", policy)))
                .isInstanceOf(OutboundHttpException.class);
        client.execute(OutboundHttpRequest.get("https://public.test/api", policy));

        assertThat(sent).extracting(request -> request.policy().proxy().host()).containsExactly("oracle.gateway");
    }

    private void enabled() {
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                .thenReturn("http://oracle.gateway:17890,http://cf.gateway:17890");
        when(config.positiveDurationValue(PublicOutboundProxyPool.COOLDOWN_KEY, Duration.ofSeconds(30)))
                .thenReturn(Duration.ofSeconds(30));
    }

    private OutboundHttpClient client(OutboundTransport delegate) {
        return new OutboundHttpClient(urls, request -> {
            sent.add(request);
            return delegate.send(request);
        }, new PublicOutboundProxyPool(config));
    }

    private static OutboundHttpResponse ok() {
        return new OutboundHttpResponse(200, Map.of(), new byte[0]);
    }
}
