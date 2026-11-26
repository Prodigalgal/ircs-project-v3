package com.prodigalgal.ircs.metadata.provider.imdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.common.outbound.OutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the classification of the answers IMDb actually returns to automated clients, so the AWS WAF
 * challenge can never again be mistaken for an ordinary HTTP status.
 */
class ImdbHttpClientTest {

    private static final Map<String, List<String>> WAF_CHALLENGE = Map.of("x-amzn-waf-action", List.of("challenge"));
    private static final Map<String, List<String>> AKAMAI_DENY = Map.of("Akamai-GRN", List.of("0.826e2317.1.2"));

    private final FakeResolver resolver = new FakeResolver();
    private final FakeTransport transport = new FakeTransport();
    private final ImdbProviderProperties properties = new ImdbProviderProperties();
    private ImdbHttpClient client;

    @BeforeEach
    void setUp() {
        properties.setRequestTimeout(Duration.ofSeconds(4));
        properties.setUserAgent("ircs-test-imdb");
        client = ImdbHttpClient.forTest(
                new OutboundHttpClient(new OutboundUrlPolicy(resolver), transport), properties);
    }

    @Test
    void awsWafChallengeOnTheTitlePageIsReportedAsAccessRestricted() {
        transport.enqueue(new OutboundHttpResponse(202, WAF_CHALLENGE, body("<html>challenge</html>")));

        assertThatThrownBy(() -> client.titlePage("tt0111161"))
                .isInstanceOf(MetadataProviderTerminalException.class)
                .extracting("errorCode")
                .isEqualTo("IMDB_TITLE_WAF_CHALLENGE");
    }

    @Test
    void botManagerDenyOnTheTitlePageIsReportedAsAccessRestricted() {
        transport.enqueue(new OutboundHttpResponse(403, AKAMAI_DENY, body("denied")));

        assertThatThrownBy(() -> client.titlePage("tt0111161"))
                .isInstanceOf(MetadataProviderTerminalException.class)
                .extracting("errorCode")
                .isEqualTo("IMDB_TITLE_BOT_MANAGER_DENY");
    }

    @Test
    void aServedTitlePageIsReturnedAndRequestedAsADocument() {
        transport.enqueue(new OutboundHttpResponse(200, Map.of(), body("<html>title</html>")));

        assertThat(client.titlePage("tt0111161")).contains("<html>title</html>");

        OutboundHttpRequest request = transport.requests.getFirst();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString()).isEqualTo("https://www.imdb.com/title/tt0111161/");
        assertThat(request.headers().get("Accept")).startsWith("text/html");
        assertThat(request.headers()).containsEntry("Upgrade-Insecure-Requests", "1");
    }

    @Test
    void aNotFoundTitlePageMeansDetailsAreUnavailable() {
        transport.enqueue(new OutboundHttpResponse(404, Map.of(), new byte[0]));

        assertThatThrownBy(() -> client.titlePage("tt0111161"))
                .isInstanceOf(MetadataProviderTerminalException.class)
                .extracting("errorCode")
                .isEqualTo(ImdbHttpClient.DETAIL_UNAVAILABLE);
    }

    @Test
    void aGoneTitlePageIsEmptyAndDoesNotBecomeAStaleIdFallback() {
        transport.enqueue(new OutboundHttpResponse(410, Map.of(), new byte[0]));

        assertThat(client.titlePage("tt0111161")).isEmpty();
    }

    @Test
    void rateLimitOnTheTitlePageIsLeftToTheWorkerRetrySchedule() {
        transport.enqueue(new OutboundHttpResponse(429, Map.of(), body("slow down")));

        assertThatThrownBy(() -> client.titlePage("tt0111161"))
                .isInstanceOf(MetadataProviderRetryableException.class)
                .extracting("errorCode")
                .isEqualTo("RATE_LIMITED");
        assertThat(transport.requests).hasSize(1);
    }

    @Test
    void challengedSuggestionIsReportedWithItsOwnCode() {
        transport.enqueue(new OutboundHttpResponse(202, WAF_CHALLENGE, body("<html>challenge</html>")));

        assertThatThrownBy(() -> client.suggestion(
                java.net.URI.create("https://v3.sg.media-imdb.com/suggestion/x/matrix.json")))
                .isInstanceOf(MetadataProviderTerminalException.class)
                .extracting("errorCode")
                .isEqualTo("IMDB_SUGGEST_WAF_CHALLENGE");
    }

    private byte[] body(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class FakeResolver implements OutboundAddressResolver {

        @Override
        public List<InetAddress> resolve(String host) throws UnknownHostException {
            return List.of(InetAddress.getByName("93.184.216.34"));
        }
    }

    private static final class FakeTransport implements OutboundTransport {

        private final List<OutboundHttpRequest> requests = new ArrayList<>();
        private final Queue<Object> responses = new ArrayDeque<>();

        void enqueue(Object response) {
            responses.add(response);
        }

        @Override
        public OutboundHttpResponse send(OutboundHttpRequest request) throws IOException {
            requests.add(request);
            Object response = responses.remove();
            if (response instanceof IOException ex) {
                throw ex;
            }
            return (OutboundHttpResponse) response;
        }
    }
}
