package com.prodigalgal.ircs.metadata.provider.application;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.time.Duration;

public final class MetadataOutboundHttp {
    private MetadataOutboundHttp() {}

    public static OutboundHttpClient proxyOnly(Duration timeout, PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                requireProxy(new JdkOutboundTransport(timeout)), proxyPool);
    }

    static OutboundTransport requireProxy(OutboundTransport transport) {
        return request -> {
            if (!request.policy().proxy().enabled()) {
                throw new OutboundHttpException("This metadata provider requires an outbound proxy");
            }
            return transport.send(request);
        };
    }
}
