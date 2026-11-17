package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NormalizationOutboundConfiguration {

    @Bean
    OutboundHttpClient normalizationOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundTransport(),
                proxyPool);
    }
}
