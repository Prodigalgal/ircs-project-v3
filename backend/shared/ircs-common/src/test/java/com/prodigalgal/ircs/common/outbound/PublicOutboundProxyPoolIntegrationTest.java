package com.prodigalgal.ircs.common.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PublicOutboundProxyPoolIntegrationTest {

    @Test
    void realJdkTransportUsesSecondProxyAfterFirstGatewayFailureAndKeepsFailedGatewayCooling() throws Exception {
        AtomicInteger failedCalls = new AtomicInteger();
        AtomicInteger healthyCalls = new AtomicInteger();
        HttpServer failed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        HttpServer healthy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        failed.createContext("/", exchange -> {
            failedCalls.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        healthy.createContext("/", exchange -> {
            healthyCalls.incrementAndGet();
            byte[] body = "healthy-gateway".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        failed.start();
        healthy.start();
        try {
            RuntimeConfigService config = org.mockito.Mockito.mock(RuntimeConfigService.class);
            when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
            when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                    .thenReturn("http://127.0.0.1:" + failed.getAddress().getPort()
                            + ",http://127.0.0.1:" + healthy.getAddress().getPort());
            when(config.positiveDurationValue(PublicOutboundProxyPool.COOLDOWN_KEY, Duration.ofSeconds(30)))
                    .thenReturn(Duration.ofSeconds(30));
            OutboundHttpClient client = new OutboundHttpClient(new OutboundUrlPolicy(
                    host -> List.of(InetAddress.getByName("93.184.216.34"))),
                    new JdkOutboundTransport(Duration.ofSeconds(2)), new PublicOutboundProxyPool(config));
            OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(Duration.ofSeconds(2)).withMaxRetries(1)
                    .withHttpProtocol("HTTP_1_1").withCircuitBreaker(OutboundCircuitBreakerConfig.disabled());

            assertThat(client.execute(OutboundHttpRequest.get("http://public.invalid/first", policy)).bodyAsUtf8())
                    .isEqualTo("healthy-gateway");
            assertThat(client.execute(OutboundHttpRequest.get("http://public.invalid/second", policy)).bodyAsUtf8())
                    .isEqualTo("healthy-gateway");
            assertThat(failedCalls).hasValue(1);
            assertThat(healthyCalls).hasValue(2);
        } finally {
            failed.stop(0);
            healthy.stop(0);
        }
    }
}
