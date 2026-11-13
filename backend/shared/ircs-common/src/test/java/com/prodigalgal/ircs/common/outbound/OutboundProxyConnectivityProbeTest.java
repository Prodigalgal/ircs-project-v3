package com.prodigalgal.ircs.common.outbound;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class OutboundProxyConnectivityProbeTest {

    @Test
    void unconfiguredLoopbackProxyCannotBeUsedToScanPrivatePorts() {
        OutboundProxyConnectivityProbe probe = new OutboundProxyConnectivityProbe();
        assertThatThrownBy(() -> probe.probe(OutboundProxy.http("127.0.0.1", 1, null, null), false))
                .isInstanceOf(IOException.class).hasMessageContaining("probe failed");
    }
}
