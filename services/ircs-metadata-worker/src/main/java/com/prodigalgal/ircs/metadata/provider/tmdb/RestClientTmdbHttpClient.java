package com.prodigalgal.ircs.metadata.provider.tmdb;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.JdkOutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.metadata.provider.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.MetadataProviderTerminalException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RestClientTmdbHttpClient implements TmdbHttpClient {

    private OutboundHttpClient httpClient;
    private final Duration timeout;

    public RestClientTmdbHttpClient(TmdbProviderProperties properties) {
        this.httpClient = new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                new JdkOutboundTransport(properties.getRequestTimeout()));
        this.timeout = properties.getRequestTimeout();
    }

    static RestClientTmdbHttpClient forTest(OutboundHttpClient httpClient, Duration timeout) {
        TmdbProviderProperties properties = new TmdbProviderProperties();
        properties.setRequestTimeout(timeout);
        RestClientTmdbHttpClient client = new RestClientTmdbHttpClient(properties);
        client.httpClient = httpClient;
        return client;
    }

    @Override
    public Optional<String> getJson(URI uri) {
        try {
            OutboundHttpResponse response = httpClient.execute(OutboundHttpRequest.get(
                    uri.toString(),
                    OutboundHttpPolicy.publicFetch(timeout)));
            int status = response.statusCode();
            String body = response.bodyAsUtf8();
            if (status >= 200 && status < 300) {
                return body == null || body.isBlank() ? Optional.empty() : Optional.of(body);
            }
            if (status == 404) {
                return Optional.empty();
            }
            if (status == 429) {
                throw new MetadataProviderRetryableException("RATE_LIMITED", "TMDB rate limited request");
            }
            if (status >= 500 && status < 600) {
                throw new MetadataProviderRetryableException(
                        "UPSTREAM_UNAVAILABLE",
                        "TMDB upstream returned " + status);
            }
            throw new MetadataProviderTerminalException("HTTP_" + status, "TMDB returned " + status);
        } catch (MetadataProviderRetryableException | MetadataProviderTerminalException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MetadataProviderRetryableException("TMDB_OUTBOUND_INTERRUPTED", "TMDB outbound request interrupted", ex);
        } catch (IOException ex) {
            throw new MetadataProviderRetryableException("TMDB_OUTBOUND_UNAVAILABLE", "TMDB outbound request failed", ex);
        }
    }
}
