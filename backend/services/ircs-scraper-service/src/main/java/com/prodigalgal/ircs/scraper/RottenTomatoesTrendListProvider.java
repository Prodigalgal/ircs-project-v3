package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
class RottenTomatoesTrendListProvider implements TrendListProvider {

    private static final String BASE_URL = "https://www.rottentomatoes.com";
    private static final Pattern MEDIA_PATH = Pattern.compile("^/(m|tv)/([a-z0-9_-]+)(?:/s([0-9]{2,3}))?/?$");
    private static final Pattern DATED_EVENT = Pattern.compile("^(Streaming|Latest Episode:|Opens)\\s+([A-Za-z]+\\s+\\d{1,2},\\s+\\d{4})$");
    private static final List<DateTimeFormatter> EVENT_FORMATS = List.of(dateFormat("MMM d, uuuu"), dateFormat("MMMM d, uuuu"));
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Los_Angeles");

    private final ScraperTrendConfigValues configValues;
    private final TrendProviderHttpClient httpClient;

    @Override
    public String name() {
        return "RottenTomatoes";
    }

    @Override
    public List<TrendItemPayload> fetchTrending() {
        return fetchBoards().stream().flatMap(board -> board.items().stream()).toList();
    }

    @Override
    public List<TrendBoardPayload> fetchBoards() {
        return fetchBoardsAt(Instant.now());
    }

    List<TrendBoardPayload> fetchBoardsAt(Instant at) {
        List<TrendBoardPayload> boards = new ArrayList<>();
        for (String mediaType : List.of("movie", "tv")) {
            if (!configValues.rottenTomatoesEnabled()) {
                boards.add(unavailable(mediaType, at, "DISABLED", "SOURCE_DISABLED"));
                continue;
            }
            String path = "movie".equals(mediaType) ? "/browse/movies_at_home/sort:popular" : "/browse/tv_series_browse/sort:popular";
            try {
                boards.add(parseBoard(mediaType, httpClient.get(BASE_URL + path), at));
            } catch (RuntimeException ex) {
                boards.add(unavailable(mediaType, at, "FAILED", TrendSourceValues.failureCode(ex)));
            }
        }
        return List.copyOf(boards);
    }

    TrendBoardPayload parseBoard(String mediaType, String html, Instant at) {
        Document document = Jsoup.parse(html, BASE_URL);
        List<TrendItemPayload> items = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        int position = 0;
        int limit = Math.max(1, Math.min(50, configValues.maxProviderItems() / 2));
        for (Element link : document.select("a[data-qa=discovery-media-list-item-caption]")) {
            position++;
            if (items.size() >= limit) {
                break;
            }
            String path = mediaPath(link.attr("href"));
            Matcher identity = MEDIA_PATH.matcher(path == null ? "" : path);
            Element titleElement = link.selectFirst("[data-qa=discovery-media-list-item-title]");
            if (!identity.matches() || titleElement == null || !StringUtils.hasText(titleElement.text())
                    || !("movie".equals(mediaType) ? "m" : "tv").equals(identity.group(1))) {
                continue;
            }
            String slug = AuthoritativeIds.normalizeRottenTomatoes(identity.group(2));
            if (slug == null) continue;
            Integer season = identity.group(3) == null ? null : Integer.valueOf(identity.group(3));
            if (!identities.add(slug + ":" + season)) {
                continue;
            }
            Element eventElement = link.selectFirst("[data-qa=discovery-media-list-item-start-date]");
            SourceEvent event = event(eventElement == null ? null : eventElement.text(), at);
            TrendSignalPayload signal = new TrendSignalPayload(name(), "popular", mediaType, slug,
                    position, "popular", null, null, at,
                    event == null ? null : event.date(), event == null ? null : event.type(), season, null,
                    "US", BASE_URL + path, "movie".equals(mediaType) ? "movie" : "series");
            // Percentage scores and streaming dates are not a 10-point rating or original release year.
            items.add(new TrendItemPayload(titleElement.text().trim(), null, null, null, null, null,
                    poster(link), null, null, null, mediaType, signal));
        }
        if (items.isEmpty()) {
            throw new TrendProviderFetchException("UNRECOGNIZED_BROWSE_PAGE");
        }
        return new TrendBoardPayload(name(), "popular", mediaType, "popular", at, "SUCCESS", null, items);
    }

    private TrendBoardPayload unavailable(String mediaType, Instant at, String status, String code) {
        return TrendSourceValues.unavailable(name(), "popular", mediaType, "popular", at, status, code);
    }

    private static String mediaPath(String href) {
        try {
            URI uri = URI.create(href);
            if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.isAbsolute() && (!"https".equals(uri.getScheme()) || !"www.rottentomatoes.com".equalsIgnoreCase(uri.getHost()))
                    || !uri.isAbsolute() && uri.getRawAuthority() != null) {
                return null;
            }
            return uri.getPath();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String poster(Element link) {
        Element tile = link.closest("media-info-tile");
        Element image = tile == null ? null : tile.selectFirst("rt-img[src], img[src]");
        String value = image == null ? null : image.attr("src");
        return StringUtils.hasText(value) && value.startsWith("https://") ? value : null;
    }

    private static SourceEvent event(String text, Instant at) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher event = DATED_EVENT.matcher(text.trim().replaceAll("\\s+", " "));
        if (!event.matches()) {
            // A yearless "Latest Episode: Nov 11" cannot establish a current-year event.
            return null;
        }
        LocalDate today = at.atZone(SOURCE_ZONE).toLocalDate();
        for (DateTimeFormatter format : EVENT_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(event.group(2), format);
                if (date.isAfter(today)) {
                    return null;
                }
                String type = switch (event.group(1)) {
                    case "Streaming" -> "DIGITAL_RELEASE";
                    case "Latest Episode:" -> "EPISODE_AIR";
                    default -> "THEATRICAL_RELEASE";
                };
                return new SourceEvent(date, type);
            } catch (DateTimeParseException ex) {
                // Try the other explicit month spelling; never infer an omitted year.
            }
        }
        return null;
    }

    private static DateTimeFormatter dateFormat(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    private record SourceEvent(LocalDate date, String type) {
    }
}
