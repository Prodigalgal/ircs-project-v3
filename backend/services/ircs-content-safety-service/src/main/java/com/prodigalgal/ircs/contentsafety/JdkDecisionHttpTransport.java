package com.prodigalgal.ircs.contentsafety;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import org.springframework.stereotype.Component;

@Component
class JdkDecisionHttpTransport implements DecisionHttpTransport {
    private final OutboundHttpClient client;

    JdkDecisionHttpTransport(OutboundHttpClient client) {
        this.client = client;
    }

    @Override
    public Response exchange(String method, URI uri, Map<String, String> headers, String body, Duration timeout)
            throws IOException, InterruptedException {
        OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(timeout)
                .withMaxRetries(0)
                .withCallerCircuitBreakerKey("content-safety-google-decisions");
        OutboundHttpRequest request = new OutboundHttpRequest(uri, method, headers, policy,
                body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        OutboundHttpResponse response = client.execute(request);
        return new Response(response.statusCode(), response.bodyAsUtf8(), response.headers());
    }
}
