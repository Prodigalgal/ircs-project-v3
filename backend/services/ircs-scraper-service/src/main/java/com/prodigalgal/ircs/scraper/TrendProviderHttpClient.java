package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.OutboundChallengeDetector;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUserAgents;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class TrendProviderHttpClient {
    private static final String PAGE_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private static final String JSON_ACCEPT = "application/json, text/plain, */*";

    private final OutboundHttpClient httpClient;
    private final ScraperTrendConfigValues configValues;

    TrendProviderHttpClient(
            OutboundHttpClient httpClient,
            ScraperTrendConfigValues configValues) {
        this.httpClient = httpClient;
        this.configValues = configValues;
    }

    String get(String url) {
        if (Thread.currentThread().isInterrupted()) {
            throw new TrendProviderFetchException("FETCH_INTERRUPTED");
        }
        try {
            URI uri = URI.create(url);
            boolean json = uri.getPath().startsWith("/j/") || "api.themoviedb.org".equalsIgnoreCase(uri.getHost());
            OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(configValues.providerTimeout())
                    .withMaxRetries(0)
                    .withUserAgent(OutboundUserAgents.defaultBrowser())
                    .withCallerCircuitBreakerKey("scraper-trend-provider");
            OutboundHttpRequest request = OutboundHttpRequest.get(uri, policy)
                    .withHeader("Accept", json ? JSON_ACCEPT : PAGE_ACCEPT)
                    .withHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6");
            if (!json) {
                request = request.withHeader("Upgrade-Insecure-Requests", "1");
            }
            OutboundHttpResponse response = httpClient.execute(request);
            Optional<String> challenge = OutboundChallengeDetector.detect(response);
            if (challenge.isPresent()) {
                throw new TrendProviderFetchException(challenge.orElseThrow());
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TrendProviderFetchException("HTTP_" + response.statusCode());
            }
            if (response.body().length > 2_000_000) {
                throw new TrendProviderFetchException("RESPONSE_TOO_LARGE");
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TrendProviderFetchException("FETCH_INTERRUPTED");
        } catch (TrendProviderFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            // Transport exceptions may contain credential-bearing URLs; expose only a fixed code.
            throw new TrendProviderFetchException("FETCH_FAILED");
        }
    }
}
