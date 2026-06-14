package com.prodigalgal.ircs.adminbff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import com.prodigalgal.ircs.common.web.ApiErrorResponses;
import com.prodigalgal.ircs.common.web.CommonApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

class AdminBffErrorEnvelopeTest {

    @Test
    void rendersUnifiedEnvelopeForBffOwnedErrors() throws Exception {
        BffProxyClient proxyClient = mock(BffProxyClient.class);
        AdminBffRoutes routes = routes();
        IrcsJwtRequestAuthenticator authenticator = mock(IrcsJwtRequestAuthenticator.class);
        when(authenticator.requireRoleAndPermission(
                any(HttpServletRequest.class),
                eq(IrcsPermissions.ROLE_ADMIN),
                eq(IrcsPermissions.ADMIN_ACCESS)))
                .thenReturn(adminPrincipal());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No BFF route for /api/v1/not-allowed"))
                .when(proxyClient)
                .proxy(any(HttpServletRequest.class), any(HttpServletResponse.class), same(routes));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminBffProxyController(proxyClient, routes, authenticator))
                .setControllerAdvice(new CommonApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/v1/not-allowed")
                        .header(ApiErrorResponses.TRACE_HEADER, "trace-admin")
                        .header(ApiErrorResponses.CORRELATION_HEADER, "corr-admin"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(ApiErrorResponses.TRACE_HEADER, "trace-admin"))
                .andExpect(header().string(ApiErrorResponses.CORRELATION_HEADER, "corr-admin"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("http.404"))
                .andExpect(jsonPath("$.message").value("No BFF route for /api/v1/not-allowed"))
                .andExpect(jsonPath("$.entity").value("http"))
                .andExpect(jsonPath("$.path").value("/api/v1/not-allowed"))
                .andExpect(jsonPath("$.traceId").value("trace-admin"))
                .andExpect(jsonPath("$.correlationId").value("corr-admin"));
    }

    private static IrcsRequestPrincipal adminPrincipal() {
        return new IrcsRequestPrincipal(
                "admin",
                IrcsPermissions.ROLE_ADMIN,
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.SCOPE_ADMIN_ALL, IrcsPermissions.SCOPE_DATA_ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL),
                Set.of(IrcsPermissions.ALL));
    }

    private static AdminBffRoutes routes() {
        return new AdminBffRoutes(
                "http://identity",
                "http://content",
                "http://catalog",
                "http://task",
                "http://ops",
                "http://storage",
                "http://config",
                "http://credential",
                "http://magnet",
                "http://scraper",
                "http://interaction");
    }
}
