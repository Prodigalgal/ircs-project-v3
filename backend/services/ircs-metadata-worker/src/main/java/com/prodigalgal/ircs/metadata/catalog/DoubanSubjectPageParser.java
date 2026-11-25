package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.math.BigDecimal;
import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** Extracts identity and optional catalog fields from a normal public Douban subject page. */
final class DoubanSubjectPageParser {
    private static final Pattern SUBJECT_PATH = Pattern.compile("^/+subject/+([0-9]+)/?$");
    private static final Pattern IMDB_ID = Pattern.compile("(?i)\\btt\\d{7,10}\\b");
    private static final Pattern IMDB_PATH = Pattern.compile("(?i)^/title/(tt\\d{7,10})/?$");
    private static final Pattern YEAR = Pattern.compile("(?:^|\\D)(\\d{4})(?:\\D|$)");
    private static final Pattern FIELD_LABEL = Pattern.compile("^([^:：]{1,20})[:：]$");
    private static final Pattern ALIAS_SEPARATOR = Pattern.compile("\\s+/\\s+");
    private static final Pattern MAINLAND_RELEASE = Pattern.compile(
            "(?<!\\d)([1-9]\\d{3})[-年](\\d{1,2})[-月](\\d{1,2})日?\\s*"
                    + "(?:\\(\\s*中国大陆\\s*\\)|（\\s*中国大陆\\s*）)(?!\\s*[（(]?\\s*重映)");
    private static final Pattern VOTE_COUNT = Pattern.compile("(?:\\d+|[1-9]\\d{0,2}(?:,\\d{3})+)");

    private DoubanSubjectPageParser() { }

    static Subject read(String html, String requestedId) {
        if (html == null || html.isBlank()) throw invalidResponse();
        String normalizedRequested = AuthoritativeIds.normalizeDouban(requestedId);
        if (normalizedRequested == null) throw invalidResponse();

        var document = Jsoup.parse(html);

        Set<String> returnedIds = new LinkedHashSet<>();
        for (Element canonical : document.select("link[rel=canonical][href]")) {
            addSubjectId(returnedIds, canonical.attr("href"));
        }
        for (Element ogUrl : document.select("meta[property=\"og:url\"][content]")) {
            addSubjectId(returnedIds, ogUrl.attr("content"));
        }
        if (returnedIds.size() > 1) throw identityConflict();
        if (returnedIds.isEmpty()) throw invalidResponse();
        String id = returnedIds.iterator().next();
        if (!id.equals(normalizedRequested)) throw identityConflict();

        Element heading = document.selectFirst("h1 [property=\"v:itemreviewed\"]");
        String title = clean(heading == null ? null : heading.text());
        Element ogTitle = document.selectFirst("meta[property=\"og:title\"]");
        if (title == null && ogTitle != null) title = clean(ogTitle.attr("content"));
        if (title == null) throw invalidResponse();

        String originalTitle = null;
        List<String> aliases = List.of();
        Set<String> imdbIds = new LinkedHashSet<>();
        LocalDate mainlandReleaseDate = null;
        Element yearElement = document.selectFirst("h1 .year");
        String year = yearElement == null ? null : firstYear(yearElement.text());
        Element info = document.selectFirst("#info");
        if (info != null) {
            for (Element label : info.getAllElements()) {
                String name = label.ownText().strip();
                var matcher = FIELD_LABEL.matcher(name);
                if (matcher.matches()) name = matcher.group(1).strip();
                else if (!label.hasClass("pl")) continue;
                if ("IMDb".equalsIgnoreCase(name)) {
                    readImdbIds(fieldNodes(label), imdbIds);
                } else if ("原名".equals(name) && originalTitle == null) {
                    originalTitle = fieldText(fieldNodes(label));
                } else if ("又名".equals(name) && aliases.isEmpty()) {
                    aliases = readAliases(fieldText(fieldNodes(label)));
                } else if ("上映日期".equals(name) || "首播".equals(name)) {
                    String releaseText = fieldText(fieldNodes(label));
                    if (year == null) year = firstYear(releaseText);
                    LocalDate candidate = firstMainlandReleaseDate(releaseText);
                    if (candidate != null && (mainlandReleaseDate == null || candidate.isBefore(mainlandReleaseDate))) {
                        mainlandReleaseDate = candidate;
                    }
                }
            }
        }
        if (imdbIds.size() > 1) throw identityConflict();
        String imdbId = imdbIds.stream().findFirst().orElse(null);
        BigDecimal score = readScore(document.selectFirst("[property=\"v:average\"]"));
        Long voteCount = readVoteCount(document.selectFirst("[property=\"v:votes\"]"));
        return new Subject(id, imdbId, title, originalTitle, year, score, aliases, mainlandReleaseDate, voteCount);
    }

    private static void addSubjectId(Set<String> ids, String rawUrl) {
        URI uri = parseUri(rawUrl);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || !"movie.douban.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return;
        var matcher = SUBJECT_PATH.matcher(uri.getRawPath());
        if (matcher.matches()) {
            String normalized = AuthoritativeIds.normalizeDouban(matcher.group(1));
            if (normalized != null) ids.add(normalized);
        }
    }

    private static URI parseUri(String rawUrl) {
        try {
            return rawUrl == null || rawUrl.isBlank() ? null : URI.create(rawUrl.strip());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<Node> fieldNodes(Element label) {
        var nodes = new ArrayList<Node>();
        for (Node node = label.nextSibling(); node != null; node = node.nextSibling()) {
            if (node instanceof Element element && ("br".equals(element.normalName())
                    || element.hasClass("pl") || FIELD_LABEL.matcher(element.ownText().strip()).matches())) break;
            nodes.add(node);
        }
        return nodes;
    }

    private static String fieldText(List<Node> nodes) {
        var value = new StringBuilder();
        for (Node node : nodes) {
            if (node instanceof TextNode text) value.append(' ').append(text.text());
            else if (node instanceof Element element) value.append(' ').append(element.text());
        }
        return clean(value.toString());
    }

    private static void readImdbIds(List<Node> nodes, Set<String> ids) {
        String text = fieldText(nodes);
        if (text != null) {
            var matches = IMDB_ID.matcher(text);
            while (matches.find()) addImdbId(ids, matches.group());
        }
        for (Node node : nodes) {
            if (!(node instanceof Element element)) continue;
            for (Element link : element.select("a[href]")) {
                URI uri = parseUri(link.attr("href"));
                if (uri == null || uri.getUserInfo() != null
                        || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        || !("imdb.com".equalsIgnoreCase(uri.getHost()) || "www.imdb.com".equalsIgnoreCase(uri.getHost()))) continue;
                var matcher = IMDB_PATH.matcher(uri.getRawPath());
                if (matcher.matches()) addImdbId(ids, matcher.group(1));
            }
        }
    }

    private static void addImdbId(Set<String> ids, String raw) {
        String verified = AuthoritativeIds.normalizeImdb(raw);
        if (verified != null) ids.add(verified);
    }

    private static BigDecimal readScore(Element element) {
        if (element == null) return null;
        try {
            BigDecimal score = new BigDecimal(element.attr("content").isBlank() ? element.text() : element.attr("content").trim());
            return score.scale() >= 0 && score.scale() <= 2 && score.signum() >= 0
                    && score.compareTo(BigDecimal.TEN) <= 0 ? score : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> readAliases(String value) {
        if (value == null) return List.of();
        Set<String> aliases = new LinkedHashSet<>();
        for (String alias : ALIAS_SEPARATOR.split(value)) {
            String cleaned = clean(alias);
            if (cleaned != null) aliases.add(cleaned);
        }
        return List.copyOf(aliases);
    }

    private static LocalDate firstMainlandReleaseDate(String value) {
        if (value == null) return null;
        Matcher matcher = MAINLAND_RELEASE.matcher(value);
        LocalDate earliest = null;
        while (matcher.find()) {
            try {
                LocalDate date = LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                if (earliest == null || date.isBefore(earliest)) earliest = date;
            } catch (DateTimeException ignored) {
                // Invalid or out-of-range dates are not usable release evidence.
            }
        }
        return earliest;
    }

    private static Long readVoteCount(Element element) {
        if (element == null) return null;
        String value = clean(element.attr("content"));
        if (value == null) value = clean(element.text());
        if (value == null || !VOTE_COUNT.matcher(value).matches()) return null;
        try {
            return Long.parseLong(value.replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String firstYear(String value) {
        if (value == null) return null;
        var matcher = YEAR.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private static CatalogMetadataUnavailableException invalidResponse() {
        return new CatalogMetadataUnavailableException(CatalogDoubanSupplementSource.INVALID_RESPONSE);
    }

    private static CatalogMetadataUnavailableException identityConflict() {
        return new CatalogMetadataUnavailableException(CatalogDoubanSupplementSource.IDENTITY_CONFLICT);
    }

    record Subject(String id, String imdbId, String title, String originalTitle, String year, BigDecimal score,
                   List<String> aliases, LocalDate mainlandReleaseDate, Long voteCount) { }
}
