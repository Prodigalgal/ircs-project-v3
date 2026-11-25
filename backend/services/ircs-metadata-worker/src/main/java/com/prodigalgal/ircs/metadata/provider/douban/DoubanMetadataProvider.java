package com.prodigalgal.ircs.metadata.provider.douban;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class DoubanMetadataProvider implements MetadataProvider {

    private static final Pattern YEAR = Pattern.compile("(?:^|\\D)(\\d{4})(?:\\D|$)");
    private static final Pattern IMDB_ID = Pattern.compile("(?i)\\btt\\d{7,10}\\b");
    private static final Pattern SEASON = Pattern.compile("第\\s*([0-9一二三四五六七八九十两]+)\\s*季|(?:\\bseason\\s*|\\bs)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBJECT_PATH = Pattern.compile("^/subject/([0-9]+)/?$", Pattern.CASE_INSENSITIVE);

    private final DoubanCredentialRepository credentialRepository;
    private final DoubanHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DoubanProviderProperties properties;

    @Override
    public ProviderType getType() {
        return ProviderType.DOUBAN;
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
        Optional<DoubanCredential> credential = credentialRepository.findPreferred();
        String requestedId = AuthoritativeIds.normalizeDouban(context.getDoubanId());
        if (requestedId != null) {
            DoubanHttpResponse detail = getSubject(requestedId, credential);
            Optional<EnrichedMetadataDTO> parsed = verifiedDetails(context, evidence, requestedId, detail);
            if (parsed.isPresent()) {
                return parsed;
            }
            if (!isChallengeOrUnavailable(detail)) {
                return Optional.empty();
            }
            return uniqueSuggestFallback(context, evidence, credential, requestedId);
        }

        Optional<Candidate> selected = search(context, evidence, credential);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        Candidate candidate = selected.orElseThrow();
        DoubanHttpResponse detail = getSubject(candidate.id(), credential);
        Optional<EnrichedMetadataDTO> parsed = verifiedDetails(context, evidence, candidate.id(), detail);
        if (parsed.isPresent()) {
            return parsed;
        }
        if (!isChallengeOrUnavailable(detail)) {
            return Optional.empty();
        }
        if (hasExistingImdbIdentity(context) || !candidateTypeMatches(context, evidence, candidate)) {
            return Optional.empty();
        }
        return Optional.of(mapPartial(candidate));
    }

    private Optional<EnrichedMetadataDTO> uniqueSuggestFallback(
            MetadataSearchContext context, MetadataSourceEvidence evidence,
            Optional<DoubanCredential> credential, String expectedId) {
        return search(context, evidence, credential)
                .filter(candidate -> expectedId.equals(candidate.id()))
                .filter(candidate -> !hasExistingImdbIdentity(context) && candidateTypeMatches(context, evidence, candidate))
                .map(this::mapPartial);
    }

    private Optional<Candidate> search(MetadataSearchContext context, MetadataSourceEvidence evidence,
            Optional<DoubanCredential> credential) {
        for (String query : MetadataCandidateTitles.searchQueries(context)) {
            DoubanHttpResponse response = httpClient.getText(buildSuggestUri(query), credential);
            if (response.statusCode() == 404) {
                continue;
            }
            requireSuccessfulSuggest(response);
            var matches = matchingCandidates(context, evidence, response.body());
            if (matches.size() > 1) {
                return Optional.empty();
            }
            if (matches.size() == 1) {
                return Optional.of(matches.values().iterator().next());
            }
        }
        return Optional.empty();
    }

    private DoubanHttpResponse getSubject(String id, Optional<DoubanCredential> credential) {
        String base = properties.getSubjectBaseUrl();
        URI uri = URI.create((base.endsWith("/") ? base : base + "/") + id + "/");
        return httpClient.getText(uri, credential);
    }

    private URI buildSuggestUri(String query) {
        return UriComponentsBuilder.fromUriString(properties.getSuggestUrl())
                .queryParam("q", "{title}").encode().buildAndExpand(query).toUri();
    }

    private Map<String, Candidate> matchingCandidates(
            MetadataSearchContext context, MetadataSourceEvidence evidence, String response) {
        if (!StringUtils.hasText(response)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            if (root == null || !root.isArray()) {
                throw new IllegalArgumentException("Douban suggest response must be an array");
            }
            Map<String, Candidate> matches = new LinkedHashMap<>();
            for (JsonNode candidate : root) {
                String id = AuthoritativeIds.normalizeDouban(text(candidate, "id"));
                String title = text(candidate, "title");
                String originalTitle = cleanOriginalTitle(text(candidate, "sub_title"));
                String year = cleanYear(text(candidate, "year"));
                String type = text(candidate, "type");
                String episode = text(candidate, "episode");
                if (id == null || !MetadataCandidateTitles.matches(context, title, originalTitle)
                        || !yearMatches(context, evidence, year)
                        || !seasonMatches(context, title, originalTitle)) {
                    continue;
                }
                matches.putIfAbsent(id, new Candidate(id, title, originalTitle, year,
                        text(candidate, "img"), "tv".equalsIgnoreCase(type) || StringUtils.hasText(episode)));
            }
            return matches;
        } catch (MetadataProviderRetryableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MetadataProviderRetryableException(
                    "PROVIDER_PARSE_ERROR", "Unable to parse Douban suggest response", ex);
        }
    }

    private Optional<EnrichedMetadataDTO> verifiedDetails(
            MetadataSearchContext context, MetadataSourceEvidence evidence, String requestedId,
            DoubanHttpResponse response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().isBlank()) {
            return Optional.empty();
        }
        Document document = Jsoup.parse(response.body());
        String pageId = canonicalSubjectId(document);
        if (!requestedId.equals(pageId) || !hasWorkMarkers(document)) {
            return Optional.empty();
        }
        Element titleElement = document.selectFirst("h1 [property=v:itemreviewed]");
        Element ogTitle = document.selectFirst("meta[property=og:title]");
        String localizedTitle = clean(stripDoubanTitle(ogTitle == null ? null : ogTitle.attr("content")));
        String heading = clean(titleElement == null ? null : titleElement.text());
        String title = localizedTitle == null ? heading : localizedTitle;
        if (!StringUtils.hasText(title)) {
            return Optional.empty();
        }
        String originalTitle = infoValue(document, "原名");
        if (!MetadataCandidateTitles.matches(context, title, originalTitle, heading)
                || !yearMatches(context, evidence, detailYear(document))
                || !seasonMatches(context, title, originalTitle, heading)) {
            return Optional.empty();
        }
        boolean tv = hasTvMarkers(document);
        String expectedType = MetadataEnrichmentPolicy.searchType(context, evidence);
        if ("movie".equals(expectedType) && tv || "tv".equals(expectedType) && !tv) {
            return Optional.empty();
        }
        String imdbId = detailImdbId(document);
        if (imdbId != null && imdbId.isEmpty()) {
            return Optional.empty();
        }
        String existingImdbId = AuthoritativeIds.normalizeImdb(context.getImdbId());
        if (existingImdbId != null && imdbId != null && !existingImdbId.equals(imdbId)) {
            return Optional.empty();
        }
        return Optional.of(mapDetails(document, requestedId, title, originalTitle, imdbId));
    }

    private boolean isChallengeOrUnavailable(DoubanHttpResponse response) {
        if (response.statusCode() == 403 || response.statusCode() == 202) {
            return true;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().isBlank()) {
            return false;
        }
        Document document = Jsoup.parse(response.body());
        return !hasWorkMarkers(document);
    }

    private void requireSuccessfulSuggest(DoubanHttpResponse response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new MetadataProviderRetryableException("DOUBAN_SUGGEST_UNAVAILABLE",
                        "Douban suggest returned " + response.statusCode());
            }
            throw new MetadataProviderTerminalException("HTTP_" + response.statusCode(),
                    "Douban suggest returned " + response.statusCode());
        }
    }

    private String canonicalSubjectId(Document document) {
        String id = null;
        for (Element identity : document.select("link[rel=canonical][href], meta[property=og:url][content]")) {
            String url = identity.hasAttr("href") ? identity.attr("href") : identity.attr("content");
            try {
                URI uri = URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || !"movie.douban.com".equalsIgnoreCase(uri.getHost())
                        || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443) {
                    continue;
                }
                Matcher matcher = SUBJECT_PATH.matcher(uri.getPath());
                if (matcher.matches()) {
                    String found = AuthoritativeIds.normalizeDouban(matcher.group(1));
                    if (found == null || id != null && !id.equals(found)) {
                        return null;
                    }
                    id = found;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid or relative identity URLs do not establish a public subject identity.
            }
        }
        return id;
    }

    private boolean hasWorkMarkers(Document document) {
        boolean hasTitle = document.selectFirst("h1 [property=v:itemreviewed], meta[property=og:title]") != null;
        boolean hasDetails = document.selectFirst("#info, #link-report, [property=v:summary], [property=v:genre], .rating_wrap") != null;
        return hasTitle && hasDetails;
    }

    private String detailYear(Document document) {
        Element year = document.selectFirst("h1 .year");
        return cleanYear(year == null ? infoValue(document, "上映日期") : year.text());
    }

    private boolean yearMatches(MetadataSearchContext context, MetadataSourceEvidence evidence, String candidateYear) {
        if (evidence.titleYear() != null) {
            return evidence.matchesTitleYear(candidateYear);
        }
        String source = cleanYear(context.getYear());
        if (!StringUtils.hasText(source)) {
            return true;
        }
        if (!StringUtils.hasText(candidateYear)) return false;
        try {
            return Math.abs(Integer.parseInt(source) - Integer.parseInt(candidateYear)) <= 1;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private boolean candidateTypeMatches(
            MetadataSearchContext context, MetadataSourceEvidence evidence, Candidate candidate) {
        String expectedType = MetadataEnrichmentPolicy.searchType(context, evidence);
        if ("tv".equals(expectedType)) {
            return candidate.tvEvidence();
        }
        if ("movie".equals(expectedType)) {
            return !candidate.tvEvidence();
        }
        return true;
    }

    private boolean hasExistingImdbIdentity(MetadataSearchContext context) {
        return AuthoritativeIds.normalizeImdb(context.getImdbId()) != null;
    }

    private boolean seasonMatches(MetadataSearchContext context, String... candidateTitles) {
        Integer expected = context.getSeason();
        if (expected == null || expected <= 0) {
            expected = seasonNumber(context.getFullTitle());
        }
        if (expected == null || expected <= 0) {
            return true;
        }
        for (String candidateTitle : candidateTitles) {
            Integer actual = seasonNumber(candidateTitle);
            if (actual != null) {
                return expected.equals(actual);
            }
        }
        return false;
    }

    private Integer seasonNumber(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        Matcher matcher = SEASON.matcher(title);
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        try {
            return Integer.valueOf(number);
        } catch (NumberFormatException ignored) {
            String digits = "零一二三四五六七八九";
            String normalized = number.replace('两', '二');
            int ten = normalized.indexOf('十');
            if (ten < 0) {
                int digit = digits.indexOf(normalized);
                return digit < 0 ? null : digit;
            }
            int tens = ten == 0 ? 1 : digits.indexOf(normalized.charAt(0));
            int units = ten == normalized.length() - 1 ? 0 : digits.indexOf(normalized.charAt(ten + 1));
            return tens < 0 || units < 0 ? null : tens * 10 + units;
        }
    }

    private boolean hasTvMarkers(Document document) {
        return StringUtils.hasText(infoValue(document, "首播"))
                || StringUtils.hasText(infoValue(document, "集数"))
                || StringUtils.hasText(infoValue(document, "单集片长"));
    }

    private String infoValue(Document document, String fieldName) {
        Element info = document.selectFirst("#info");
        if (info == null) {
            return null;
        }
        for (Element label : info.select(".pl")) {
            if (!label.text().replace(":", "").replace("：", "").trim().equals(fieldName)) {
                continue;
            }
            StringBuilder value = new StringBuilder();
            for (org.jsoup.nodes.Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
                if (node instanceof Element element && ("br".equals(element.normalName()) || element.hasClass("pl"))) {
                    break;
                }
                value.append(' ').append(node instanceof Element element ? element.text()
                        : node instanceof org.jsoup.nodes.TextNode text ? text.text() : "");
            }
            return clean(value.toString().replaceFirst("^\\s*[:：]\\s*", ""));
        }
        return null;
    }

    private String detailImdbId(Document document) {
        Element info = document.selectFirst("#info");
        if (info == null) {
            return null;
        }
        Matcher matcher = IMDB_ID.matcher(info.text());
        String id = null;
        while (matcher.find()) {
            String candidate = AuthoritativeIds.normalizeImdb(matcher.group());
            if (candidate != null && id != null && !id.equals(candidate)) {
                return "";
            }
            id = candidate;
        }
        return id;
    }

    private EnrichedMetadataDTO mapDetails(
            Document document, String doubanId, String title, String originalTitle, String imdbId) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setDoubanId(doubanId);
        metadata.setImdbId(imdbId);
        metadata.setTitle(title);
        metadata.setOriginalTitle(originalTitle);
        metadata.setYear(detailYear(document));
        Element poster = document.selectFirst("meta[property=og:image][content]");
        if (poster != null && StringUtils.hasText(poster.attr("content"))) {
            metadata.setPosterUrl(poster.attr("content").trim());
            metadata.setImageSource(ProviderType.DOUBAN);
        }
        Element summary = document.selectFirst("[property=v:summary]");
        if (summary != null) {
            metadata.setDescription(clean(summary.text()));
        }
        Element score = document.selectFirst("[property=v:average]");
        if (score != null) {
            metadata.setScore(decimal(score.hasAttr("content") ? score.attr("content") : score.text()));
        }
        document.select("a[rel=v:directedBy]").forEach(element -> metadata.addDirector(clean(element.text())));
        document.select("a[rel=v:starring]").forEach(element -> metadata.addActor(clean(element.text())));
        document.select("[property=v:genre]").forEach(element -> metadata.addGenre(clean(element.text())));
        return metadata;
    }

    private EnrichedMetadataDTO mapPartial(Candidate candidate) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setDoubanId(candidate.id());
        metadata.setTitle(candidate.title());
        metadata.setOriginalTitle(candidate.originalTitle());
        metadata.setYear(candidate.year());
        if (StringUtils.hasText(candidate.posterUrl())) {
            metadata.setPosterUrl(candidate.posterUrl());
            metadata.setImageSource(ProviderType.DOUBAN);
        }
        return metadata;
    }

    private String stripDoubanTitle(String value) {
        return value == null ? null : value.replaceFirst("\\s*(?:[-|·]\\s*)?(?:豆瓣(?:电影)?)\\s*$", "");
    }

    private String cleanYear(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = YEAR.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String cleanOriginalTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceFirst("^\\s*原名[:：]\\s*", "").trim();
    }

    private BigDecimal decimal(String value) {
        try {
            BigDecimal score = new BigDecimal(value.trim());
            return score.signum() >= 0 && score.compareTo(BigDecimal.TEN) <= 0 ? score : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) && !"null".equals(value) ? value.trim() : null;
    }

    private record Candidate(String id, String title, String originalTitle, String year, String posterUrl,
            boolean tvEvidence) { }
}
