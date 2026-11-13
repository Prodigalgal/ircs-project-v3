package com.prodigalgal.ircs.config.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import com.prodigalgal.ircs.common.outbound.OutboundProxyConnectivityProbe;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ConfigConnectivityServiceTest {

    private final PublicOutboundProxyPool pool = org.mockito.Mockito.mock(PublicOutboundProxyPool.class);
    private final OutboundProxyConnectivityProbe probe = org.mockito.Mockito.mock(OutboundProxyConnectivityProbe.class);
    private final ConfigConnectivityService service = new ConfigConnectivityService(pool, probe);

    @Test
    void dryRunDoesNotClaimAnExternalConnectionWasTested() {
        Map<String, Object> result = service.testConnection("llm", Map.of("api_key", "secret"));
        assertEquals("LLM", result.get("type"));
        assertEquals("dry-run", result.get("mode"));
        assertEquals("NOT_TESTED", result.get("status"));
        assertEquals(false, result.get("success"));
        verifyNoInteractions(probe);
    }

    @Test
    void configuredGatewayUsesRealFixedTargetProbeAndReturnsBooleanSuccess() throws Exception {
        when(pool.isConfiguredEndpoint("oracle.gateway", 17890)).thenReturn(true);
        when(probe.probe(any(), eq(true))).thenReturn(204);
        Map<String, Object> result = service.testConnection("PROXY", Map.of(
                "app.metadata.tmdb.proxy.host", "oracle.gateway", "app.metadata.tmdb.proxy.port", "17890",
                "app.metadata.tmdb.proxy.username", "private-user", "app.metadata.tmdb.proxy.password", "secret",
                "url", "http://127.0.0.1/admin"));
        assertEquals(true, result.get("success"));
        assertEquals("live", result.get("mode"));
        assertEquals(OutboundProxyConnectivityProbe.TARGET, result.get("target"));
        assertFalse(result.toString().contains("secret"));
        assertFalse(result.toString().contains("private-user"));
        verify(probe).probe(OutboundProxy.http("oracle.gateway", 17890, "private-user", "secret"), true);
    }

    @Test
    void failureDoesNotLeakProxyAuthenticationOrClaimSuccess() throws Exception {
        when(probe.probe(any(), eq(false))).thenThrow(new IOException("private-user:secret"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
                service.testConnection("PROXY", Map.of("host", "203.0.113.10", "port", 8080)));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertFalse(error.getReason().contains("secret"));
    }

    @Test
    void invalidPortAndUnsupportedProxyTypeAreRejectedBeforeProbe() {
        assertThrows(ResponseStatusException.class, () -> service.testConnection("PROXY", Map.of("host", "x", "port", 0)));
        assertThrows(ResponseStatusException.class, () -> service.testConnection("PROXY", Map.of("host", "x", "port", 1, "type", "SOCKS5")));
        verifyNoInteractions(probe);
    }

    @Test
    void rejectsUnknownConnectionType() {
        assertThrows(IllegalArgumentException.class, () -> service.testConnection("unknown", Map.of()));
    }
}
