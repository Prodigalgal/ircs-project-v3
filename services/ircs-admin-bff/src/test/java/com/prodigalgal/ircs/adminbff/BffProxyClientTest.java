package com.prodigalgal.ircs.adminbff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.common.audit.BffRequestAuditWriter;
import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingTransport;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

class BffProxyClientTest {

    private final FakeStreamingTransport transport = new FakeStreamingTransport();
    private final BffProxyClient proxyClient = new BffProxyClient(
            Duration.ofSeconds(5),
            Duration.ofSeconds(2),
            Duration.ofMinutes(30),
            "admin-bff-proxy-test",
            false,
            5,
            Duration.ofSeconds(30),
            1,
            "admin-bff",
            "",
            "ops:read ops:run",
            new BffRequestAuditWriter(false, null, null, null, "ircs-admin-bff"),
            provider(new OutboundStreamingHttpClient(
                    new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                    transport)),
            provider(new OutboundUrlPolicy(new DefaultOutboundAddressResolver())));

    @Test
    void forwardsWhitelistedRouteQueryAndFiltersHopByHopHeaders() throws Exception {
        transport.response = response(
                302,
                Map.of(
                        "Location", List.of("http://content/next"),
                        "Transfer-Encoding", List.of("chunked"),
                        "X-Upstream", List.of("content")),
                "redirect-body");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/raw-videos/42");
        request.setQueryString("next=http://169.254.169.254/latest&keyword=codex");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Host", "admin-bff");
        request.addHeader("Proxy-Authorization", "Basic secret");
        request.addHeader("Authorization", "Bearer ircs_pat_should_not_forward");
        request.addHeader(AdminApiTokenService.HEADER_API_TOKEN, "ircs_pat_header_should_not_forward");
        request.addHeader("X-Authenticated-User", "spoof@example.com");
        request.addHeader("X-IRCS-Auth-Role", "ROLE_MEMBER");
        request.addHeader("X-Trace-Id", "trace-1");
        IrcsAuthHeaders.setRequestAttribute(request, adminPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/raw-videos", "http://content-service", "/api/v1/raw-videos")));

        proxyClient.proxy(request, response, routes);

        OutboundHttpRequest sent = transport.requests.getFirst();
        assertThat(sent.uri())
                .isEqualTo(URI.create("http://content-service/api/v1/raw-videos/42?next=http://169.254.169.254/latest&keyword=codex"));
        assertThat(sent.method()).isEqualTo("GET");
        assertThat(sent.body()).isEmpty();
        assertThat(sent.policy().circuitBreakerKey()).isEqualTo("admin-bff-proxy-test");
        assertThat(sent.policy().timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(sent.headers()).containsEntry("X-Trace-Id", "trace-1");
        assertThat(sent.headers()).containsEntry(IrcsAuthHeaders.AUTHENTICATED_USER, "admin");
        assertThat(sent.headers()).containsEntry(IrcsAuthHeaders.AUTH_ROLE, IrcsPermissions.ROLE_ADMIN);
        assertThat(sent.headers()).containsEntry(IrcsAuthHeaders.AUTH_PERMISSIONS, IrcsPermissions.ALL);
        assertThat(sent.headers()).doesNotContainKeys(
                "Connection",
                "Host",
                "Proxy-Authorization",
                "Authorization",
                AdminApiTokenService.HEADER_API_TOKEN);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("http://content/next");
        assertThat(response.getHeader("X-Upstream")).isEqualTo("content");
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("redirect-body");
    }

    private static IrcsRequestPrincipal adminPrincipal() {
        return new IrcsRequestPrincipal(
                "admin",
                IrcsPermissions.ROLE_ADMIN,
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.SCOPE_ADMIN_ALL, IrcsPermissions.SCOPE_DATA_ALL),
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.ALL));
    }

    @Test
    void forwardsPatchBodyThroughSharedTransport() throws Exception {
        transport.response = response(200, Map.of("X-Upstream", List.of("content")), "{\"ok\":true}");
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/v1/raw-videos/42");
        request.setContentType("application/json");
        request.setContent("{\"title\":\"codex\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/raw-videos", "http://content-service", "/api/v1/raw-videos")));

        proxyClient.proxy(request, response, routes);

        OutboundHttpRequest sent = transport.requests.getFirst();
        assertThat(sent.method()).isEqualTo("PATCH");
        assertThat(new String(sent.body(), StandardCharsets.UTF_8)).isEqualTo("{\"title\":\"codex\"}");
        assertThat(sent.headers()).containsEntry("Content-Type", "application/json");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void usesLongTimeoutForEventStreamProxyRequests() throws Exception {
        transport.response = response(200, Map.of("Content-Type", List.of("text/event-stream")), "data: {}\n\n");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard/stream/metrics");
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/dashboard", "http://ops-service", "/api/v1/dashboard")));

        proxyClient.proxy(request, response, routes);

        assertThat(transport.requests.getFirst().policy().timeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(response.getContentAsString()).isEqualTo("data: {}\n\n");
    }

    @Test
    void stripsEventSourceQueryTokenBeforeProxying() throws Exception {
        transport.response = response(200, Map.of("Content-Type", List.of("text/event-stream")), "data: {}\n\n");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard/stream/metrics");
        request.setQueryString("token=secret-jwt&limit=50");
        request.addHeader("Accept", "text/event-stream");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/dashboard", "http://ops-service", "/api/v1/dashboard")));

        proxyClient.proxy(request, response, routes);

        assertThat(transport.requests.getFirst().uri().toString())
                .isEqualTo("http://ops-service/api/v1/dashboard/stream/metrics?limit=50");
    }

    @Test
    void preservesBusinessQueryTokenOnRegularRequests() throws Exception {
        transport.response = response(200, Map.of("X-Upstream", List.of("content")), "ok");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/raw-videos/42");
        request.setQueryString("token=business-token&limit=50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/raw-videos", "http://content-service", "/api/v1/raw-videos")));

        proxyClient.proxy(request, response, routes);

        assertThat(transport.requests.getFirst().uri().toString())
                .isEqualTo("http://content-service/api/v1/raw-videos/42?token=business-token&limit=50");
    }

    @Test
    void rejectsUnknownRouteBeforeSendingRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/not-allowed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/raw-videos", "http://content-service", "/api/v1/raw-videos")));

        assertThatThrownBy(() -> proxyClient.proxy(request, response, routes))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException status = (ResponseStatusException) ex;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(status.getReason()).isEqualTo("Resource not found");
                });
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void rejectsInvalidTargetBaseUrlBeforeSendingRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/raw-videos/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/v1/raw-videos", "http://token@content-service", "/api/v1/raw-videos")));

        assertThatThrownBy(() -> proxyClient.proxy(request, response, routes))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException status = (ResponseStatusException) ex;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(status.getReason()).isEqualTo("Invalid BFF target base URL");
                });
        assertThat(transport.requests).isEmpty();
    }

    private static OutboundStreamingHttpResponse response(
            int statusCode,
            Map<String, List<String>> headers,
            String body) {
        return new OutboundStreamingHttpResponse(
                statusCode,
                headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfUnique()).thenReturn(value);
        return provider;
    }

    private static final class FakeStreamingTransport implements OutboundStreamingTransport {

        private final List<OutboundHttpRequest> requests = new ArrayList<>();
        private OutboundStreamingHttpResponse response = response(200, Map.of(), "ok");

        @Override
        public OutboundStreamingHttpResponse send(OutboundHttpRequest request) throws IOException {
            requests.add(request);
            return response;
        }
    }
}
