package com.prodigalgal.ircs.catalog;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundTransportRouter;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class CatalogFetchSampleClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OutboundHttpClient httpClient;

    CatalogFetchSampleClient(
            ObjectProvider<OutboundHttpClient> httpClient,
            ObjectProvider<PublicOutboundProxyPool> proxyPoolProvider) {
        PublicOutboundProxyPool proxyPool = proxyPoolProvider == null ? null : proxyPoolProvider.getIfAvailable();
        this.httpClient = httpClient == null
                ? defaultHttpClient(proxyPool)
                : httpClient.getIfAvailable(() -> defaultHttpClient(proxyPool));
    }

    static CatalogFetchSampleClient forTest(OutboundHttpClient httpClient) {
        return new CatalogFetchSampleClient(new ObjectProvider<>() {
            @Override
            public OutboundHttpClient getObject() {
                return httpClient;
            }
        }, null);
    }

    String get(String url) throws IOException, InterruptedException {
        return get(url, null);
    }

    String get(String url, SourceNetworkOptions options) throws IOException, InterruptedException {
        OutboundHttpPolicy policy = options == null ? OutboundHttpPolicy.publicFetch(TIMEOUT) : options.toPolicy();
        OutboundHttpResponse response = httpClient.execute(OutboundHttpRequest.get(url, policy));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        String body = response.bodyAsUtf8();
        return looksLikeJson(body) ? body : null;
    }

    private boolean looksLikeJson(String body) {
        if (body == null) {
            return false;
        }
        for (int i = 0; i < body.length(); i++) {
            char candidate = body.charAt(i);
            if (candidate == '\uFEFF' || Character.isWhitespace(candidate)) {
                continue;
            }
            return candidate == '{' || candidate == '[';
        }
        return false;
    }

    private static OutboundHttpClient defaultHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new OutboundTransportRouter(),
                proxyPool);
    }
}
