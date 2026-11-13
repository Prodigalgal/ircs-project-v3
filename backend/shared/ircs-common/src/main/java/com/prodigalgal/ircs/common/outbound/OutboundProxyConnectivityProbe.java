package com.prodigalgal.ircs.common.outbound;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class OutboundProxyConnectivityProbe {

    public static final String TARGET = "https://www.gstatic.com/generate_204";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private final OutboundAddressResolver resolver = new DefaultOutboundAddressResolver();
    private final OutboundUrlPolicy urlPolicy = new OutboundUrlPolicy(resolver);
    private final OutboundHttpClient client = new OutboundHttpClient(urlPolicy, new JdkOutboundTransport(TIMEOUT));

    public int probe(OutboundProxy proxy, boolean configuredInternalGateway) throws IOException, InterruptedException {
        FutureTask<Integer> operation = new FutureTask<>(() -> {
            proxy.validate();
            if (!proxy.enabled()) {
                throw new OutboundHttpException("A proxy endpoint is required for connectivity testing");
            }
            OutboundProxy connectionProxy = proxy;
            if (!configuredInternalGateway) {
                URI proxyUri = new URI("http", null, proxy.host(), proxy.port(), null, null, null);
                java.util.List<java.net.InetAddress> addresses = resolver.resolve(proxy.host());
                new OutboundUrlPolicy(ignored -> addresses)
                        .validatePublicFetch(proxyUri, OutboundHttpPolicy.publicFetch(TIMEOUT));
                // Pin the validated endpoint so a second DNS answer cannot redirect the probe into a private subnet.
                connectionProxy = OutboundProxy.http(addresses.getFirst().getHostAddress(), proxy.port(),
                        proxy.username(), proxy.password());
            }
            OutboundHttpPolicy policy = OutboundHttpPolicy.publicFetch(TIMEOUT).withMaxRetries(0)
                    .withProxy(connectionProxy).withCircuitBreaker(OutboundCircuitBreakerConfig.disabled());
            return client.execute(OutboundHttpRequest.get(TARGET, policy)).statusCode();
        });
        Thread worker = Thread.ofVirtual().name("ircs-proxy-connectivity-probe").start(operation);
        try {
            return operation.get(6, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new OutboundHttpException("Proxy HTTPS connectivity probe timed out");
        } catch (ExecutionException ex) {
            throw new OutboundHttpException("Proxy HTTPS connectivity probe failed");
        } finally {
            operation.cancel(true);
            worker.interrupt();
        }
    }
}
