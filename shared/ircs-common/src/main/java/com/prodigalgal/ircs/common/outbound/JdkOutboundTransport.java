package com.prodigalgal.ircs.common.outbound;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class JdkOutboundTransport implements OutboundTransport {

    private static final Set<String> JDK_RESTRICTED_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "upgrade");

    private final Duration connectTimeout;
    private final HttpClient fixedHttpClient;

    public JdkOutboundTransport() {
        this(null, null);
    }

    public JdkOutboundTransport(Duration connectTimeout) {
        this(connectTimeout, null);
    }

    public JdkOutboundTransport(HttpClient httpClient) {
        this(null, httpClient);
    }

    private JdkOutboundTransport(Duration connectTimeout, HttpClient fixedHttpClient) {
        this.connectTimeout = connectTimeout;
        this.fixedHttpClient = fixedHttpClient;
    }

    @Override
    public OutboundHttpResponse send(OutboundHttpRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(request.policy().timeout());
        applyHttpVersion(builder, request.policy().httpProtocol());
        request.headers().forEach((name, value) -> {
            if (!isRestrictedHeader(name)) {
                builder.header(name, value);
            }
        });
        byte[] body = request.body();
        builder.method(request.method(), body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        HttpResponse<byte[]> response = httpClient(request).send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Map<String, List<String>> headers = response.headers().map();
        return new OutboundHttpResponse(response.statusCode(), headers, response.body());
    }

    private HttpClient httpClient(OutboundHttpRequest request) throws OutboundHttpException {
        if (fixedHttpClient != null) {
            return fixedHttpClient;
        }
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER);
        Duration timeout = connectTimeout == null ? request.policy().timeout() : connectTimeout;
        builder.connectTimeout(timeout);

        OutboundProxy proxy = request.policy().proxy();
        proxy.validate();
        if (proxy.enabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (proxy.hasCredentials()) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                proxy.username(),
                                (proxy.password() == null ? "" : proxy.password()).toCharArray());
                    }
                });
            }
        }
        return builder.build();
    }

    private void applyHttpVersion(HttpRequest.Builder builder, String protocol) {
        if ("HTTP_2".equalsIgnoreCase(protocol)) {
            builder.version(HttpClient.Version.HTTP_2);
        } else if ("HTTP_1_1".equalsIgnoreCase(protocol) || "HTTP_1_0".equalsIgnoreCase(protocol)) {
            builder.version(HttpClient.Version.HTTP_1_1);
        }
    }

    private boolean isRestrictedHeader(String name) {
        return name != null && JDK_RESTRICTED_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
