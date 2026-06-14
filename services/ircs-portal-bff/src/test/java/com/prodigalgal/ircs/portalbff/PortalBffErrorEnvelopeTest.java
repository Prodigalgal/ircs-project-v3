package com.prodigalgal.ircs.portalbff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.web.ApiErrorResponses;
import com.prodigalgal.ircs.common.web.CommonApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;

class PortalBffErrorEnvelopeTest {

    @Test
    void rendersUnifiedEnvelopeForBffOwnedErrors() throws Exception {
        BffProxyClient proxyClient = mock(BffProxyClient.class);
        PortalBffRoutes routes = routes();
        IrcsJwtRequestAuthenticator authenticator = mock(IrcsJwtRequestAuthenticator.class);
        when(authenticator.authenticateIfPresent(any(HttpServletRequest.class))).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid BFF target base URL"))
                .when(proxyClient)
                .proxy(any(HttpServletRequest.class), any(HttpServletResponse.class), same(routes));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PortalBffProxyController(proxyClient, routes, authenticator))
                .setControllerAdvice(new CommonApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/portal/search/suggest")
                        .header(ApiErrorResponses.TRACE_HEADER, "trace-portal")
                        .header(ApiErrorResponses.CORRELATION_HEADER, "corr-portal"))
                .andExpect(status().isBadGateway())
                .andExpect(header().string(ApiErrorResponses.TRACE_HEADER, "trace-portal"))
                .andExpect(header().string(ApiErrorResponses.CORRELATION_HEADER, "corr-portal"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.code").value("http.502"))
                .andExpect(jsonPath("$.message").value("Invalid BFF target base URL"))
                .andExpect(jsonPath("$.entity").value("http"))
                .andExpect(jsonPath("$.path").value("/api/portal/search/suggest"))
                .andExpect(jsonPath("$.traceId").value("trace-portal"))
                .andExpect(jsonPath("$.correlationId").value("corr-portal"));
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
