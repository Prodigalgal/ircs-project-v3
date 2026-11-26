package com.prodigalgal.ircs.common.outbound;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** HTTP gateways for public fetches and strict public image downloads. Configuration reuses SystemConfig L1/L2/L3. */
@Component
public class PublicOutboundProxyPool {

    public static final String ENABLED_KEY = "app.outbound.proxy-pool.enabled";
    public static final String ENDPOINTS_KEY = "app.outbound.proxy-pool.endpoints";
    public static final String AUTHORITY_ENDPOINTS_KEY = "app.outbound.proxy-pool.authority-endpoints";
    public static final String COOLDOWN_KEY = "app.outbound.proxy-pool.failure-cooldown";
    public static final String DEFAULT_ENDPOINTS =
            "http://ircs-proxy-oracle.ircs-prod.svc.cluster.local:17890,http://ircs-proxy-cf.ircs-prod.svc.cluster.local:17890";
    public static final String DEFAULT_AUTHORITY_ENDPOINTS =
            "http://ircs-proxy-oracle.ircs-prod.svc.cluster.local:17890";
    private static final Set<String> AUTHORITY_DOMAINS =
            Set.of("imdb.com", "media-imdb.com", "douban.com", "rottentomatoes.com");
    private final RuntimeConfigService config;
    private final AtomicLong cursor = new AtomicLong();
    private final Map<String, Long> unavailableUntil = new ConcurrentHashMap<>();
    private final Map<String, OutboundProxy> customProxies = new ConcurrentHashMap<>();
    private volatile boolean previouslyEnabled;

    public PublicOutboundProxyPool(RuntimeConfigService config) {
        this.config = config;
    }

    public boolean appliesTo(OutboundHttpRequest request) throws OutboundHttpException {
        OutboundHttpPolicy policy = request.policy();
        boolean strictPublicImage = policy.type() == OutboundHttpPolicyType.IMAGE_DOWNLOAD_STRICT
                && policy.blockPrivateAddresses();
        if ((policy.type() != OutboundHttpPolicyType.PUBLIC_FETCH && !strictPublicImage)
                || policy.proxy().enabled()) {
            return false;
        }
        if (isAuthority(request.uri())) {
            return true;
        }
        Optional<String> enabled = config.value(ENABLED_KEY);
        if (enabled.isEmpty()) {
            if (previouslyEnabled) {
                throw new OutboundHttpException("Public proxy pool configuration unavailable; direct fallback is disabled");
            }
            return false;
        }
        String value = enabled.orElseThrow().trim().toLowerCase(Locale.ROOT);
        boolean active = switch (value) {
            case "true", "1", "yes", "on", "enabled" -> true;
            case "false", "0", "no", "off", "disabled" -> false;
            default -> throw new OutboundHttpException("Invalid public proxy pool enable setting");
        };
        previouslyEnabled = active;
        return active;
    }

    public OutboundProxy select() throws OutboundHttpException {
        return select(null);
    }

    /** Applies the destination's routing configuration without consulting the other pool. */
    public OutboundProxy select(URI uri) throws OutboundHttpException {
        List<OutboundProxy> proxies = isAuthority(uri) ? authorityEndpoints() : configuredEndpoints();
        int start = (int) Math.floorMod(cursor.getAndIncrement(), (long) proxies.size());
        long now = System.nanoTime();
        for (int offset = 0; offset < proxies.size(); offset++) {
            OutboundProxy proxy = proxies.get((start + offset) % proxies.size());
            Long until = unavailableUntil.get(key(proxy));
            if (until == null || now - until >= 0L) {
                return proxy;
            }
        }
        throw new OutboundHttpException("All public proxy gateways are cooling down; direct fallback is disabled");
    }

    private static boolean isAuthority(URI uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        String domain = normalized.endsWith(".") ? normalized.substring(0, normalized.length() - 1) : normalized;
        return AUTHORITY_DOMAINS.stream().anyMatch(suffix -> domain.equals(suffix) || domain.endsWith("." + suffix));
    }

    public void recordFailure(OutboundProxy proxy) {
        Duration cooldown = config.positiveDurationValue(COOLDOWN_KEY, Duration.ofSeconds(30));
        Duration bounded = cooldown.compareTo(Duration.ofMinutes(10)) > 0 ? Duration.ofMinutes(10) : cooldown;
        long now = System.nanoTime();
        unavailableUntil.entrySet().removeIf(entry -> now - entry.getValue() >= 0L);
        unavailableUntil.put(key(proxy), now + bounded.toNanos());
    }

    public List<OutboundProxy> configuredEndpoints() throws OutboundHttpException {
        return parseEndpoints(config.stringValue(ENDPOINTS_KEY, DEFAULT_ENDPOINTS));
    }

    /** Blank or missing authority settings use the Oracle gateway. */
    private List<OutboundProxy> authorityEndpoints() throws OutboundHttpException {
        String configured = config.stringValue(AUTHORITY_ENDPOINTS_KEY, DEFAULT_AUTHORITY_ENDPOINTS);
        return parseEndpoints(configured == null || configured.isBlank() ? DEFAULT_AUTHORITY_ENDPOINTS : configured);
    }

    private static List<OutboundProxy> parseEndpoints(String configured) throws OutboundHttpException {
        List<String> endpoints = Arrays.stream(configured.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (endpoints.isEmpty() || endpoints.size() > 16) {
            throw new OutboundHttpException("Public proxy pool requires between 1 and 16 HTTP gateway endpoints");
        }
        java.util.ArrayList<OutboundProxy> proxies = new java.util.ArrayList<>();
        for (String endpoint : endpoints) {
            try {
                URI uri = URI.create(endpoint);
                if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                        || uri.getPort() < 1 || uri.getPort() > 65535 || uri.getUserInfo() != null
                        || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                    throw new IllegalArgumentException();
                }
                proxies.add(OutboundProxy.http(uri.getHost(), uri.getPort(), null, null));
            } catch (IllegalArgumentException ex) {
                throw new OutboundHttpException("Invalid public proxy HTTP gateway endpoint");
            }
        }
        return List.copyOf(proxies);
    }

    public boolean isConfiguredEndpoint(String host, int port) throws OutboundHttpException {
        return configuredEndpoints().stream().anyMatch(proxy -> proxy.host().equalsIgnoreCase(host) && proxy.port() == port);
    }

    public OutboundProxy customProxy(String providerPrefix) throws OutboundHttpException {
        String prefix = providerPrefix + ".proxy.";
        Optional<String> enabled = config.value(prefix + "enabled");
        if (enabled.isEmpty()) {
            return customProxies.getOrDefault(providerPrefix, OutboundProxy.disabled());
        }
        String flag = enabled.orElseThrow().trim().toLowerCase(Locale.ROOT);
        boolean active = switch (flag) {
            case "true", "1", "yes", "on", "enabled" -> true;
            case "false", "0", "no", "off", "disabled" -> false;
            default -> throw new OutboundHttpException("Invalid provider proxy enable setting");
        };
        if (!active) {
            customProxies.remove(providerPrefix);
            return OutboundProxy.disabled();
        }
        if (!"HTTP".equalsIgnoreCase(config.stringValue(prefix + "type", "HTTP"))) {
            throw new OutboundHttpException("Only HTTP provider proxies are supported");
        }
        OutboundProxy proxy = OutboundProxy.http(config.stringValue(prefix + "host", ""),
                config.intValue(prefix + "port", 0), config.stringValue(prefix + "username", ""),
                config.stringValue(prefix + "password", ""));
        proxy.validate();
        customProxies.put(providerPrefix, proxy);
        return proxy;
    }

    private static String key(OutboundProxy proxy) {
        return proxy.host().toLowerCase(Locale.ROOT) + ":" + proxy.port();
    }
}
