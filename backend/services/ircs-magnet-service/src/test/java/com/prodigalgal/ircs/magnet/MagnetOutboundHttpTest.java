package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MagnetOutboundHttpTest {

    @Test
    void refusesToSendWithoutASelectedProxy() {
        OutboundTransport transport = mock(OutboundTransport.class);
        OutboundHttpRequest request = OutboundHttpRequest.get(
                "https://source.example/search", OutboundHttpPolicy.publicFetch(Duration.ofSeconds(5)));

        assertThrows(OutboundHttpException.class, () -> MagnetOutboundHttp.requireProxy(transport).send(request));
        verifyNoInteractions(transport);
    }

    @Test
    void delegatesProxiedRequestsToTheSharedTransport() throws Exception {
        OutboundTransport transport = mock(OutboundTransport.class);
        OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(Duration.ofSeconds(5))
                .withProxy(OutboundProxy.http("proxy.example", 8080, null, null));
        OutboundHttpRequest request = OutboundHttpRequest.get("https://source.example/search", policy);

        MagnetOutboundHttp.requireProxy(transport).send(request);

        verify(transport).send(request);
    }
}
