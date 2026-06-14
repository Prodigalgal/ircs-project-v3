package com.prodigalgal.ircs.portalbff;

import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PortalBffProxyController {

    private final BffProxyClient proxyClient;
    private final PortalBffRoutes routes;
    private final IrcsJwtRequestAuthenticator authenticator;

    PortalBffProxyController(
            BffProxyClient proxyClient,
            PortalBffRoutes routes,
            IrcsJwtRequestAuthenticator authenticator) {
        this.proxyClient = proxyClient;
        this.routes = routes;
        this.authenticator = authenticator;
    }

    @RequestMapping({"/api/backend/**", "/api/portal/**", "/media/**"})
    void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<String> requiredPermission = requiredPermission(request);
        if (requiredPermission.isPresent()) {
            IrcsAuthHeaders.setRequestAttribute(
                    request,
                    authenticator.requirePermission(request, requiredPermission.get()));
        } else {
            authenticator.authenticateIfPresent(request)
                    .filter(principal -> principal.hasPermission(IrcsPermissions.PORTAL_READ))
                    .ifPresent(principal -> IrcsAuthHeaders.setRequestAttribute(request, principal));
        }
        proxyClient.proxy(request, response, routes);
    }

    private static Optional<String> requiredPermission(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return Optional.empty();
        }
        String path = normalizedPortalPath(request.getRequestURI());
        String method = request.getMethod();
        if (path.startsWith("/api/portal/profile")) {
            return Optional.of(isRead(method)
                    ? IrcsPermissions.PORTAL_PROFILE_READ
                    : IrcsPermissions.PORTAL_PROFILE_WRITE);
        }
        if (path.startsWith("/api/portal/interaction")) {
            return Optional.of(isRead(method)
                    ? IrcsPermissions.PORTAL_INTERACTION_READ
                    : IrcsPermissions.PORTAL_INTERACTION_WRITE);
        }
        if (path.startsWith("/api/portal/feedback/wall")) {
            return Optional.empty();
        }
        if (path.startsWith("/api/portal/feedback")) {
            return Optional.of(isRead(method)
                    ? IrcsPermissions.PORTAL_INTERACTION_READ
                    : IrcsPermissions.PORTAL_INTERACTION_WRITE);
        }
        return Optional.empty();
    }

    private static String normalizedPortalPath(String path) {
        if (path != null && path.startsWith("/api/backend")) {
            return path.substring("/api/backend".length());
        }
        return path == null ? "" : path;
    }

    private static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }
}
