package com.prodigalgal.ircs.storage.image;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundStreamingTransport;
import com.prodigalgal.ircs.common.outbound.OutboundStreamingHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CoverImageOutboundConfiguration {

    @Bean
    OutboundStreamingHttpClient coverImageOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundStreamingHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundStreamingTransport(),
                proxyPool);
    }

    @Bean
    CoverImageDownloadAdapter coverImageDownloadAdapter(OutboundStreamingHttpClient httpClient) {
        return new JdkCoverImageDownloadAdapter(httpClient);
    }
}
