package com.prodigalgal.ircs.magnet;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import com.prodigalgal.ircs.common.outbound.OutboundHttpClient;
import com.prodigalgal.ircs.common.outbound.OutboundHttpPolicy;
import com.prodigalgal.ircs.common.outbound.OutboundHttpRequest;
import com.prodigalgal.ircs.common.outbound.OutboundHttpResponse;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class GenericMagnetProviderSearchRunner implements MagnetProviderSearchRunner {

    static final String HTTP_ERROR = "GENERIC_MAGNET_HTTP_ERROR";
    static final String ACCESS_RESTRICTED = "GENERIC_MAGNET_ACCESS_RESTRICTED";
    static final String PROVIDER_ERROR = "GENERIC_MAGNET_PROVIDER_ERROR";
    private static final String PARSE_FAILURE = "GENERIC_MAGNET_PARSE_FAILURE";
    private static final String TIMEOUT = "GENERIC_MAGNET_TIMEOUT";
    private static final String UNSUPPORTED_QUERY = "GENERIC_MAGNET_UNSUPPORTED_QUERY";
    private static final Set<String> JSON_ERROR_FIELDS = Set.of("error", "errors", "error_description");
    private static final Set<String> JSON_ERROR_STATUSES = Set.of("error", "failed", "failure", "denied");
    private static final List<String> CHALLENGE_MARKERS = List.of(
            "cf-mitigated", "challenges.cloudflare.com", "just a moment", "enable javascript and cookies to continue");
    private static final List<String> RESTRICTED_PAGE_MARKERS = List.of("captcha", "access denied");
    private static final List<String> PROXY_ERROR_MARKERS = List.of(
            "bad gateway", "proxy error", "upstream connect error", "gateway timeout", "service unavailable");
    private static final List<String> EMPTY_RESULT_MARKERS = List.of(
            "no results", "nothing found", "no torrents", "0 torrents", "no matching torrents");

    private final MagnetProviderTrafficLimiter trafficLimiter;
    private final ObjectMapper objectMapper;
    private final GenericMagnetHttpClient httpClient;
    private final GenericMagnetRequestUrlBuilder requestUrlBuilder;
    private final GenericMagnetCandidateParser candidateParser;

    GenericMagnetProviderSearchRunner(
            ObjectMapper objectMapper,
            MagnetProviderTrafficLimiter trafficLimiter,
            ObjectProvider<GenericMagnetHttpClient> httpClientProvider,
            ObjectProvider<PublicOutboundProxyPool> proxyPoolProvider) {
        this.trafficLimiter = trafficLimiter;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        PublicOutboundProxyPool proxyPool = proxyPoolProvider == null ? null : proxyPoolProvider.getIfAvailable();
        this.httpClient = httpClient(httpClientProvider, proxyPool);
        this.requestUrlBuilder = new GenericMagnetRequestUrlBuilder();
        this.candidateParser = new GenericMagnetCandidateParser(this.objectMapper);
    }

    @Override
    public MagnetProviderSearchResult search(
            MagnetProviderSummary provider,
            MagnetExternalIdQuery query,
            UUID unifiedVideoId) {
        if (!isSupportedQuery(query)) {
            throw new MagnetProviderRunnerException(UNSUPPORTED_QUERY, null, null);
        }
        String requestUrl = requestUrlBuilder.build(provider, query);
        GenericMagnetHttpResponse response = execute(provider, requestUrl);
        if (isAccessRestricted(response)) {
            throw new MagnetProviderRunnerException(ACCESS_RESTRICTED, requestUrl, response.statusCode());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new MagnetProviderRunnerException(HTTP_ERROR, requestUrl, response.statusCode());
        }
        try {
            validateResponseBody(response.body(), requestUrl, response.statusCode());
            List<MagnetProviderCandidate> candidates = candidateParser.parse(provider, query, requestUrl, response.body());
            return new MagnetProviderSearchResult(requestUrl, response.statusCode(), candidates);
        } catch (MagnetProviderRunnerException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, response.statusCode());
        }
    }

    private void validateResponseBody(String body, String requestUrl, int statusCode) throws IOException {
        if (!StringUtils.hasText(body)) {
            throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            JsonNode root;
            try {
                root = objectMapper.readTree(trimmed);
            } catch (Exception ex) {
                throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
            }
            if (isJsonErrorEnvelope(root)) {
                String failureType = isJsonAccessRestricted(root) ? ACCESS_RESTRICTED : PROVIDER_ERROR;
                throw new MagnetProviderRunnerException(failureType, requestUrl, statusCode);
            }
            if (!recognizedJson(root)) {
                throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
            }
            return;
        }

        Document document = Jsoup.parse(trimmed);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String titleAndHead = (document.title() + " " + document.head().text()).toLowerCase(Locale.ROOT);
        String visibleText = document.body().text().toLowerCase(Locale.ROOT);
        if (containsAny(lower, CHALLENGE_MARKERS)
                || hasCaptchaChallenge(document)
                || containsAny(titleAndHead, RESTRICTED_PAGE_MARKERS)
                || isExplicitAccessDeniedText(visibleText)) {
            throw new MagnetProviderRunnerException(ACCESS_RESTRICTED, requestUrl, statusCode);
        }
        String headingText = document.select("h1, h2").text().toLowerCase(Locale.ROOT);
        if (containsAny(titleAndHead, PROXY_ERROR_MARKERS) || containsAny(headingText, PROXY_ERROR_MARKERS)) {
            throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
        }
        boolean hasHtmlStructure = lower.startsWith("<!doctype html")
                || lower.startsWith("<html")
                || document.selectFirst("html, body, form, table, main, article") != null;
        if (!hasHtmlStructure) {
            throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
        }
        if (!hasRecognizedHtmlResult(document, lower)) {
            throw new MagnetProviderRunnerException(PARSE_FAILURE, requestUrl, statusCode);
        }
    }

    private boolean isJsonErrorEnvelope(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }
        for (String field : JSON_ERROR_FIELDS) {
            JsonNode value = root.path(field);
            if (!value.isMissingNode() && !value.isNull()
                    && (!value.isTextual() || StringUtils.hasText(value.asText()))) {
                return true;
            }
        }
        JsonNode success = root.path("success");
        if (success.isBoolean() && !success.asBoolean()) {
            return true;
        }
        JsonNode status = root.path("status");
        return status.isTextual() && JSON_ERROR_STATUSES.contains(status.asText().trim().toLowerCase(Locale.ROOT));
    }

    private boolean recognizedJson(JsonNode root) {
        if (root == null) { return false; }
        if (root.isArray()) {
            for (JsonNode element : root) { if (!element.isObject()) { return false; } }
            return true;
        }
        if (!root.isObject()) { return false; }
        for (String field : List.of("data", "results", "items", "torrents", "list")) {
            JsonNode child = root.path(field);
            if (child.isArray() || child.isObject() && recognizedJson(child)) { return true; }
        }
        return root.hasNonNull("info_hash") || root.hasNonNull("hash") || root.hasNonNull("magnet")
                || root.hasNonNull("magnet_uri");
    }

    private boolean isJsonAccessRestricted(JsonNode root) {
        if (!isJsonErrorEnvelope(root)) { return false; }
        for (String field : List.of("error", "errors", "error_description", "message", "status")) {
            JsonNode value = root.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").toLowerCase(Locale.ROOT);
                if (containsAny(text, List.of("access denied", "request denied", "permission denied", "forbidden", "captcha", "cloudflare", "challenge"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsAny(String value, List<String> markers) {
        return markers.stream().anyMatch(value::contains);
    }

    private boolean isExplicitAccessDeniedText(String visibleText) {
        return visibleText.matches("(?i)^access denied(?:\\s+\\d+)?$");
    }

    private boolean hasCaptchaChallenge(Document document) {
        return document.selectFirst(".captcha, [class*=captcha], [id*=captcha], iframe[src*=captcha], script[src*=captcha]") != null;
    }

    private boolean hasRecognizedHtmlResult(Document document, String lowerBody) {
        String visibleText = document.body().text().toLowerCase(Locale.ROOT);
        return containsAny(visibleText, EMPTY_RESULT_MARKERS)
                || lowerBody.contains("magnet:?xt=urn:btih:")
                || document.selectFirst(".no-results, .empty-state") != null;
    }

    private GenericMagnetHttpResponse execute(MagnetProviderSummary provider, String requestUrl) {
        try {
            trafficLimiter.acquireProviderSlot(provider);
            return httpClient.get(URI.create(requestUrl), timeout(provider));
        } catch (HttpTimeoutException ex) {
            throw new MagnetProviderRunnerException(TIMEOUT, requestUrl, null);
        } catch (IOException ex) {
            throw new MagnetProviderRunnerException(HTTP_ERROR, requestUrl, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MagnetProviderRunnerException(TIMEOUT, requestUrl, null);
        }
    }

    private boolean isSupportedQuery(MagnetExternalIdQuery query) {
        return query != null && StringUtils.hasText(query.type()) && StringUtils.hasText(query.value());
    }

    private Duration timeout(MagnetProviderSummary provider) {
        int timeoutMs = provider == null || provider.timeoutMs() == null ? 10000 : provider.timeoutMs();
        return Duration.ofMillis(Math.max(1, timeoutMs));
    }

    private boolean isAccessRestricted(GenericMagnetHttpResponse response) {
        if (response == null) {
            return false;
        }
        if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 429) {
            return true;
        }
        String body = response.body() == null ? "" : response.body().trim();
        if (body.startsWith("{") || body.startsWith("[")) {
            try {
                return isJsonAccessRestricted(objectMapper.readTree(body));
            } catch (tools.jackson.core.JacksonException ignored) {
                return false;
            }
        }
        String lower = body.toLowerCase(Locale.ROOT);
        if (containsAny(lower, CHALLENGE_MARKERS)) {
            return true;
        }
        Document document = Jsoup.parse(body);
        String titleAndHead = (document.title() + " " + document.head().text()).toLowerCase(Locale.ROOT);
        return hasCaptchaChallenge(document) || containsAny(titleAndHead, RESTRICTED_PAGE_MARKERS)
                || isExplicitAccessDeniedText(document.body().text());
    }

    private static GenericMagnetHttpClient httpClient(
            ObjectProvider<GenericMagnetHttpClient> httpClientProvider,
            PublicOutboundProxyPool proxyPool) {
        if (httpClientProvider != null) {
            GenericMagnetHttpClient provided = httpClientProvider.getIfUnique();
            if (provided != null) {
                return provided;
            }
        }
        return new SharedOutboundGenericMagnetHttpClient(proxyPool);
    }

    interface GenericMagnetHttpClient {
        GenericMagnetHttpResponse get(URI uri, Duration timeout) throws IOException, InterruptedException;
    }

    record GenericMagnetHttpResponse(int statusCode, String body) {
    }

    static class SharedOutboundGenericMagnetHttpClient implements GenericMagnetHttpClient {

        private final OutboundHttpClient httpClient;

        SharedOutboundGenericMagnetHttpClient(PublicOutboundProxyPool proxyPool) {
            this(MagnetOutboundHttp.client(proxyPool));
        }

        SharedOutboundGenericMagnetHttpClient(OutboundHttpClient httpClient) {
            this.httpClient = httpClient;
        }

        @Override
        public GenericMagnetHttpResponse get(URI uri, Duration timeout) throws IOException, InterruptedException {
            OutboundHttpResponse response = httpClient.execute(OutboundHttpRequest.get(
                    uri.toString(),
                    OutboundHttpPolicy.publicFetch(timeout)));
            return new GenericMagnetHttpResponse(response.statusCode(), response.bodyAsUtf8());
        }
    }
}
