package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * IMDb's public chart pages. Both charts carry a JSON-LD {@code ItemList} of every ranked title, which
 * is far more stable than the rendered DOM and needs no undocumented endpoint.
 *
 * <p>These pages sit behind the AWS WAF challenge, so they answer only while a browser-primed session
 * is published for IMDb; without one the board reports {@link #WAF_CHALLENGE} rather than failing
 * anonymously.
 */
@Component
@Slf4j
@RequiredArgsConstructor
class ImdbTrendListProvider implements TrendListProvider {

    static final String WAF_CHALLENGE = "WAF_CHALLENGE";

    private static final String MOVIE_CHART_URL = "https://www.imdb.com/chart/moviemeter/";
    private static final String TV_CHART_URL = "https://www.imdb.com/chart/tvmeter";
    private static final Pattern TITLE_PATH = Pattern.compile("^/title/(tt[0-9]{7,10})/?$");
    private static final Set<String> IMAGE_HOSTS = Set.of("m.media-amazon.com", "ia.media-imdb.com");

    private final ScraperTrendConfigValues configValues;
    private final TrendProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "IMDb";
    }

    @Override
    public List<TrendItemPayload> fetchTrending() {
        return fetchBoards().stream().flatMap(board -> board.items().stream()).toList();
    }

    @Override
    public List<TrendBoardPayload> fetchBoards() {
        return fetchBoardsAt(Instant.now());
    }

    List<TrendBoardPayload> fetchBoardsAt(Instant observedAt) {
        List<TrendBoardPayload> boards = new ArrayList<>();
        boards.add(board("movie", MOVIE_CHART_URL, observedAt));
        boards.add(board("tv", TV_CHART_URL, observedAt));
        return List.copyOf(boards);
    }

    private TrendBoardPayload board(String mediaType, String url, Instant observedAt) {
        if (!configValues.imdbEnabled()) {
            return unavailable(mediaType, observedAt, "DISABLED", "SOURCE_DISABLED");
        }
        try {
            return parseBoard(mediaType, httpClient.get(url), observedAt);
        } catch (RuntimeException ex) {
            return unavailable(mediaType, observedAt, "FAILED", TrendSourceValues.failureCode(ex));
        }
    }

    TrendBoardPayload parseBoard(String mediaType, String html, Instant observedAt) {
        Document document = Jsoup.parse(html, "https://www.imdb.com");
        JsonNode listing = chartListing(document);
        if (listing == null) {
            // The page answered, but not with a chart: almost always the WAF interstitial.
            throw new TrendProviderFetchException(WAF_CHALLENGE);
        }
        List<TrendItemPayload> items = new ArrayList<>();
        int position = 0;
        int limit = Math.max(1, Math.min(50, configValues.maxProviderItems() / 2));
        for (JsonNode entry : listing.path("itemListElement")) {
            position++;
            if (items.size() >= limit) {
                break;
            }
            JsonNode item = entry.has("item") ? entry.path("item") : entry;
            String url = text(item, "url");
            String id = titleId(url);
            String title = text(item, "name");
            if (id == null || title == null) {
                continue;
            }
            int rank = entry.path("position").asInt(position);
            TrendSignalPayload signal = new TrendSignalPayload(name(), "popular", mediaType, id, rank,
                    "popular", null, null, observedAt, null, null, null, null, "US", url,
                    "movie".equals(mediaType) ? "movie" : "series");
            items.add(new TrendItemPayload(title, null, null, null, null,
                    rating(item.path("aggregateRating").path("ratingValue")),
                    poster(item.path("image")), null, null, id, mediaType, signal));
        }
        if (items.isEmpty()) {
            throw new TrendProviderFetchException("UNRECOGNIZED_CHART_PAGE");
        }
        return new TrendBoardPayload(name(), "popular", mediaType, "popular", observedAt, "SUCCESS", null, items);
    }

    private JsonNode chartListing(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            JsonNode node;
            try {
                node = objectMapper.readTree(script.data());
            } catch (JacksonException ex) {
                continue;
            }
            if (node != null && node.path("itemListElement").isArray()) {
                return node;
            }
        }
        return null;
    }

    private TrendBoardPayload unavailable(String mediaType, Instant observedAt, String status, String code) {
        return TrendSourceValues.unavailable(name(), "popular", mediaType, "popular", observedAt, status, code);
    }

    private static String titleId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String path = URI.create(value.trim()).getPath();
            if (path == null) {
                return null;
            }
            Matcher matcher = TITLE_PATH.matcher(path);
            return matcher.matches() ? matcher.group(1) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String poster(JsonNode image) {
        if (image == null || image.isMissingNode() || image.isNull()) {
            return null;
        }
        String value = image.isTextual() ? image.asText() : text(image, "url");
        if (!StringUtils.hasText(value) || !value.startsWith("https://")) {
            return null;
        }
        try {
            String host = URI.create(value).getHost();
            return host != null && IMAGE_HOSTS.contains(host.toLowerCase(Locale.ROOT)) ? value : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal rating(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        BigDecimal score = node.decimalValue();
        return score.signum() >= 0 && score.compareTo(BigDecimal.TEN) <= 0 ? score : null;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.strip();
    }
}
