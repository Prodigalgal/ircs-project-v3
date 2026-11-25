package com.prodigalgal.ircs.metadata.provider.application;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.net.URI;
import java.time.Duration;

public final class MetadataOutboundHttp {
    private static final String PAGE_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private static final String JSON_ACCEPT = "application/json, text/plain, */*";

    private MetadataOutboundHttp() {}

    public static OutboundHttpClient proxyOnly(Duration timeout, PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                requireProxy(new JdkOutboundTransport(timeout)), proxyPool);
    }

    public static OutboundHttpRequest browserGet(URI uri, OutboundHttpPolicy policy, boolean json) {
        var request = OutboundHttpRequest.get(uri, policy)
                .withHeader("Accept", json ? JSON_ACCEPT : PAGE_ACCEPT)
                .withHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        return json ? request : request.withHeader("Upgrade-Insecure-Requests", "1");
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
