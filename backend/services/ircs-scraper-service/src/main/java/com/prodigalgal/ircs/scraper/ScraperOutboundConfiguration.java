package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import com.prodigalgal.ircs.common.outbound.OutboundTransportRouter;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScraperOutboundConfiguration {

    @Bean
    OutboundHttpClient scraperOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new OutboundTransportRouter(), proxyPool);
    }
}
