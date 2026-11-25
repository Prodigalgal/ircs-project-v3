package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanCredentialRepository;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanHttpClient;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanProviderProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Optional, identity-only Douban supplement used by explicitly authorized catalog imports. */
@Component
final class CatalogDoubanSupplementSource {
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;
    private final DoubanCredentialRepository credentials;
    private final DoubanHttpClient http;
    private final ObjectMapper mapper;
    private final DoubanProviderProperties properties;

    CatalogDoubanSupplementSource(DoubanCredentialRepository credentials, DoubanHttpClient http,
                                  ObjectMapper mapper, DoubanProviderProperties properties) {
        this.credentials = credentials;
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
    }

    Optional<EnrichedMetadataDTO> fetch(String queryTitle, String verifiedDoubanId, String verifiedImdbId) {
        String title = clean(queryTitle);
        String knownDouban = AuthoritativeIds.normalizeDouban(verifiedDoubanId);
        String knownImdb = AuthoritativeIds.normalizeImdb(verifiedImdbId);
        if (title == null || (knownDouban == null && knownImdb == null)) return Optional.empty();
        URI uri = UriComponentsBuilder.fromUriString(properties.getSuggestUrl())
                .queryParam("q", title).build().encode().toUri();
        Optional<String> response;
        try {
            response = http.getJson(uri, credentials.findPreferred());
        } catch (MetadataProviderRetryableException | MetadataProviderTerminalException ex) {
            throw unavailable("DOUBAN_SUPPLEMENT_UNAVAILABLE");
        }
        if (response.isEmpty()) return Optional.empty();
        String body = response.get();
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
            throw unavailable("DOUBAN_SUPPLEMENT_RESPONSE_TOO_LARGE");
        }
        JsonNode candidates;
        try {
            candidates = mapper.readTree(body);
        } catch (Exception ex) {
            throw unavailable("DOUBAN_SUPPLEMENT_INVALID_RESPONSE");
        }
        if (candidates == null || !candidates.isArray()) throw unavailable("DOUBAN_SUPPLEMENT_INVALID_RESPONSE");
        JsonNode match = null;
        for (JsonNode candidate : candidates) {
            if (!candidate.isObject()) continue;
            String id = AuthoritativeIds.normalizeDouban(text(candidate, "id"));
            if (id == null) continue;
            if (knownDouban != null) {
                if (!knownDouban.equals(id)) continue;
                String explicitImdb = candidateImdb(candidate);
                if (explicitImdb != null && knownImdb != null && !knownImdb.equals(explicitImdb)) {
                    throw unavailable("DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT");
                }
            } else {
                String explicitImdb = candidateImdb(candidate);
                if (explicitImdb == null || !knownImdb.equals(explicitImdb)) continue;
            }
            if (match != null) return Optional.empty();
            match = candidate;
        }
        if (match == null) return Optional.empty();
        return Optional.of(map(match, AuthoritativeIds.normalizeDouban(text(match, "id"))));
    }

    private EnrichedMetadataDTO map(JsonNode candidate, String doubanId) {
        EnrichedMetadataDTO result = new EnrichedMetadataDTO();
        result.setDoubanId(doubanId);
        result.setTitle(text(candidate, "title"));
        result.setOriginalTitle(cleanOriginalTitle(text(candidate, "sub_title")));
        result.setYear(cleanYear(text(candidate, "year")));
        String imdbId = candidateImdb(candidate);
        if (imdbId != null) result.setImdbId(imdbId);
        String score = text(candidate, "rating");
        if (score != null) {
            try {
                BigDecimal value = new BigDecimal(score);
                if (value.scale() >= 0 && value.scale() <= 2 && value.signum() >= 0
                        && value.compareTo(BigDecimal.TEN) <= 0) result.setScore(value);
            } catch (NumberFormatException ignored) {
                // Suggest ratings are optional and malformed values carry no usable evidence.
            }
        }
        return result;
    }

    private String candidateImdb(JsonNode candidate) {
        JsonNode node = candidate.has("imdb_id") ? candidate.get("imdb_id") : candidate.get("imdb");
        return node != null && node.isTextual() ? AuthoritativeIds.normalizeImdb(node.asText()) : null;
    }

    private static CatalogMetadataUnavailableException unavailable(String code) {
        return new CatalogMetadataUnavailableException(code);
    }
    private static String clean(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private static String text(JsonNode node, String field) { return clean(node.path(field).asText(null)); }
    private static String cleanOriginalTitle(String value) {
        if (value == null) return null;
        String cleaned = value.replaceFirst("^\\s*原名[:：]\\s*", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
    private static String cleanYear(String value) {
        if (value == null) return null;
        var matcher = java.util.regex.Pattern.compile("(?:^|\\D)(\\d{4})(?:\\D|$)").matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }
}
