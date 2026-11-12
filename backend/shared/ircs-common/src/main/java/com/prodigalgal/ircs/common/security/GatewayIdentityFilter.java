package com.prodigalgal.ircs.common.security;

import com.prodigalgal.ircs.common.web.ApiErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PublicKey;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Every backend API request, including anonymous portal/auth calls, must originate at the gateway. */
public final class GatewayIdentityFilter extends OncePerRequestFilter implements Ordered {
    private final boolean gateway;
    private final PublicKey verificationKey;

    public GatewayIdentityFilter(String applicationName, String encodedKey) {
        this.gateway = "ircs-api-gateway".equals(applicationName);
        this.verificationKey = encodedKey == null || encodedKey.isBlank() ? null : GatewayIdentitySignature.publicKey(encodedKey);
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 5; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Public object keys and internal/service authentication retain their existing path semantics.
        String rawPath = request.getRequestURI();
        if (rawPath != null && (rawPath.equals("/media") || rawPath.startsWith("/media/")
                || rawPath.equals("/internal") || rawPath.startsWith("/internal/")
                || rawPath.equals("/actuator") || rawPath.startsWith("/actuator/"))) {
            chain.doFilter(request, response);
            return;
        }
        String path;
        try {
            path = SecurityRequestPaths.canonicalPath(request);
        } catch (IllegalArgumentException ex) {
            deny(request, response, HttpStatus.BAD_REQUEST, "Invalid request path");
            return;
        }
        if (gateway || !SecurityRequestPaths.gatewayApiPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (verificationKey == null) {
            deny(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Gateway identity verification is not configured");
            return;
        }
        if (!GatewayIdentitySignature.verify(request, verificationKey)) {
            deny(request, response, HttpStatus.UNAUTHORIZED, "Invalid gateway identity");
            return;
        }
        request.setAttribute(GatewayIdentitySignature.VERIFIED_ATTRIBUTE, Boolean.TRUE);
        request.setAttribute(GatewayIdentitySignature.CLIENT_IP_ATTRIBUTE, request.getHeader(GatewayIdentitySignature.CLIENT_IP));
        IrcsAuthHeaders.fromTrustedHeaders(request).ifPresent(principal -> IrcsAuthHeaders.setRequestAttribute(request, principal));
        chain.doFilter(request, response);
    }

    private static void deny(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        ApiErrorResponses.write(request, response, status, "gateway.identity.invalid", message, "auth");
    }
}
