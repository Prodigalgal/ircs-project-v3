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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ImdbMetadataProvider implements MetadataProvider {

    private static final Pattern YEAR = Pattern.compile("^(?:18|19|20)[0-9]{2}");
    private static final Pattern TITLE_YEAR = Pattern.compile("\\((?:TV Series |TV Mini Series )?((?:18|19|20)[0-9]{2})");
    private static final Pattern SUBJECT_PATH = Pattern.compile("^/title/(tt[0-9]{7,10})/?$");
    private static final Set<String> MOVIE_KINDS = Set.of("movie", "tvmovie", "short", "tvshort");
    private static final Set<String> SERIES_KINDS = Set.of("tvseries", "tvminiseries", "tvseason");
    private static final Set<String> IMAGE_HOSTS =
            Set.of("m.media-amazon.com", "ia.media-imdb.com", "images-na.ssl-images-amazon.com");

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
            log.debug("IMDb title details unavailable after verified suggestion, videoId={}", context.getVideoId());
            return Optional.of(partialMetadata(match));
        }
    }

    private Optional<JsonNode> search(MetadataSearchContext context, MetadataSourceEvidence evidence, String knownId) {
        for (String query : MetadataCandidateTitles.searchQueries(context)) {
            var uri = UriComponentsBuilder.fromUriString(properties.getSuggestionBaseUrl())
                    .pathSegment("{query}.json").encode().buildAndExpand(query).toUri();
            Optional<String> payload = client.suggestion(uri);
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

    /**
     * Reads the public title page. It only answers while a browser-primed session is published for
     * IMDb; without one the WAF challenge is reported as {@code DETAIL_UNAVAILABLE} so the caller
     * degrades to the verified suggestion.
     */
    private Optional<EnrichedMetadataDTO> detail(
            MetadataSearchContext context, MetadataSourceEvidence evidence, String id, JsonNode match) {
        String requestedId = AuthoritativeIds.normalizeImdb(id);
        if (requestedId == null) {
            throw ImdbHttpClient.detailsUnavailable();
        }
        return client.titlePage(requestedId)
                .flatMap(html -> readTitlePage(requestedId, html)
                        .flatMap(work -> verified(context, evidence, match, work)));
    }

    private Optional<EnrichedMetadataDTO> verified(
            MetadataSearchContext context, MetadataSourceEvidence evidence, JsonNode match, ImdbTitle work) {
        if (!isWorkKind(work.type())) {
            return Optional.empty();
        }
        if (!workTypeMatches(context, evidence, work.type())
                || !yearMatches(context, evidence, work.year())
                || !seasonMatches(context, work.name(), work.originalTitle())
                || !MetadataCandidateTitles.matches(context, work.name(), work.originalTitle(),
                    match == null ? null : text(match, "l"))) {
            return Optional.empty();
        }
        return Optional.of(mapDetail(work));
    }

    /**
     * Parses the page IMDb serves to a browser.
     *
     * <p>The year deliberately does not come from the JSON-LD {@code datePublished}: that field holds a
     * release <em>event</em> date, not the original year. The Matrix reports {@code 2000-01-14} and The
     * Shawshank Redemption reports {@code 2026-08-28} even though both pages display 1999 and 1994. The
     * release-info anchor and the {@code <title>} tag carry the original year and are used instead.
     *
     * <p>Empty means the page is a real title page for a <em>different</em> work, which rejects the
     * candidate. An unusable page - blocked, or carrying no structured data - raises
     * {@code DETAIL_UNAVAILABLE} so the caller can degrade to the verified suggestion instead.
     */
    private Optional<ImdbTitle> readTitlePage(String requestedId, String html) {
        Document document = Jsoup.parse(html);
        Element canonical = document.selectFirst("link[rel=canonical][href]");
        String canonicalId = titleId(canonical == null ? null : canonical.attr("href"));
        if (canonicalId == null) {
            throw ImdbHttpClient.detailsUnavailable();
        }
        if (!canonicalId.equals(requestedId)) {
            return Optional.empty();
        }
        JsonNode work = jsonLdWork(document);
        if (work == null) {
            throw ImdbHttpClient.detailsUnavailable();
        }
        String workId = titleId(text(work, "url"));
        if (workId != null && !workId.equals(requestedId)) {
            return Optional.empty();
        }
        String name = text(work, "name");
        if (name == null) {
            throw ImdbHttpClient.detailsUnavailable();
        }
        return Optional.of(new ImdbTitle(
                requestedId,
                text(work, "@type"),
                name,
                // The page no longer carries alternateName; the credited title is the original title.
                text(work, "alternateName"),
                originalYear(document),
                text(work, "datePublished"),
                text(work, "description"),
                imageUrl(work.path("image")),
                decimal(work.path("aggregateRating").path("ratingValue")),
                names(work.path("director")),
                names(work.path("actor")),
                strings(work.path("genre"))));
    }

    private JsonNode jsonLdWork(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode node;
            try {
                node = mapper.readTree(script.data());
            } catch (JacksonException ex) {
                continue;
            }
            String type = node == null ? null : text(node, "@type");
            if (type != null && isWorkKind(type)) {
                return node;
            }
        }
        return null;
    }

    private String originalYear(Document document) {
        for (Element anchor : document.select("a[href*=/releaseinfo]")) {
            Matcher matcher = YEAR.matcher(anchor.text().strip());
            if (matcher.find()) {
                return matcher.group();
            }
        }
        Element title = document.selectFirst("title");
        if (title != null) {
            Matcher matcher = TITLE_YEAR.matcher(title.text());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private EnrichedMetadataDTO mapDetail(ImdbTitle work) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setImdbId(work.id());
        metadata.setTitle(work.name());
        metadata.setOriginalTitle(work.originalTitle() == null ? work.name() : work.originalTitle());
        metadata.setYear(work.year());
        metadata.setDescription(work.description());
        if (work.released() != null) {
            try {
                metadata.setPublishedAt(LocalDate.parse(work.released()));
            } catch (DateTimeParseException ignored) {
                // A year-only release still carries verified identity evidence.
            }
        }
        applyPoster(metadata, work.poster());
        if (work.score() != null) {
            metadata.setScore(work.score());
        }
        work.directors().forEach(metadata::addDirector);
        work.actors().forEach(metadata::addActor);
        work.genres().forEach(metadata::addGenre);
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

    private static String titleId(String value) {
        URI uri = safeUri(value);
        if (uri == null || (uri.getHost() != null && !Set.of("www.imdb.com", "imdb.com").contains(uri.getHost()))
                || uri.getPath() == null) {
            return null;
        }
        var match = SUBJECT_PATH.matcher(uri.getPath());
        return match.matches() ? match.group(1) : null;
    }

    /** JSON-LD carries credits either as a single object or as an array of objects. */
    private static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return names;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectName(item, names);
            }
            return names;
        }
        collectName(node, names);
        return names;
    }

    private static void collectName(JsonNode node, List<String> names) {
        String name = text(node, "name");
        if (name != null) {
            names.add(name);
        }
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addString(item, values);
            }
            return values;
        }
        addString(node, values);
        return values;
    }

    private static void addString(JsonNode node, List<String> values) {
        String value = node.isTextual() ? node.asText() : text(node, "text");
        if (value != null && !value.isBlank()) {
            values.add(value.strip());
        }
    }

    private static String imageUrl(JsonNode image) {
        if (image == null || image.isMissingNode() || image.isNull()) {
            return null;
        }
        return image.isTextual() ? image.asText() : text(image, "url");
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        BigDecimal value = node.isNumber() ? node.decimalValue() : parseDecimal(node.asText(null));
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.TEN) <= 0 ? value : null;
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static URI safeUri(String value) {
        try {
            return value == null ? null : URI.create(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() || "null".equals(value) ? null : value.strip();
    }

    /** One title, normalised from whichever source answered. */
    private record ImdbTitle(
            String id,
            String type,
            String name,
            String originalTitle,
            String year,
            String released,
            String description,
            String poster,
            BigDecimal score,
            List<String> directors,
            List<String> actors,
            List<String> genres) { }
}
