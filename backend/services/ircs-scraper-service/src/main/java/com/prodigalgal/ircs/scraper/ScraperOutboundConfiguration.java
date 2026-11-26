package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicyType;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundTransportRouter;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScraperOutboundConfiguration {

    @Bean
    OutboundHttpClient scraperOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                requirePublicProxy(new OutboundTransportRouter()),
                proxyPool);
    }

    static OutboundTransport requirePublicProxy(OutboundTransport transport) {
        return request -> {
            if (request.policy().type() == OutboundHttpPolicyType.PUBLIC_FETCH && !request.policy().proxy().enabled()) {
                throw new OutboundHttpException("Scraper requests require an outbound proxy");
            }
            return transport.send(request);
        };
    }
}
