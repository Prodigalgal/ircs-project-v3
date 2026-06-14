package com.prodigalgal.ircs.adminbff;

import com.prodigalgal.ircs.common.audit.BffRequestAuditWriter;
import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundStreamingTransport;
import com.prodigalgal.ircs.common.outbound.OutboundCircuitBreakerConfig;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.InternalServiceAuthHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
class BffProxyClient {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private final OutboundStreamingHttpClient outboundClient;
    private final BffRequestAuditWriter auditWriter;
    private final OutboundUrlPolicy targetUrlPolicy;
    private final Duration requestTimeout;
    private final String circuitKey;
    private final OutboundCircuitBreakerConfig circuitBreakerConfig;
    private final String serviceId;
    private final String opsServiceToken;
    private final String opsServiceScopes;
    BffProxyClient(
            @Value("${app.bff.proxy.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.bff.proxy.request-timeout:PT5S}") Duration requestTimeout,
            @Value("${app.bff.proxy.circuit-key:admin-bff-proxy}") String circuitKey,
            @Value("${app.bff.proxy.circuit.enabled:true}") boolean circuitEnabled,
            @Value("${app.bff.proxy.circuit.failure-threshold:5}") int failureThreshold,
            @Value("${app.bff.proxy.circuit.open-duration:PT30S}") Duration openDuration,
            @Value("${app.bff.proxy.circuit.half-open-max-calls:1}") int halfOpenMaxCalls,
            @Value("${app.bff.service-identity.service-id:admin-bff}") String serviceId,
            @Value("${app.bff.service-identity.ops-token:${APP_BFF_OPS_SERVICE_TOKEN:}}") String opsServiceToken,
            @Value("${app.bff.service-identity.ops-scopes:ops:read ops:run}") String opsServiceScopes,
            BffRequestAuditWriter auditWriter,
            ObjectProvider<OutboundStreamingHttpClient> outboundClientProvider,
            ObjectProvider<OutboundUrlPolicy> targetUrlPolicyProvider) {
        this.outboundClient = outboundClient(outboundClientProvider, connectTimeout);
        this.auditWriter = auditWriter;
        this.targetUrlPolicy = targetUrlPolicy(targetUrlPolicyProvider);
        this.requestTimeout = requestTimeout;
        this.circuitKey = circuitKey;
        this.circuitBreakerConfig = new OutboundCircuitBreakerConfig(
                circuitEnabled,
                failureThreshold,
                openDuration,
                halfOpenMaxCalls);
        this.serviceId = serviceId;
        this.opsServiceToken = opsServiceToken;
        this.opsServiceScopes = opsServiceScopes;
    }

    void proxy(HttpServletRequest request, HttpServletResponse response, BffRouteTable routes) throws IOException {
        Instant startedAt = Instant.now();
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        String errorMessage = null;
        try {
            ResolvedRoute route = routes.resolve(request.getRequestURI())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found"));
            URI target = targetUri(route, request.getQueryString());
            OutboundHttpRequest outbound = outboundRequest(request, target);
            try (OutboundStreamingHttpResponse upstream = outboundClient.execute(outbound)) {
                statusCode = upstream.statusCode();
                writeResponse(upstream, response);
            }
        } catch (ResponseStatusException ex) {
            statusCode = ex.getStatusCode().value();
            errorMessage = ex.getReason();
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            errorMessage = "BFF proxy interrupted";
            throw new IOException("BFF proxy interrupted", ex);
        } catch (OutboundHttpException ex) {
            statusCode = HttpStatus.BAD_GATEWAY.value();
            errorMessage = ex.getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "BFF proxy upstream unavailable", ex);
        } catch (IOException | RuntimeException ex) {
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            auditWriter.record(request, statusCode, Duration.between(startedAt, Instant.now()), errorMessage);
        }
    }

    private URI targetUri(ResolvedRoute route, String query) {
        validateTargetBaseUrl(route.targetBaseUrl());
        String value = route.targetBaseUrl() + route.targetPath();
        if (query != null && !query.isBlank()) {
            value += "?" + query;
        }
        return URI.create(value);
    }

    private void validateTargetBaseUrl(String targetBaseUrl) {
        try {
            targetUrlPolicy.validateBffProxyTarget(URI.create(targetBaseUrl));
        } catch (IllegalArgumentException | OutboundHttpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid BFF target base URL", ex);
        }
    }

    private OutboundHttpRequest outboundRequest(HttpServletRequest request, URI target) throws IOException {
        Map<String, String> headers = requestHeaders(request);
        String method = request.getMethod();
        OutboundHttpPolicy policy = OutboundHttpPolicy.bffProxy(requestTimeout)
                .withCircuitBreaker(circuitBreakerConfig)
                .withCircuitBreakerKey(circuitKey);
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return new OutboundHttpRequest(target, method, headers, policy, new byte[0]);
        }
        byte[] body = request.getInputStream().readAllBytes();
        return new OutboundHttpRequest(target, method, headers, policy, body);
    }

    private Map<String, String> requestHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.merge(name, values.nextElement(), (left, right) -> left + "," + right);
            }
        }
        IrcsAuthHeaders.removeTrustedIdentityHeaders(headers);
        removeInternalServiceHeaders(headers);
        IrcsAuthHeaders.fromRequestAttribute(request)
                .ifPresent(principal -> IrcsAuthHeaders.writePrincipal(headers, principal));
        if (shouldAttachOpsIdentity(request.getRequestURI()) && StringUtils.hasText(opsServiceToken)) {
            headers.put(InternalServiceAuthHeaders.SERVICE_ID, serviceId);
            headers.put(InternalServiceAuthHeaders.SERVICE_TOKEN, opsServiceToken.trim());
            headers.put(InternalServiceAuthHeaders.SERVICE_SCOPES, opsServiceScopes);
        }
        return headers;
    }

    private boolean shouldAttachOpsIdentity(String path) {
        return path.startsWith("/api/v1/ops")
                || path.startsWith("/api/v1/dashboard")
                || path.startsWith("/api/v1/debug");
    }

    private void removeInternalServiceHeaders(Map<String, String> headers) {
        headers.keySet().removeIf(name ->
                name.equalsIgnoreCase(InternalServiceAuthHeaders.SERVICE_ID)
                        || name.equalsIgnoreCase(InternalServiceAuthHeaders.SERVICE_TOKEN)
                        || name.equalsIgnoreCase(InternalServiceAuthHeaders.SERVICE_SCOPES));
    }

    private void writeResponse(OutboundStreamingHttpResponse upstream, HttpServletResponse response) throws IOException {
        response.setStatus(upstream.statusCode());
        upstream.headers().forEach((name, values) -> {
            if (HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                response.addHeader(name, value);
            }
        });
        try (OutputStream output = response.getOutputStream()) {
            upstream.body().transferTo(output);
            output.flush();
        }
    }

    private static OutboundStreamingHttpClient outboundClient(
            ObjectProvider<OutboundStreamingHttpClient> outboundClientProvider,
            Duration connectTimeout) {
        if (outboundClientProvider != null) {
            OutboundStreamingHttpClient provided = outboundClientProvider.getIfUnique();
            if (provided != null) {
                return provided;
            }
        }
        return new OutboundStreamingHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundStreamingTransport(connectTimeout));
    }

    private static OutboundUrlPolicy targetUrlPolicy(ObjectProvider<OutboundUrlPolicy> targetUrlPolicyProvider) {
        if (targetUrlPolicyProvider != null) {
            OutboundUrlPolicy provided = targetUrlPolicyProvider.getIfUnique();
            if (provided != null) {
                return provided;
            }
        }
        return new OutboundUrlPolicy(new DefaultOutboundAddressResolver());
    }
}
