package com.prodigalgal.ircs.metadata.provider.imdb;

import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import com.prodigalgal.ircs.metadata.provider.application.MetadataOutboundHttp;
import com.prodigalgal.ircs.metadata.provider.application.MetadataProviderTrafficLimiter;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class ImdbHttpClient {
    static final String DETAIL_UNAVAILABLE = "IMDB_DETAIL_UNAVAILABLE";
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final OutboundHttpClient client;
    private final PublicOutboundProxyPool proxyPool;
    private final ImdbProviderProperties properties;
    private final MetadataProviderTrafficLimiter trafficLimiter;

    ImdbHttpClient(
            ImdbProviderProperties properties,
            MetadataProviderTrafficLimiter trafficLimiter,
            ObjectProvider<PublicOutboundProxyPool> proxies) {
        this.properties = properties;
        this.trafficLimiter = trafficLimiter;
        this.proxyPool = proxies.getIfAvailable();
        this.client = MetadataOutboundHttp.proxyOnly(properties.getRequestTimeout(), proxyPool);
    }

    Optional<String> get(URI uri, boolean details) {
        try {
            trafficLimiter.acquireProviderSlot("IMDB");
            var policy = OutboundHttpPolicy.publicFetch(properties.getRequestTimeout())
                    .withUserAgent(properties.getUserAgent())
                    .withProxy(proxyPool == null ? OutboundProxy.disabled() : proxyPool.customProxy("app.metadata.imdb"));
            var response = client.execute(OutboundHttpRequest.get(uri, policy)
                    .withHeader("Accept", details ? "text/html" : "application/json"));
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return Optional.empty();
            }
            if (details && (status == 202 || status == 401 || status == 403)) {
                throw detailsUnavailable();
            }
            if (status == 429 || status >= 500) {
                throw new MetadataProviderRetryableException("IMDB_HTTP_" + status, "IMDb is temporarily unavailable");
            }
            if (status != 200) {
                throw new MetadataProviderTerminalException("IMDB_HTTP_" + status, "IMDb returned HTTP " + status);
            }
            if (response.body().length > MAX_BODY_BYTES) {
                throw new MetadataProviderRetryableException("IMDB_RESPONSE_TOO_LARGE", "IMDb response exceeds size limit");
            }
            String body = response.bodyAsUtf8();
            if (body.isBlank()) {
                if (details) {
                    throw detailsUnavailable();
                }
                throw new MetadataProviderRetryableException("IMDB_EMPTY_RESPONSE", "IMDb search returned no response body");
            }
            return Optional.of(body);
        } catch (IOException ex) {
            throw new MetadataProviderRetryableException("IMDB_OUTBOUND_UNAVAILABLE", "IMDb outbound request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetadataProviderRetryableException("IMDB_OUTBOUND_INTERRUPTED", "IMDb outbound request interrupted", ex);
        }
    }

    static MetadataProviderTerminalException detailsUnavailable() {
        return new MetadataProviderTerminalException(DETAIL_UNAVAILABLE, "IMDb did not serve a public title page");
    }
}
