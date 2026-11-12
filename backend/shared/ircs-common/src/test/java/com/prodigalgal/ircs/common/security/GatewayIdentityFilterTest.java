package com.prodigalgal.ircs.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayIdentityFilterTest {
    private final KeyPair keys = keys();
    private final GatewayIdentityFilter filter = new GatewayIdentityFilter("ircs-config-service",
            Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()));

    @Test
    void blocksDirectApiEvenWithCompleteForgedAdminHeaders() throws Exception {
        var request = request("/api/v1/configs");
        principalHeaders().forEach(request::addHeader);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        assertThat(IrcsAuthHeaders.fromTrustedHeaders(request)).isEmpty();
    }

    @Test
    void acceptsSignedAdminAndPopulatesVerifiedPrincipalAndClientIp() throws Exception {
        var request = signed("/api/v1/configs", principalHeaders());
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(IrcsAuthHeaders.fromRequestAttribute(request).orElseThrow().role()).isEqualTo("ROLE_ADMIN");
        assertThat(request.getAttribute(GatewayIdentitySignature.CLIENT_IP_ATTRIBUTE)).isEqualTo("203.0.113.9");
    }

    @Test
    void preservesAnonymousPortalAuthAndPublicReadThroughGateway() throws Exception {
        for (String path : new String[]{"/api/portal/videos", "/api/v1/auth/pow", "/api/portal/auth/login"}) {
            var request = signed(path, new LinkedHashMap<>());
            var chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
            assertThat(IrcsAuthHeaders.principalOrPublic(request).isAnonymous()).isTrue();
        }
    }

    @Test
    void rejectsAlteredMethodPathQueryIdentityClientIpAndDuplicateHeaders() throws Exception {
        for (String alteration : new String[]{"method", "path", "query", "role", "ip", "duplicate"}) {
            var request = signed("/api/v1/configs", principalHeaders());
            switch (alteration) {
                case "method" -> request.setMethod("DELETE");
                case "path" -> request.setRequestURI("/api/v1/members");
                case "query" -> request.setQueryString("admin=true");
                case "role" -> { request.removeHeader(IrcsAuthHeaders.AUTH_ROLE); request.addHeader(IrcsAuthHeaders.AUTH_ROLE, "ROLE_MEMBER"); }
                case "ip" -> { request.removeHeader(GatewayIdentitySignature.CLIENT_IP); request.addHeader(GatewayIdentitySignature.CLIENT_IP, "203.0.113.10"); }
                case "duplicate" -> request.addHeader(IrcsAuthHeaders.AUTH_ROLE, "ROLE_ADMIN");
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).as(alteration).isEqualTo(401);
        }
    }

    @Test
    void rejectsExpiredOrFutureSignatureEvenWithValidCryptographicProof() throws Exception {
        for (long issued : new long[]{Instant.now().getEpochSecond() - 60, Instant.now().getEpochSecond() + 60}) {
            Map<String, String> headers = principalHeaders();
            headers.put(GatewayIdentitySignature.CLIENT_IP, "203.0.113.9");
            headers.put(GatewayIdentitySignature.TIMESTAMP, Long.toString(issued));
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(keys.getPrivate());
            signer.update(GatewayIdentitySignature.canonical("GET", "/api/v1/configs", headers::get));
            headers.put(GatewayIdentitySignature.SIGNATURE, Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
            var request = request("/api/v1/configs");
            headers.forEach(request::addHeader);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    void missingKeyFailsClosedButMediaHealthAndInternalRemainOutsideGatewayBoundary() throws Exception {
        var missingKey = new GatewayIdentityFilter("ircs-storage-service", "");
        var response = new MockHttpServletResponse();
        missingKey.doFilter(request("/api/v1/cover-images"), response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
        for (String path : new String[]{"/media/cover/1.jpg", "/actuator/health", "/internal/v1/maintenance"}) {
            var request = request(path);
            var chain = new MockFilterChain();
            missingKey.doFilter(request, new MockHttpServletResponse(), chain);
            assertThat(chain.getRequest()).isSameAs(request);
        }
        var gateway = new GatewayIdentityFilter("ircs-api-gateway", "");
        var chain = new MockFilterChain();
        gateway.doFilter(request("/api/v1/auth/login"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void catchesMvcPathAliasesAndRejectsAmbiguousPaths() throws Exception {
        for (String path : new String[]{"/api/v1/configs/", "/api/v1/configs;x=1", "/api/%76%31/configs", "/api//v1/configs"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, new MockFilterChain());
            assertThat(response.getStatus()).as(path).isEqualTo(401);
        }
        for (String path : new String[]{"/api/v1/../configs", "/api/v1/%252fconfigs"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request(path), response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(400);
        }
    }

    private MockHttpServletRequest signed(String path, Map<String, String> headers) {
        GatewayIdentitySignature.sign(headers, "GET", path, "203.0.113.9", keys.getPrivate());
        var request = request(path);
        headers.forEach(request::addHeader);
        return request;
    }

    private static MockHttpServletRequest request(String path) { return new MockHttpServletRequest("GET", path); }

    private static Map<String, String> principalHeaders() {
        var headers = new LinkedHashMap<String, String>();
        IrcsAuthHeaders.writePrincipal(headers, new IrcsRequestPrincipal("admin", "ROLE_ADMIN",
                Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*")));
        return headers;
    }

    private static KeyPair keys() {
        try { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
