package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;

final class MagnetOutboundHttp {

    private MagnetOutboundHttp() {
    }

    static OutboundHttpClient client(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                requireProxy(new JdkOutboundTransport()), proxyPool);
    }

    static OutboundTransport requireProxy(OutboundTransport transport) {
        return request -> {
            if (!request.policy().proxy().enabled()) {
                throw new OutboundHttpException("Magnet provider requests require a configured proxy route");
            }
            return transport.send(request);
        };
    }
}
