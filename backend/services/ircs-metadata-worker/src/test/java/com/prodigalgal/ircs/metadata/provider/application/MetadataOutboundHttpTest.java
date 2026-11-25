package com.prodigalgal.ircs.metadata.provider.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class MetadataOutboundHttpTest {
    @Test
    void rejectsDirectConnectionAtTheTransportBoundary() throws Exception {
        var transport = mock(OutboundTransport.class);
        var guarded = MetadataOutboundHttp.requireProxy(transport);
        var policy = OutboundHttpPolicy.publicFetch(Duration.ofSeconds(5));
        var direct = OutboundHttpRequest.get("https://www.imdb.com/title/tt0111161/", policy);

        assertThatThrownBy(() -> guarded.send(direct)).isInstanceOf(OutboundHttpException.class);
        verifyNoInteractions(transport);

        var routed = OutboundHttpRequest.get(direct.uri(),
                policy.withProxy(OutboundProxy.http("proxy.internal", 17890, null, null)));
        guarded.send(routed);
        verify(transport).send(routed);
    }
}
