package com.prodigalgal.ircs.portalbff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prodigalgal.ircs.common.security.IrcsAuthException;
import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import com.prodigalgal.ircs.common.web.CommonApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PortalBffAuthGateTest {

    @Test
    void permitsPublicPortalReadWithoutToken() throws Exception {
        BffProxyClient proxyClient = mock(BffProxyClient.class);
        IrcsJwtRequestAuthenticator authenticator = mock(IrcsJwtRequestAuthenticator.class);
        when(authenticator.authenticateIfPresent(any(HttpServletRequest.class))).thenReturn(Optional.empty());

        mvc(proxyClient, authenticator)
                .perform(get("/api/portal/home"))
                .andExpect(status().isOk());

        verify(authenticator, never()).requirePermission(any(HttpServletRequest.class), any(String.class));
        verify(proxyClient).proxy(any(HttpServletRequest.class), any(HttpServletResponse.class), any(PortalBffRoutes.class));
    }

    @Test
    void rejectsProfileReadWithoutToken() throws Exception {
        BffProxyClient proxyClient = mock(BffProxyClient.class);
        IrcsJwtRequestAuthenticator authenticator = mock(IrcsJwtRequestAuthenticator.class);
        when(authenticator.requirePermission(
                any(HttpServletRequest.class),
                eq(IrcsPermissions.PORTAL_PROFILE_READ)))
                .thenThrow(new IrcsAuthException(IrcsAuthException.Reason.MISSING, "缺少登录凭证"));

        mvc(proxyClient, authenticator)
                .perform(get("/api/portal/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.token.missing"));

        verify(proxyClient, never()).proxy(any(HttpServletRequest.class), any(HttpServletResponse.class), any(PortalBffRoutes.class));
    }

    @Test
    void protectsBackendInteractionWritesWithPermissionCode() throws Exception {
        BffProxyClient proxyClient = mock(BffProxyClient.class);
        IrcsJwtRequestAuthenticator authenticator = mock(IrcsJwtRequestAuthenticator.class);
        when(authenticator.requirePermission(
                any(HttpServletRequest.class),
                eq(IrcsPermissions.PORTAL_INTERACTION_WRITE)))
                .thenReturn(memberPrincipal());

        mvc(proxyClient, authenticator)
                .perform(post("/api/backend/api/portal/interaction/history"))
                .andExpect(status().isOk());

        verify(authenticator).requirePermission(
                any(HttpServletRequest.class),
                eq(IrcsPermissions.PORTAL_INTERACTION_WRITE));
        verify(proxyClient).proxy(any(HttpServletRequest.class), any(HttpServletResponse.class), any(PortalBffRoutes.class));
    }

    private static MockMvc mvc(BffProxyClient proxyClient, IrcsJwtRequestAuthenticator authenticator) {
        return MockMvcBuilders
                .standaloneSetup(new PortalBffProxyController(proxyClient, routes(), authenticator))
                .setControllerAdvice(new CommonApiExceptionHandler())
                .build();
    }

    private static IrcsRequestPrincipal memberPrincipal() {
        return new IrcsRequestPrincipal(
                "member-1",
                IrcsPermissions.ROLE_MEMBER,
                Set.of(IrcsPermissions.PORTAL_INTERACTION_WRITE),
                Set.of(IrcsPermissions.SCOPE_INTERACTION_WRITE),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.VISIBILITY_PUBLIC, IrcsPermissions.VISIBILITY_MEMBER));
    }

    private static PortalBffRoutes routes() {
        return new PortalBffRoutes(
                "http://identity",
                "http://interaction",
                "http://portal",
                "http://search",
                "http://storage");
    }
}
