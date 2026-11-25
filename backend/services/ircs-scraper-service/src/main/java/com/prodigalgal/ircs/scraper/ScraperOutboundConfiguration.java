package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.common.outbound.DefaultOutboundAddressResolver;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpException;
import com.prodigalgal.ircs.common.outbound.OutboundTransport;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import com.prodigalgal.ircs.common.outbound.OutboundTransportRouter;
import com.prodigalgal.ircs.common.outbound.OutboundUrlPolicy;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ScraperOutboundConfiguration {
    private static final Set<String> AUTHORITIES_REQUIRING_PROXY =
            Set.of("imdb.com", "media-imdb.com", "douban.com", "rottentomatoes.com");

    @Bean
    OutboundHttpClient scraperOutboundHttpClient(PublicOutboundProxyPool proxyPool) {
        return new OutboundHttpClient(
                new OutboundUrlPolicy(new DefaultOutboundAddressResolver()),
                protectAuthorityRequests(new OutboundTransportRouter()), proxyPool);
    }

    static OutboundTransport protectAuthorityRequests(OutboundTransport transport) {
        return request -> {
            String host = request.uri().getHost();
            if (!request.policy().proxy().enabled() && host != null && requiresProxy(host)) {
                throw new OutboundHttpException("Authority requests require an outbound proxy");
            }
            return transport.send(request);
        };
    }

    private static boolean requiresProxy(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String authority = normalized;
        return AUTHORITIES_REQUIRING_PROXY.stream()
                .anyMatch(domain -> authority.equals(domain) || authority.endsWith("." + domain));
    }
}
