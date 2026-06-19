package com.prodigalgal.ircs.adminbff;

import com.prodigalgal.ircs.common.security.IrcsAuthException;
import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class AdminBffAuthenticator {

    private final AdminApiTokenService apiTokenService;
    private final IrcsJwtRequestAuthenticator jwtAuthenticator;

    AdminBffAuthenticator(
            AdminApiTokenService apiTokenService,
            IrcsJwtRequestAuthenticator jwtAuthenticator) {
        this.apiTokenService = apiTokenService;
        this.jwtAuthenticator = jwtAuthenticator;
    }

    Optional<IrcsRequestPrincipal> authenticateIfPresent(HttpServletRequest request) {
        Optional<IrcsRequestPrincipal> apiTokenPrincipal = apiTokenService.authenticateIfPresent(request);
        return apiTokenPrincipal.isPresent()
                ? apiTokenPrincipal
                : jwtAuthenticator.authenticateIfPresent(request);
    }

    IrcsRequestPrincipal requireRoleAndPermission(HttpServletRequest request, String role, String permission) {
        IrcsRequestPrincipal principal = authenticateIfPresent(request)
                .orElseThrow(() -> new IrcsAuthException(IrcsAuthException.Reason.MISSING, "缺少登录凭证"));
        String requiredRole = IrcsPermissions.normalizeRole(role);
        if (!requiredRole.equals(principal.role())) {
            throw new IrcsAuthException(IrcsAuthException.Reason.FORBIDDEN, "权限不足");
        }
        if (!principal.hasPermission(permission)) {
            throw new IrcsAuthException(IrcsAuthException.Reason.FORBIDDEN, "权限不足");
        }
        return principal;
    }
}
