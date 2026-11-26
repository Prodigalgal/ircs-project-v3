package com.prodigalgal.ircs.common.outbound;

import java.io.IOException;
import java.util.Locale;

public class OutboundStreamingHttpClient {

    private final OutboundUrlPolicy urlPolicy;
    private final OutboundStreamingTransport transport;
    private final OutboundCircuitBreaker circuitBreaker;
    private final PublicOutboundProxyPool proxyPool;

    public OutboundStreamingHttpClient(OutboundUrlPolicy urlPolicy, OutboundStreamingTransport transport) {
        this(urlPolicy, transport, (PublicOutboundProxyPool) null);
    }

    public OutboundStreamingHttpClient(
            OutboundUrlPolicy urlPolicy,
            OutboundStreamingTransport transport,
            PublicOutboundProxyPool proxyPool) {
        this(urlPolicy, transport, new OutboundCircuitBreaker(), proxyPool);
    }

    OutboundStreamingHttpClient(
            OutboundUrlPolicy urlPolicy,
            OutboundStreamingTransport transport,
            OutboundCircuitBreaker circuitBreaker) {
        this(urlPolicy, transport, circuitBreaker, null);
    }

    private OutboundStreamingHttpClient(
            OutboundUrlPolicy urlPolicy,
            OutboundStreamingTransport transport,
            OutboundCircuitBreaker circuitBreaker,
            PublicOutboundProxyPool proxyPool) {
        this.urlPolicy = urlPolicy;
        this.transport = transport;
        this.circuitBreaker = circuitBreaker;
        this.proxyPool = proxyPool;
    }

    public OutboundStreamingHttpResponse execute(OutboundHttpRequest request)
            throws IOException, InterruptedException {
        validate(request);
        request.policy().proxy().validate();
        boolean pooled = proxyPool != null && proxyPool.appliesTo(request);
        String circuitKey = circuitKey(request);
        circuitBreaker.beforeCall(circuitKey, request.policy().circuitBreaker());
        OutboundProxy selected = null;
        try {
            selected = pooled ? proxyPool.select(request.uri()) : request.policy().proxy();
            OutboundHttpRequest routed = pooled
                    ? new OutboundHttpRequest(request.uri(), request.method(), request.headers(),
                            request.policy().withProxy(selected), request.body())
                    : request;
            OutboundStreamingHttpResponse response = transport.send(routed);
            if (pooled && (response.statusCode() == 502 || response.statusCode() == 503
                    || response.statusCode() == 504)) {
                proxyPool.recordFailure(selected);
            }
            if (isCircuitFailure(response, request.policy())) {
                circuitBreaker.recordFailure(circuitKey, request.policy().circuitBreaker(),
                        "http_status_" + response.statusCode());
            } else {
                circuitBreaker.recordSuccess(circuitKey, request.policy().circuitBreaker());
            }
            return response;
        } catch (IOException | RuntimeException ex) {
            if (pooled && selected != null) {
                proxyPool.recordFailure(selected);
            }
            circuitBreaker.recordFailure(circuitKey, request.policy().circuitBreaker(), ex.getClass().getSimpleName());
            throw ex;
        } catch (InterruptedException ex) {
            circuitBreaker.recordFailure(circuitKey, request.policy().circuitBreaker(), "InterruptedException");
            throw ex;
        }
    }

    private void validate(OutboundHttpRequest request) throws OutboundHttpException {
        switch (request.policy().type()) {
            case PUBLIC_FETCH -> urlPolicy.validatePublicFetch(request.uri(), request.policy());
            case IMAGE_DOWNLOAD_STRICT -> urlPolicy.validateImageDownloadStrict(request.uri(), request.policy());
            case INTERNAL_SERVICE -> urlPolicy.validateInternalService(request.uri());
            case API_GATEWAY_PROXY -> urlPolicy.validateApiGatewayProxyTarget(request.uri());
        }
    }

    private boolean isCircuitFailure(OutboundStreamingHttpResponse response, OutboundHttpPolicy policy) {
        return response.statusCode() >= 500 || policy.retryStatuses().contains(response.statusCode());
    }

    private String circuitKey(OutboundHttpRequest request) {
        String host = request.uri().getHost() == null
                ? "unknown"
                : request.uri().getHost().toLowerCase(Locale.ROOT);
        int port = request.uri().getPort();
        String authority = port < 0 ? host : host + ":" + port;
        String prefix = request.policy().circuitBreakerKey();
        if (prefix == null || prefix.isBlank()) {
            prefix = request.policy().type().name();
        }
        return prefix + ":" + authority;
    }
}
