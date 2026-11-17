package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUserAgents;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
class TrendProviderHttpClient {

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
            OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(configValues.providerTimeout())
                    .withUserAgent(OutboundUserAgents.defaultBrowser())
                    .withCallerCircuitBreakerKey("scraper-trend-provider");
            OutboundHttpRequest request = OutboundHttpRequest.get(url, policy)
                    .withHeader("Accept", "text/html,application/json")
                    .withHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.6");
            OutboundHttpResponse response = httpClient.execute(request);
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
