package com.prodigalgal.ircs.metadata.provider.imdb;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataCandidateTitles;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataEnrichmentPolicy;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProvider;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataSourceEvidence;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ImdbMetadataProvider implements MetadataProvider {
    private static final Pattern TITLE_PATH = Pattern.compile("^/title/(tt[0-9]{7,10})/?$");
    private static final Pattern YEAR = Pattern.compile("^(?:18|19|20)[0-9]{2}");
    private static final Set<String> MOVIE_KINDS = Set.of("movie", "tvmovie", "short", "tvshort");
    private static final Set<String> SERIES_KINDS = Set.of("tvseries", "tvminiseries", "tvseason");
    private static final Set<String> IMAGE_HOSTS = Set.of("m.media-amazon.com", "ia.media-imdb.com", "images-na.ssl-images-amazon.com");

    private final ImdbHttpClient client;
    private final ImdbProviderProperties properties;
    private final ObjectMapper mapper;

    @Override
    public ProviderType getType() {
        return ProviderType.IMDB;
    }

    @Override
    public boolean supports(MetadataSearchContext context) {
        return MetadataEnrichmentPolicy.supports(context, getType());
    }

    @Override
    public Optional<EnrichedMetadataDTO> enrich(MetadataSearchContext context, MetadataSourceEvidence evidence) {
        if (!supports(context)) {
            return Optional.empty();
        }
        String knownId = AuthoritativeIds.normalizeImdb(context.getImdbId());
        if (knownId != null) {
            try {
                return detail(context, evidence, knownId, null);
            } catch (MetadataProviderTerminalException ex) {
                if (!ImdbHttpClient.DETAIL_UNAVAILABLE.equals(ex.getErrorCode())) {
                    throw ex;
                }
                return search(context, evidence, knownId).map(this::partialMetadata);
            }
        }
        Optional<JsonNode> candidate = search(context, evidence, null);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        JsonNode match = candidate.orElseThrow();
        try {
            return detail(context, evidence, text(match, "id"), match);
        } catch (MetadataProviderTerminalException ex) {
            if (!ImdbHttpClient.DETAIL_UNAVAILABLE.equals(ex.getErrorCode())) {
                throw ex;
            }
            log.debug("IMDb public title page unavailable after verified suggestion, videoId={}", context.getVideoId());
            return Optional.of(partialMetadata(match));
        }
    }

    private Optional<JsonNode> search(MetadataSearchContext context, MetadataSourceEvidence evidence, String knownId) {
        for (String query : MetadataCandidateTitles.searchQueries(context)) {
            var uri = UriComponentsBuilder.fromUriString(properties.getSuggestionBaseUrl())
                    .pathSegment("{query}.json").encode().buildAndExpand(query).toUri();
            Optional<String> payload = client.get(uri, false);
            if (payload.isEmpty()) {
                continue;
            }
            var candidates = new LinkedHashMap<String, JsonNode>();
            for (JsonNode item : suggestionResults(payload.orElseThrow())) {
                String candidateId = AuthoritativeIds.normalizeImdb(text(item, "id"));
                String title = text(item, "l");
                if (candidateId != null && (knownId == null || knownId.equals(candidateId))
                        && workTypeMatches(context, evidence, text(item, "qid"))
                        && seasonMatches(context, title)
                        && yearMatches(context, evidence, text(item, "y"))
                        && MetadataCandidateTitles.matches(context, title)) {
                    candidates.putIfAbsent(candidateId, item);
                }
            }
            if (candidates.size() > 1) {
                return Optional.empty();
            }
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.values().iterator().next());
            }
        }
        return Optional.empty();
    }

    private JsonNode suggestionResults(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject() || root.has("d") && !root.path("d").isArray()
                    || !root.has("d") && !root.has("q")) {
                throw new IllegalArgumentException("Invalid IMDb suggestions envelope");
            }
            return root.path("d");
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new MetadataProviderRetryableException("IMDB_PARSE_ERROR", "Unable to parse IMDb suggestions", ex);
        }
    }

    private Optional<EnrichedMetadataDTO> detail(
            MetadataSearchContext context, MetadataSourceEvidence evidence, String id, JsonNode match) {
        var url = UriComponentsBuilder.fromUriString(properties.getDetailBaseUrl())
                .pathSegment(id).path("/").build().toUri();
        return client.get(url, true).flatMap(html -> parseDetail(context, evidence, id, match, html));
    }

    private Optional<EnrichedMetadataDTO> parseDetail(
            MetadataSearchContext context, MetadataSourceEvidence evidence, String id, JsonNode match, String html) {
        var document = Jsoup.parse(html);
        var canonical = document.selectFirst("link[rel=canonical][href]");
        String canonicalId = canonical == null ? null : titleId(canonical.attr("href"));
        if (canonicalId != null && !id.equals(canonicalId)) {
            return Optional.empty();
        }
        for (var script : document.select("script[type=application/ld+json]")) {
            JsonNode work;
            try {
                work = mapper.readTree(script.data());
            } catch (JacksonException ex) {
                continue;
            }
            if (work == null) {
                continue;
            }
            String type = text(work, "@type");
            String name = text(work, "name");
            if (type == null || name == null || !isWorkKind(type)) {
                continue;
            }
            String encodedId = titleId(text(work, "url"));
            if (encodedId == null) {
                encodedId = titleId(text(work, "@id"));
            }
            if (!(id.equals(encodedId) || encodedId == null && id.equals(canonicalId))) {
                return Optional.empty();
            }
            String originalTitle = text(work, "alternateName");
            String date = text(work, "datePublished");
            String workYear = date == null && match != null ? text(match, "y") : year(date);
            if (!workTypeMatches(context, evidence, type) || !yearMatches(context, evidence, workYear)
                    || !seasonMatches(context, name, originalTitle)
                    || !MetadataCandidateTitles.matches(context, name, originalTitle,
                        match == null ? null : text(match, "l"))) {
                return Optional.empty();
            }
            return Optional.of(mapDetail(work, id, name, originalTitle, workYear, date));
        }
        throw ImdbHttpClient.detailsUnavailable();
    }

    private EnrichedMetadataDTO mapDetail(
            JsonNode work, String id, String title, String alternateTitle, String year, String date) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setImdbId(id);
        metadata.setTitle(title);
        metadata.setOriginalTitle(alternateTitle == null ? title : alternateTitle);
        metadata.setYear(year);
        metadata.setDescription(text(work, "description"));
        if (date != null) {
            try {
                metadata.setPublishedAt(LocalDate.parse(date));
            } catch (DateTimeParseException ignored) {
                // A year-only release still carries verified identity evidence.
            }
        }
        JsonNode image = work.path("image");
        applyPoster(metadata, image.isTextual() ? image.asText() : text(image, "url"));
        JsonNode value = work.path("aggregateRating").path("ratingValue");
        if (value.isNumber()) {
            BigDecimal rating = value.decimalValue();
            if (rating.signum() >= 0 && rating.compareTo(BigDecimal.TEN) <= 0) {
                metadata.setScore(rating);
            }
        }
        people(work.path("actor"), metadata::addActor);
        people(work.path("director"), metadata::addDirector);
        var genres = work.path("genre");
        if (genres.isTextual()) {
            metadata.addGenre(genres.asText());
        } else {
            genres.forEach(genre -> metadata.addGenre(genre.asText(null)));
        }
        return metadata;
    }

    private EnrichedMetadataDTO partialMetadata(JsonNode candidate) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setImdbId(AuthoritativeIds.normalizeImdb(text(candidate, "id")));
        metadata.setTitle(text(candidate, "l"));
        metadata.setOriginalTitle(metadata.getTitle());
        metadata.setYear(year(text(candidate, "y")));
        applyPoster(metadata, text(candidate.path("i"), "imageUrl"));
        // The suggestion's "s" field can describe a non-cast credit. Credits are read from the title page.
        return metadata;
    }

    private void applyPoster(EnrichedMetadataDTO metadata, String value) {
        URI image = safeUri(value);
        if (image != null && "https".equalsIgnoreCase(image.getScheme()) && image.getHost() != null
                && IMAGE_HOSTS.contains(image.getHost().toLowerCase(Locale.ROOT))) {
            metadata.setPosterUrl(value);
            metadata.setImageSource(getType());
        }
    }

    private static boolean seasonMatches(MetadataSearchContext context, String... titles) {
        Integer season = context.getSeason();
        if (season == null || season < 2) {
            return true;
        }
        for (String title : titles) {
            if (MetadataCandidateTitles.identifiesSeason(title, season)) {
                return true;
            }
        }
        return false;
    }

    private static boolean workTypeMatches(MetadataSearchContext context, MetadataSourceEvidence evidence, String kind) {
        String type = MetadataEnrichmentPolicy.searchType(context, evidence);
        String normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        boolean movie = MOVIE_KINDS.contains(normalized);
        boolean television = SERIES_KINDS.contains(normalized);
        return switch (type) {
            case "movie" -> movie;
            case "tv" -> television;
            case "multi" -> movie || television;
            default -> false;
        };
    }

    private static boolean isWorkKind(String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        return MOVIE_KINDS.contains(normalized) || SERIES_KINDS.contains(normalized);
    }

    private static boolean yearMatches(MetadataSearchContext context, MetadataSourceEvidence evidence, String value) {
        String expected = year(context.getYear());
        String candidate = year(value);
        return evidence.matchesTitleYear(candidate) && (expected == null || expected.equals(candidate));
    }

    private static String year(String value) {
        if (value == null) {
            return null;
        }
        var match = YEAR.matcher(value);
        return match.find() ? match.group() : null;
    }

    private static void people(JsonNode nodes, Consumer<String> add) {
        if (nodes.isObject()) {
            add.accept(text(nodes, "name"));
        } else {
            nodes.forEach(person -> add.accept(text(person, "name")));
        }
    }

    private static String titleId(String value) {
        URI uri = safeUri(value);
        if (uri == null || (uri.getHost() != null && !Set.of("www.imdb.com", "imdb.com").contains(uri.getHost()))
                || uri.getPath() == null) {
            return null;
        }
        var match = TITLE_PATH.matcher(uri.getPath());
        return match.matches() ? match.group(1) : null;
    }

    private static URI safeUri(String value) {
        try {
            return value == null ? null : URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() || "null".equals(value) ? null : value.strip();
    }
}
