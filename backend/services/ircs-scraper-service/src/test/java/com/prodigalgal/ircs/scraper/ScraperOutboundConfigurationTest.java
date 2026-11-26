package com.prodigalgal.ircs.scraper;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScraperOutboundConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://www.imdb.com/chart/", "https://v3.sg.media-imdb.com/suggestion/",
            "https://movie.douban.com/chart", "https://www.rottentomatoes.com/browse", "https://source.example.test/list"})
    void forcesPublicRequestsThroughProxy(String url) throws Exception {
        var transport = mock(OutboundTransport.class);
        var guarded = ScraperOutboundConfiguration.requirePublicProxy(transport);
        var policy = OutboundHttpPolicy.publicFetch(Duration.ofSeconds(5));

        assertThatThrownBy(() -> guarded.send(OutboundHttpRequest.get(url, policy)))
                .isInstanceOf(OutboundHttpException.class);
        verifyNoInteractions(transport);

        var routed = OutboundHttpRequest.get(url, policy.withProxy(OutboundProxy.http("proxy.internal", 17890, null, null)));
        guarded.send(routed);
        verify(transport).send(routed);
    }
}
