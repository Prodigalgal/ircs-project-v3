package com.prodigalgal.ircs.common.outbound;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recognises the anti-automation responses the catalog sites return instead of the page a caller
 * asked for.
 *
 * <p>Each site answers with a different shape, and none of them is an ordinary error: IMDb answers
 * {@code 202 Accepted} with an AWS WAF JavaScript challenge, Douban answers {@code 302} to
 * {@code sec.douban.com}, and Rotten Tomatoes answers {@code 403} stamped by Akamai. Treating those
 * as generic HTTP failures is what turned a routing problem into an unrecoverable one, because the
 * same request retried within the same session always fails again.
 */
public final class OutboundChallengeDetector {

    public static final String WAF_CHALLENGE = "WAF_CHALLENGE";
    public static final String SECURITY_REDIRECT = "SECURITY_REDIRECT";
    public static final String BOT_MANAGER_DENY = "BOT_MANAGER_DENY";
    public static final String RATE_LIMITED = "RATE_LIMITED";

    private static final String AWS_WAF_ACTION = "x-amzn-waf-action";
    private static final String AKAMAI_TRANSACTION = "akamai-grn";
    private static final String DOUBAN_SECURITY_HOST = "sec.douban.com";

    private OutboundChallengeDetector() {
    }

    public static Optional<String> detect(OutboundHttpResponse response) {
        if (response == null) {
            return Optional.empty();
        }
        int status = response.statusCode();
        Map<String, List<String>> headers = response.headers();
        if (status == 429) {
            return Optional.of(RATE_LIMITED);
        }
        Optional<String> wafAction = header(headers, AWS_WAF_ACTION);
        if (wafAction.isPresent() && (status == 202 || status == 403)) {
            return Optional.of(WAF_CHALLENGE);
        }
        if (status >= 300 && status < 400 && pointsAtDoubanSecurity(headers)) {
            return Optional.of(SECURITY_REDIRECT);
        }
        if (status == 403) {
            if (header(headers, AKAMAI_TRANSACTION).isPresent()) {
                return Optional.of(BOT_MANAGER_DENY);
            }
            if (mentionsDoubanSecurity(response.body())) {
                return Optional.of(SECURITY_REDIRECT);
            }
        }
        return Optional.empty();
    }

    public static boolean isChallenge(String code) {
        return WAF_CHALLENGE.equals(code) || SECURITY_REDIRECT.equals(code) || BOT_MANAGER_DENY.equals(code);
    }

    /** A challenge is a permanent answer for an unauthenticated client, not a transient outage. */
    public static boolean isTransient(String code) {
        return RATE_LIMITED.equals(code);
    }

    public static Optional<String> header(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().equalsIgnoreCase(name) || entry.getValue().isEmpty()) {
                continue;
            }
            String value = entry.getValue().getFirst();
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private static boolean pointsAtDoubanSecurity(Map<String, List<String>> headers) {
        Optional<String> location = header(headers, "Location");
        if (location.isEmpty()) {
            return false;
        }
        try {
            String host = URI.create(location.orElseThrow()).getHost();
            return host != null && DOUBAN_SECURITY_HOST.equalsIgnoreCase(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean mentionsDoubanSecurity(byte[] body) {
        if (body == null || body.length == 0 || body.length > 8192) {
            return false;
        }
        String text = new String(body, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return text.contains(DOUBAN_SECURITY_HOST) || text.contains("please login");
    }
}
