package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class DoubanTrendListProvider implements TrendListProvider {

    private static final String MOVIE_CHART_URL = "https://movie.douban.com/chart";
    private static final String TV_API_BASE = "https://movie.douban.com/j/search_subjects";
    private static final Pattern ID_PATTERN = Pattern.compile("^https://movie\\.douban\\.com/subject/([1-9]\\d{0,15})/?(?:[?#].*)?$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b((?:19|20)\\d{2})\\b");

    private final ScraperTrendConfigValues configValues;
    private final TrendProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "Douban";
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
        if (!configValues.doubanEnabled()) {
            return List.of(unavailable("chart", "movie", "chart", observedAt, "DISABLED", "SOURCE_DISABLED"),
                    unavailable("popular", "tv", "popular", observedAt, "DISABLED", "SOURCE_DISABLED"));
        }
        List<TrendBoardPayload> boards = new ArrayList<>();
        try {
            boards.add(movieBoard(observedAt));
        } catch (RuntimeException ex) {
            boards.add(unavailable("chart", "movie", "chart", observedAt, "FAILED", TrendSourceValues.failureCode(ex)));
        }
        try {
            boards.add(tvBoard(observedAt));
        } catch (RuntimeException ex) {
            boards.add(unavailable("popular", "tv", "popular", observedAt, "FAILED", TrendSourceValues.failureCode(ex)));
        }
        return List.copyOf(boards);
    }

    private TrendBoardPayload movieBoard(Instant observedAt) {
        Document document = Jsoup.parse(httpClient.get(MOVIE_CHART_URL));
        List<TrendItemPayload> items = new ArrayList<>();
        int position = 0;
        for (Element table : document.select("div.indent table")) {
            Element link = table.selectFirst("a.nbg");
            if (link == null) {
                continue;
            }
            position++;
            Matcher identity = ID_PATTERN.matcher(link.attr("href"));
            if (!identity.matches() || !StringUtils.hasText(link.attr("title"))) {
                continue;
            }
            if (items.size() >= boardLimit()) {
                break;
            }
            Element info = table.selectFirst("p.pl");
            Matcher year = YEAR_PATTERN.matcher(info == null ? "" : info.text());
            Element rating = table.selectFirst("span.rating_nums");
            String id = identity.group(1);
            items.add(new TrendItemPayload(link.attr("title").trim(), null, null,
                    year.find() ? year.group(1) : null, null,
                    rating(rating == null ? null : rating.text()),
                    poster(table.select("img").attr("src")), null, id, null, "movie",
                    signal("chart", "movie", "chart", id, position, observedAt)));
        }
        if (items.isEmpty()) {
            throw new TrendProviderFetchException("UNRECOGNIZED_CHART_PAGE");
        }
        return new TrendBoardPayload(name(), "chart", "movie", "chart", observedAt, "SUCCESS", null, items);
    }

    private TrendBoardPayload tvBoard(Instant observedAt) {
        String tag = URLEncoder.encode("热门", StandardCharsets.UTF_8);
        String url = TV_API_BASE + "?type=tv&tag=" + tag + "&sort=recommend&page_limit=50&page_start=0";
        JsonNode root;
        try {
            root = objectMapper.readTree(httpClient.get(url));
        } catch (TrendProviderFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TrendProviderFetchException("INVALID_JSON_RESPONSE");
        }
        if (root == null || !root.isObject() || !root.path("subjects").isArray()) {
            throw new TrendProviderFetchException("MISSING_SUBJECTS");
        }
        List<TrendItemPayload> items = new ArrayList<>();
        int position = 0;
        for (JsonNode node : root.path("subjects")) {
            position++;
            if (items.size() >= boardLimit()) {
                break;
            }
            String id = TrendSourceValues.positiveId(node.path("id").asText());
            String title = TrendSourceValues.text(node.path("title"));
            if (id == null || title == null) {
                continue;
            }
            items.add(new TrendItemPayload(title, null, null, null, null,
                    rating(TrendSourceValues.text(node.path("rate"))),
                    poster(TrendSourceValues.text(node.path("cover"))), null, id, null, "tv",
                    signal("popular", "tv", "popular", id, position, observedAt)));
        }
        if (!root.path("subjects").isEmpty() && items.isEmpty()) {
            throw new TrendProviderFetchException("INVALID_ITEMS");
        }
        return new TrendBoardPayload(name(), "popular", "tv", "popular", observedAt, "SUCCESS", null, items);
    }

    private TrendSignalPayload signal(String board, String mediaType, String window, String id,
            int position, Instant observedAt) {
        return new TrendSignalPayload(name(), board, mediaType, id, position, window,
                null, null, observedAt, null, null, null, null, "CN",
                "https://movie.douban.com/subject/" + id + "/", "movie".equals(mediaType) ? "movie" : "series");
    }

    private TrendBoardPayload unavailable(String board, String mediaType, String window, Instant at,
            String status, String code) {
        return TrendSourceValues.unavailable(name(), board, mediaType, window, at, status, code);
    }

    private int boardLimit() {
        return Math.max(1, Math.min(50, configValues.maxProviderItems() / 2));
    }

    private static BigDecimal rating(String value) {
        BigDecimal rating = TrendSourceValues.decimal(value);
        return rating != null && rating.signum() >= 0 && rating.compareTo(BigDecimal.TEN) <= 0 ? rating : null;
    }

    private static String poster(String value) {
        return StringUtils.hasText(value) && value.startsWith("https://")
                ? value.replace("s_ratio_poster", "l_ratio_poster") : null;
    }
}
