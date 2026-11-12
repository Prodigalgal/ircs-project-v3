package com.prodigalgal.ircs.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class GatewayClientAddressTest {
    @Test
    void ignoresForwardedHeadersFromUntrustedPeer() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.3");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        assertThat(GatewayClientAddress.resolve(request, "10.244.0.0/16")).isEqualTo("192.0.2.3");
        assertThat(GatewayClientAddress.resolve(request, "")).isEqualTo("192.0.2.3");
    }

    @Test
    void usesEnvoyAppendedLastHopInsteadOfClientSuppliedFirstHop() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.244.1.143");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.7");
        assertThat(GatewayClientAddress.resolve(request, "10.244.0.0/16")).isEqualTo("203.0.113.7");
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "attacker.example");
        assertThat(GatewayClientAddress.resolve(request, "10.244.0.0/16")).isEqualTo("10.244.1.143");
    }
}
