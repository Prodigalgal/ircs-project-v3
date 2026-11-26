package com.prodigalgal.ircs.metadata.provider.imdb;

import com.prodigalgal.ircs.common.outbound.OutboundChallengeDetector;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
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

    private final PublicOutboundProxyPool proxyPool;
    private final ImdbProviderProperties properties;
    private final MetadataProviderTrafficLimiter trafficLimiter;
    private OutboundHttpClient client;

    ImdbHttpClient(
            ImdbProviderProperties properties,
            MetadataProviderTrafficLimiter trafficLimiter,
            ObjectProvider<PublicOutboundProxyPool> proxies) {
        this.properties = properties;
        this.trafficLimiter = trafficLimiter;
        this.proxyPool = proxies == null ? null : proxies.getIfAvailable();
        this.client = MetadataOutboundHttp.proxyOnly(properties.getRequestTimeout(), proxyPool);
    }

    static ImdbHttpClient forTest(OutboundHttpClient client, ImdbProviderProperties properties) {
        ImdbHttpClient instance =
                new ImdbHttpClient(properties, MetadataProviderTrafficLimiter.noop(), null);
        instance.client = client;
        return instance;
    }

    Optional<String> suggestion(URI uri) {
        try {
            trafficLimiter.acquireProviderSlot("IMDB");
            OutboundHttpResponse response = client.execute(
                    MetadataOutboundHttp.browserGet(uri, policy(), true));
            int status = response.statusCode();
            if (status == 404 || status == 410) {
                return Optional.empty();
            }
            requireUsable(response, "IMDB_SUGGEST");
            String body = response.bodyAsUtf8();
            if (body.isBlank()) {
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

    /** Reads a public title page. A verified search candidate can provide partial metadata when details are missing. */
    Optional<String> titlePage(String imdbId) {
        URI uri = URI.create(properties.getDetailBaseUrl() + "/" + imdbId + "/");
        try {
            trafficLimiter.acquireProviderSlot("IMDB");
            OutboundHttpResponse response = client.execute(
                    MetadataOutboundHttp.browserGet(uri, policy(), false));
            int status = response.statusCode();
            if (status == 404) {
                throw detailsUnavailable();
            }
            if (status == 410) {
                return Optional.empty();
            }
            requireUsable(response, "IMDB_TITLE");
            return Optional.of(response.bodyAsUtf8());
        } catch (IOException ex) {
            throw new MetadataProviderRetryableException("IMDB_OUTBOUND_UNAVAILABLE", "IMDb outbound request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetadataProviderRetryableException("IMDB_OUTBOUND_INTERRUPTED", "IMDb outbound request interrupted", ex);
        }
    }

    private OutboundHttpPolicy policy() throws OutboundHttpException {
        return OutboundHttpPolicy.publicFetch(properties.getRequestTimeout())
                .withMaxRetries(0)
                .withUserAgent(properties.getUserAgent())
                .withProxy(proxyPool == null ? OutboundProxy.disabled() : proxyPool.customProxy("app.metadata.imdb"));
    }

    private void requireUsable(OutboundHttpResponse response, String codePrefix) {
        int status = response.statusCode();
        Optional<String> challenge = OutboundChallengeDetector.detect(response);
        if (challenge.isPresent() && OutboundChallengeDetector.isChallenge(challenge.orElseThrow())) {
            throw new MetadataProviderTerminalException(
                    codePrefix + "_" + challenge.orElseThrow(), "IMDb answered with an anti-automation challenge");
        }
        if (status == 429) {
            throw new MetadataProviderRetryableException("RATE_LIMITED", "IMDb rate limited request");
        }
        if (status >= 500) {
            throw new MetadataProviderRetryableException("IMDB_HTTP_" + status, "IMDb is temporarily unavailable");
        }
        if (status != 200) {
            throw new MetadataProviderTerminalException("IMDB_HTTP_" + status, "IMDb returned HTTP " + status);
        }
        if (response.body().length > MAX_BODY_BYTES) {
            throw new MetadataProviderRetryableException("IMDB_RESPONSE_TOO_LARGE", "IMDb response exceeds size limit");
        }
    }

    static MetadataProviderTerminalException detailsUnavailable() {
        return new MetadataProviderTerminalException(DETAIL_UNAVAILABLE, "IMDb did not serve a public title page");
    }
}
