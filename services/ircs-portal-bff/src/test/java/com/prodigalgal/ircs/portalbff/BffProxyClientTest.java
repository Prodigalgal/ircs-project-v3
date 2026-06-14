package com.prodigalgal.ircs.portalbff;

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
            "portal-bff-proxy-test",
            false,
            5,
            Duration.ofSeconds(30),
            1,
            new BffRequestAuditWriter(false, null, null, null, "ircs-portal-bff"),
            provider(new OutboundStreamingHttpClient(
                    new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                    transport)),
            provider(new OutboundUrlPolicy(new DefaultOutboundAddressResolver())));

    @Test
    void forwardsWhitelistedRoutePathQueryAndFiltersHopByHopHeaders() throws Exception {
        transport.response = response(
                302,
                Map.of(
                        "Location", List.of("http://portal/next"),
                        "Transfer-Encoding", List.of("chunked"),
                        "X-Upstream", List.of("portal")),
                "redirect-body");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portal/search/suggest");
        request.setQueryString("keyword=codex&next=http://169.254.169.254/latest");
        request.addHeader("Connection", "keep-alive");
        request.addHeader("Host", "portal-bff");
        request.addHeader("Proxy-Authorization", "Basic secret");
        request.addHeader("X-Authenticated-User", "spoof@example.com");
        request.addHeader("X-IRCS-Auth-Role", "ROLE_ADMIN");
        request.addHeader("X-Trace-Id", "trace-portal");
        IrcsAuthHeaders.setRequestAttribute(request, memberPrincipal());
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/portal/search", "http://search-service", "/api/portal/search"),
                new BffRoute("/api/portal", "http://portal-service", "/api/portal")));

        proxyClient.proxy(request, response, routes);

        OutboundHttpRequest sent = transport.requests.getFirst();
        assertThat(sent.uri())
                .isEqualTo(URI.create("http://search-service/api/portal/search/suggest?keyword=codex&next=http://169.254.169.254/latest"));
        assertThat(sent.method()).isEqualTo("GET");
        assertThat(sent.body()).isEmpty();
        assertThat(sent.policy().circuitBreakerKey()).isEqualTo("portal-bff-proxy-test");
        assertThat(sent.policy().timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(sent.headers()).containsEntry("X-Trace-Id", "trace-portal");
        assertThat(sent.headers()).containsEntry(IrcsAuthHeaders.AUTHENTICATED_USER, "member-1");
        assertThat(sent.headers()).containsEntry(IrcsAuthHeaders.AUTH_ROLE, IrcsPermissions.ROLE_MEMBER);
        assertThat(sent.headers()).containsEntry(
                IrcsAuthHeaders.AUTH_PERMISSIONS,
                "portal.interaction.read,portal.interaction.write,portal.profile.read,portal.profile.write,portal.read");
        assertThat(sent.headers()).doesNotContainKeys("Connection", "Host", "Proxy-Authorization");
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("http://portal/next");
        assertThat(response.getHeader("X-Upstream")).isEqualTo("portal");
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("redirect-body");
    }

    private static IrcsRequestPrincipal memberPrincipal() {
        return new IrcsRequestPrincipal(
                "member-1",
                IrcsPermissions.ROLE_MEMBER,
                IrcsPermissions.defaultPermissions(IrcsPermissions.ROLE_MEMBER),
                IrcsPermissions.defaultScopes(IrcsPermissions.ROLE_MEMBER),
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.ALL),
                java.util.Set.of(IrcsPermissions.ALL),
                IrcsPermissions.defaultContentVisibility(IrcsPermissions.ROLE_MEMBER));
    }

    @Test
    void forwardsPostBodyThroughSharedTransport() throws Exception {
        transport.response = response(201, Map.of("X-Upstream", List.of("interaction")), "{\"ok\":true}");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/portal/interaction/feedback");
        request.setContentType("application/json");
        request.setContent("{\"message\":\"codex\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/portal/interaction", "http://interaction-service", "/api/portal/interaction")));

        proxyClient.proxy(request, response, routes);

        OutboundHttpRequest sent = transport.requests.getFirst();
        assertThat(sent.uri()).isEqualTo(URI.create("http://interaction-service/api/portal/interaction/feedback"));
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(new String(sent.body(), StandardCharsets.UTF_8)).isEqualTo("{\"message\":\"codex\"}");
        assertThat(sent.headers()).containsEntry("Content-Type", "application/json");
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void rejectsUnknownRouteBeforeSendingRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portal/not-allowed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/portal/search", "http://search-service", "/api/portal/search")));

        assertThatThrownBy(() -> proxyClient.proxy(request, response, routes))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(transport.requests).isEmpty();
    }

    @Test
    void rejectsInvalidTargetBaseUrlBeforeSendingRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/portal/search/suggest");
        MockHttpServletResponse response = new MockHttpServletResponse();
        BffRouteTable routes = new BffRouteTable(List.of(
                new BffRoute("/api/portal/search", "http://token@search-service", "/api/portal/search")));

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
