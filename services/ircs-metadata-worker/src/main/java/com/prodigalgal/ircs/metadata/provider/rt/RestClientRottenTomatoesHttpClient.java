package com.prodigalgal.ircs.metadata.provider.rt;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.metadata.provider.MetadataProviderTrafficLimiter;
import com.prodigalgal.ircs.metadata.provider.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.MetadataProviderTerminalException;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RestClientRottenTomatoesHttpClient implements RottenTomatoesHttpClient {

    private static final String PROVIDER_CODE = "ROTTEN_TOMATOES";

    private OutboundHttpClient httpClient;
    private final RottenTomatoesProviderProperties properties;
    private final MetadataProviderTrafficLimiter trafficLimiter;

    @Autowired
    public RestClientRottenTomatoesHttpClient(
            RottenTomatoesProviderProperties properties,
            ObjectProvider<MetadataProviderTrafficLimiter> trafficLimiterProvider) {
        this(properties, trafficLimiterProvider.getIfAvailable(MetadataProviderTrafficLimiter::noop));
    }

    private RestClientRottenTomatoesHttpClient(
            RottenTomatoesProviderProperties properties,
            MetadataProviderTrafficLimiter trafficLimiter) {
        this.httpClient = new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundTransport(properties.getRequestTimeout()));
        this.properties = properties;
        this.trafficLimiter = trafficLimiter == null ? MetadataProviderTrafficLimiter.noop() : trafficLimiter;
    }

    static RestClientRottenTomatoesHttpClient forTest(
            OutboundHttpClient httpClient,
            RottenTomatoesProviderProperties properties) {
        RestClientRottenTomatoesHttpClient client = new RestClientRottenTomatoesHttpClient(
                properties,
                MetadataProviderTrafficLimiter.noop());
        client.httpClient = httpClient;
        return client;
    }

    @Override
    public Optional<String> getHtml(URI uri, Optional<RottenTomatoesCredential> credential) {
        try {
            trafficLimiter.acquireProviderSlot(PROVIDER_CODE);
            OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(properties.getRequestTimeout())
                    .withUserAgent(credential
                            .map(RottenTomatoesCredential::userAgent)
                            .filter(StringUtils::hasText)
                            .orElse(properties.getDefaultUserAgent()));
            OutboundHttpRequest request = OutboundHttpRequest.get(uri, policy)
                    .withHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            if (credential.isPresent() && StringUtils.hasText(credential.get().cookie())) {
                request = request.withHeader("Cookie", credential.get().cookie());
            }
            OutboundHttpResponse response = httpClient.execute(request);
            int status = response.statusCode();
            String body = response.bodyAsUtf8();
            if (status >= 200 && status < 300) {
                return body == null || body.isBlank() ? Optional.empty() : Optional.of(body);
            }
            if (status == 404) {
                return Optional.empty();
            }
            if (status == 429) {
                throw new MetadataProviderRetryableException("RATE_LIMITED", "Rotten Tomatoes rate limited request");
            }
            if (status >= 500 && status < 600) {
                throw new MetadataProviderRetryableException(
                        "UPSTREAM_UNAVAILABLE",
                        "Rotten Tomatoes upstream returned " + status);
            }
            if (status == 403) {
                throw new MetadataProviderTerminalException("RT_FORBIDDEN", "Rotten Tomatoes returned 403");
            }
            throw new MetadataProviderTerminalException("HTTP_" + status, "Rotten Tomatoes returned " + status);
        } catch (MetadataProviderRetryableException | MetadataProviderTerminalException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetadataProviderRetryableException("RT_OUTBOUND_INTERRUPTED", "Rotten Tomatoes outbound request interrupted", ex);
        } catch (IOException ex) {
            throw new MetadataProviderRetryableException("RT_OUTBOUND_UNAVAILABLE", "Rotten Tomatoes outbound request failed", ex);
        }
    }
}
