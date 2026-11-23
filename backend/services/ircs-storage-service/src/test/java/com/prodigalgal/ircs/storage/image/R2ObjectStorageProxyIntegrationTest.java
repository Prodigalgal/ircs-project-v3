package com.prodigalgal.ircs.storage.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

class R2ObjectStorageProxyIntegrationTest {
    private final List<HttpServer> servers = new java.util.ArrayList<>();
    private final List<R2ObjectStorage> storages = new java.util.ArrayList<>();

    @AfterEach
    void closeResources() {
        storages.forEach(R2ObjectStorage::close);
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void sdkPutAndHeadUseConfiguredProxyForAnUnresolvablePublicEndpoint() throws Exception {
        var calls = new CopyOnWriteArrayList<String>();
        var proxy = server(200, calls);
        var storage = storage(pool(endpoint(proxy)), "http://r2.public.invalid");

        storage.store("image".getBytes(StandardCharsets.UTF_8), "covers/example.jpg", "image/jpeg");
        assertThat(storage.exists("covers/example.jpg")).isTrue();

        assertThat(calls).hasSize(2);
        assertThat(calls).anyMatch(call -> call.startsWith("PUT "))
                .anyMatch(call -> call.startsWith("HEAD "));
        assertThat(calls).allMatch(call -> call.contains("r2.public.invalid") && call.contains("covers/example.jpg"));
    }

    @Test
    void failedGatewayCoolsDownAndSubsequentOperationsUseTheHealthyGateway() throws Exception {
        var failed = new CopyOnWriteArrayList<String>();
        var healthy = new CopyOnWriteArrayList<String>();
        var storage = storage(pool(endpoint(server(503, failed)) + "," + endpoint(server(200, healthy))),
                "http://r2.public.invalid");

        assertThatThrownBy(() -> storage.exists("covers/image.jpg")).isInstanceOf(S3Exception.class);
        assertThat(storage.exists("covers/image.jpg")).isTrue();
        assertThat(storage.exists("covers/image.jpg")).isTrue();

        assertThat(failed.size()).isBetween(1, 4); // Only bounded SDK retries, no application retry loop.
        assertThat(healthy).hasSize(2);
    }

    @Test
    void unavailableOrCoolingPoolNeverFallsBackToDirectAccess() throws Exception {
        var proxyPool = pool("");
        var storage = storage(proxyPool, "http://r2.public.invalid");
        assertThatThrownBy(() -> storage.exists("covers/image.jpg")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("direct fallback is disabled");

        var calls = new CopyOnWriteArrayList<String>();
        proxyPool = pool(endpoint(server(200, calls)));
        proxyPool.recordFailure(proxyPool.select());
        var cooling = storage(proxyPool, "http://r2.public.invalid");
        assertThatThrownBy(() -> cooling.exists("covers/image.jpg")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("direct fallback is disabled");
        assertThat(calls).isEmpty();
    }

    @Test
    void administratorConfiguredInternalStorageRemainsDirectAndNotFoundDoesNotCoolTheProxy() throws Exception {
        var internalCalls = new CopyOnWriteArrayList<String>();
        var config = mock(RuntimeConfigService.class);
        var internal = storage(new PublicOutboundProxyPool(config), endpoint(server(200, internalCalls)));
        assertThat(internal.exists("covers/internal.jpg")).isTrue();
        verify(config, never()).value(PublicOutboundProxyPool.ENABLED_KEY);
        assertThat(internalCalls).hasSize(1);

        var missingCalls = new CopyOnWriteArrayList<String>();
        var missing = storage(pool(endpoint(server(404, missingCalls))), "http://r2.public.invalid");
        assertThat(missing.exists("covers/missing.jpg")).isFalse();
        assertThat(missing.exists("covers/missing.jpg")).isFalse();
        assertThat(missingCalls).hasSize(2);
    }

    private PublicOutboundProxyPool pool(String endpoints) {
        var config = mock(RuntimeConfigService.class);
        when(config.value(PublicOutboundProxyPool.ENABLED_KEY)).thenReturn(Optional.of("true"));
        when(config.stringValue(PublicOutboundProxyPool.ENDPOINTS_KEY, PublicOutboundProxyPool.DEFAULT_ENDPOINTS))
                .thenReturn(endpoints);
        when(config.positiveDurationValue(PublicOutboundProxyPool.COOLDOWN_KEY, Duration.ofSeconds(30)))
                .thenReturn(Duration.ofSeconds(30));
        return new PublicOutboundProxyPool(config);
    }

    private R2ObjectStorage storage(PublicOutboundProxyPool pool, String endpoint) {
        var storage = new R2ObjectStorage(pool, true, "ircs", "test", "test-access", "test-secret", endpoint);
        storages.add(storage);
        return storage;
    }

    private HttpServer server(int status, List<String> calls) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Host"));
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("ETag", "\"test-etag\"");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        servers.add(server);
        return server;
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
