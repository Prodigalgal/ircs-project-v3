package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanHttpClient;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanProviderProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
final class CatalogDoubanSupplementSource {
    static final String ACCESS_DENIED = "DOUBAN_SUPPLEMENT_ACCESS_DENIED";
    static final String RATE_LIMITED = "DOUBAN_SUPPLEMENT_RATE_LIMITED";
    static final String IDENTITY_CONFLICT = "DOUBAN_SUPPLEMENT_IDENTITY_CONFLICT";
    static final String INVALID_RESPONSE = "DOUBAN_SUPPLEMENT_INVALID_RESPONSE";
    private static final String SUBJECT_ROOT = "https://movie.douban.com/subject/";
    private static final int MAX_CANDIDATES = 3;
    private static final int MAX_SEARCH_BYTES = 512 * 1024;
    private static final int MAX_SUBJECT_BYTES = 1024 * 1024;
    private final DoubanHttpClient http;
    private final ObjectMapper mapper;
    private final DoubanProviderProperties properties;

    Optional<EnrichedMetadataDTO> fetch(String queryTitle, String verifiedDoubanId, String verifiedImdbId) {
        String knownDouban = AuthoritativeIds.normalizeDouban(verifiedDoubanId);
        String knownImdb = AuthoritativeIds.normalizeImdb(verifiedImdbId);
        if (knownDouban != null) {
            return subject(knownDouban).map(page -> {
                if (knownImdb != null && page.imdbId() != null && !knownImdb.equals(page.imdbId())) {
                    throw unavailable(IDENTITY_CONFLICT);
                }
                return metadata(page);
            });
        }
        if (knownImdb == null || !StringUtils.hasText(queryTitle)) return Optional.empty();

        DoubanSubjectPageParser.Subject matching = null;
        for (String candidateId : search(queryTitle.strip())) {
            var page = subject(candidateId);
            if (page.isEmpty() || !knownImdb.equals(page.get().imdbId())) continue;
            if (matching != null) throw unavailable(IDENTITY_CONFLICT);
            matching = page.get();
        }
        return Optional.ofNullable(matching).map(this::metadata);
    }

    private List<String> search(String title) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getSuggestUrl())
                .queryParam("q", "{title}").encode().buildAndExpand(title).toUri();
        Optional<String> response = fetchPage(uri, "application/json", MAX_SEARCH_BYTES);
        if (response.isEmpty()) return List.of();
        JsonNode candidates;
        try {
            candidates = mapper.readTree(response.get());
        } catch (Exception ex) {
            throw unavailable(INVALID_RESPONSE);
        }
        if (candidates == null || !candidates.isArray()) throw unavailable(INVALID_RESPONSE);
        var ids = new LinkedHashSet<String>();
        for (JsonNode candidate : candidates) {
            String id = AuthoritativeIds.normalizeDouban(candidate.path("id").asText(null));
            if (id != null) ids.add(id);
            if (ids.size() == MAX_CANDIDATES) break;
        }
        return List.copyOf(ids);
    }

    private Optional<DoubanSubjectPageParser.Subject> subject(String id) {
        URI uri = URI.create(SUBJECT_ROOT + id + "/");
        return fetchPage(uri, "text/html", MAX_SUBJECT_BYTES).map(body -> DoubanSubjectPageParser.read(body, id));
    }

    private Optional<String> fetchPage(URI uri, String accept, int maxBytes) {
        Optional<String> response;
        try {
            response = http.getPublicPage(uri, accept);
        } catch (MetadataProviderRetryableException ex) {
            throw unavailable("RATE_LIMITED".equals(ex.getErrorCode()) ? RATE_LIMITED : "DOUBAN_SUPPLEMENT_UNAVAILABLE");
        } catch (MetadataProviderTerminalException ex) {
            throw unavailable("DOUBAN_FORBIDDEN".equals(ex.getErrorCode()) ? ACCESS_DENIED : "DOUBAN_SUPPLEMENT_UNAVAILABLE");
        }
        if (response.isPresent() && response.get().getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw unavailable("DOUBAN_SUPPLEMENT_RESPONSE_TOO_LARGE");
        }
        return response;
    }

    private EnrichedMetadataDTO metadata(DoubanSubjectPageParser.Subject subject) {
        EnrichedMetadataDTO result = new EnrichedMetadataDTO();
        result.setDoubanId(subject.id());
        result.setImdbId(subject.imdbId());
        result.setTitle(subject.title());
        result.setOriginalTitle(subject.originalTitle());
        result.setYear(subject.year());
        result.setScore(subject.score());
        return result;
    }

    private static CatalogMetadataUnavailableException unavailable(String code) {
        return new CatalogMetadataUnavailableException(code);
    }
}
