package com.prodigalgal.ircs.common.outbound;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

final class JdkHttpClientResolver {

    private final Duration connectTimeout;
    private final HttpClient fixedHttpClient;
    private static final int MAX_PROXY_CLIENTS = 32;
    private final java.util.LinkedHashMap<ProxyClientKey, HttpClient> proxyClients =
            new java.util.LinkedHashMap<>(16, 0.75f, true);
    private volatile HttpClient sharedDirectClient;
    private volatile Duration sharedDirectClientTimeout;

    JdkHttpClientResolver(Duration connectTimeout, HttpClient fixedHttpClient) {
        this.connectTimeout = connectTimeout;
        this.fixedHttpClient = fixedHttpClient;
    }

    HttpClient resolve(OutboundHttpRequest request) throws OutboundHttpException {
        if (fixedHttpClient != null) {
            return fixedHttpClient;
        }
        Duration timeout = connectTimeout == null ? request.policy().timeout() : connectTimeout;
        OutboundProxy proxy = request.policy().proxy();
        proxy.validate();
        if (!proxy.enabled()) {
            return sharedDirectClient(timeout);
        }
        return proxyClient(timeout, proxy);
    }

    private HttpClient proxyClient(Duration timeout, OutboundProxy proxy) {
        ProxyClientKey key = new ProxyClientKey(timeout, proxy.host(), proxy.port(), proxy.username(), proxy.password());
        synchronized (proxyClients) {
            HttpClient client = proxyClients.get(key);
            if (client != null) {
                return client;
            }
            client = buildClient(timeout, proxy);
            proxyClients.put(key, client);
            if (proxyClients.size() > MAX_PROXY_CLIENTS) {
                ProxyClientKey oldest = proxyClients.keySet().iterator().next();
                HttpClient evicted = proxyClients.remove(oldest);
                evicted.shutdown();
            }
            return client;
        }
    }

    private record ProxyClientKey(Duration timeout, String host, Integer port, String username, String password) {}

    private HttpClient sharedDirectClient(Duration timeout) {
        HttpClient existing = sharedDirectClient;
        if (existing != null && timeout.equals(sharedDirectClientTimeout)) {
            return existing;
        }
        synchronized (this) {
            if (sharedDirectClient == null || !timeout.equals(sharedDirectClientTimeout)) {
                sharedDirectClient = buildClient(timeout, OutboundProxy.disabled());
                sharedDirectClientTimeout = timeout;
            }
            return sharedDirectClient;
        }
    }

    private HttpClient buildClient(Duration timeout, OutboundProxy proxy) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout);
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
}
