package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DecisionOutboundConfiguration {

    @Bean
    OutboundHttpClient decisionOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundTransport(Duration.ofSeconds(5)),
                proxyPool);
    }
}
