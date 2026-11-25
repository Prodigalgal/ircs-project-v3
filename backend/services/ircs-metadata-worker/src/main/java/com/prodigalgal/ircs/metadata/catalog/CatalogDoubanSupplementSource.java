package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.metadata.catalog.CatalogReferenceSyncRepository.DoubanTarget;
import com.prodigalgal.ircs.metadata.catalog.DoubanCatalogObservation.MatchBasis;
import com.prodigalgal.ircs.metadata.catalog.DoubanSubjectPageParser.Subject;
import com.prodigalgal.ircs.metadata.catalog.DoubanWorkMatcher.TmdbWorkEvidence;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanHttpClient;
import com.prodigalgal.ircs.metadata.provider.douban.DoubanProviderProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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

    Optional<DoubanCatalogObservation> fetch(DoubanTarget target, Supplier<TmdbWorkEvidence> loadEvidence) {
        String knownDouban = AuthoritativeIds.normalizeDouban(target.doubanId());
        String knownImdb = AuthoritativeIds.normalizeImdb(target.imdbId());
        if (knownDouban != null) {
            return subject(knownDouban).map(page -> {
                if (!compatibleType(target, page) || conflictsWithImdb(page, knownImdb)) {
                    throw unavailable(IDENTITY_CONFLICT);
                }
                return observation(page, MatchBasis.EXTERNAL_ID);
            });
        }
        if (knownImdb == null && target.releaseYear() == null) return Optional.empty();

        var candidates = new ArrayList<Subject>();
        var visited = new LinkedHashSet<String>();
        readCandidates(target.title(), visited, candidates);
        var exact = idMatches(target, candidates, knownImdb);
        if (exact.isEmpty() && !CatalogTitleKey.of(target.title()).equals(CatalogTitleKey.of(target.originalTitle()))) {
            readCandidates(target.originalTitle(), visited, candidates);
            exact = idMatches(target, candidates, knownImdb);
        }
        if (!exact.isEmpty()) return uniqueObservation(exact, MatchBasis.EXTERNAL_ID);
        if (candidates.isEmpty()) return Optional.empty();

        TmdbWorkEvidence evidence = loadEvidence.get();
        return uniqueObservation(candidates.stream()
                .filter(page -> !conflictsWithImdb(page, knownImdb))
                .filter(page -> DoubanWorkMatcher.matches(target, page, evidence)).toList(), MatchBasis.TMDB_DETAILS);
    }

    private void readCandidates(String title, Set<String> visited, List<Subject> candidates) {
        if (!StringUtils.hasText(title)) return;
        for (String candidateId : search(title.strip())) {
            if (visited.add(candidateId)) subject(candidateId).ifPresent(candidates::add);
        }
    }

    private static List<Subject> idMatches(DoubanTarget target, List<Subject> candidates, String knownImdb) {
        if (knownImdb == null) return List.of();
        return candidates.stream().filter(page -> knownImdb.equals(page.imdbId()) && compatibleType(target, page)).toList();
    }

    private static boolean compatibleType(DoubanTarget target, Subject page) {
        return page.mediaType() == null || target.mediaType().equals(page.mediaType());
    }

    private static boolean conflictsWithImdb(Subject page, String knownImdb) {
        return knownImdb != null && page.imdbId() != null && !knownImdb.equals(page.imdbId());
    }

    private static Optional<DoubanCatalogObservation> uniqueObservation(List<Subject> matches, MatchBasis basis) {
        if (matches.size() > 1) throw unavailable(IDENTITY_CONFLICT);
        return matches.stream().findFirst().map(page -> observation(page, basis));
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

    private Optional<Subject> subject(String id) {
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

    private static DoubanCatalogObservation observation(Subject subject, MatchBasis basis) {
        var facts = new DoubanCatalogObservation.Facts(subject.id(), subject.imdbId(), subject.title(),
                subject.originalTitle(), subject.year(), subject.aliases(), subject.mainlandReleaseDate());
        return new DoubanCatalogObservation(facts, subject.score(), subject.voteCount(), basis);
    }

    private static CatalogMetadataUnavailableException unavailable(String code) {
        return new CatalogMetadataUnavailableException(code);
    }
}
