package com.prodigalgal.ircs.scraper;

import com.prodigalgal.ircs.contracts.trend.TrendBoardPayload;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class TmdbTrendListProvider implements TrendListProvider {

    private static final String API_BASE = "https://api.themoviedb.org/3";
    private static final String IMAGE_BASE = "https://image.tmdb.org/t/p/w500";
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<Board> BOARDS = List.of(
            new Board("airing-today", "tv", "event", "/tv/airing_today"),
            new Board("trending-day", "movie", "day", "/trending/movie/day"),
            new Board("trending-day", "tv", "day", "/trending/tv/day"),
            new Board("trending-week", "movie", "week", "/trending/movie/week"),
            new Board("trending-week", "tv", "week", "/trending/tv/week"),
            new Board("recent-release", "movie", "event", "/discover/movie"));

    private final ScraperTrendConfigValues configValues;
    private final TrendProviderHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TmdbCredentialResolver credentialResolver;

    @Override
    public String name() {
        return "TMDB";
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
        if (!configValues.tmdbEnabled()) {
            return unavailableBoards(observedAt, "DISABLED", "SOURCE_DISABLED");
        }
        String apiKey = credentialResolver.resolveApiKey().orElse(null);
        if (!StringUtils.hasText(apiKey)) {
            return unavailableBoards(observedAt, "UNAVAILABLE", "CREDENTIAL_UNAVAILABLE");
        }
        int limit = Math.max(1, Math.min(20, configValues.maxProviderItems() / BOARDS.size()));
        EpisodeLookup episodes = new EpisodeLookup(apiKey, configValues.tmdbEpisodeDetailBudget());
        List<TrendBoardPayload> boards = new ArrayList<>();
        for (Board board : BOARDS) {
            if (Thread.currentThread().isInterrupted()) {
                boards.add(TrendSourceValues.unavailable(name(), board.name(), board.mediaType(),
                        board.window(), observedAt, "FAILED", "FETCH_INTERRUPTED"));
                continue;
            }
            try {
                boards.add(fetchBoard(board, apiKey, observedAt, limit, episodes));
            } catch (RuntimeException ex) {
                boards.add(TrendSourceValues.unavailable(name(), board.name(), board.mediaType(),
                        board.window(), observedAt, "FAILED", TrendSourceValues.failureCode(ex)));
            }
        }
        return List.copyOf(boards);
    }

    private TrendBoardPayload fetchBoard(Board board, String apiKey, Instant observedAt,
            int limit, EpisodeLookup episodes) {
        LocalDate today = observedAt.atZone(SOURCE_ZONE).toLocalDate();
        JsonNode root = read(endpoint(board, apiKey, today));
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            throw new TrendProviderFetchException("MISSING_RESULTS");
        }
        List<TrendItemPayload> items = new ArrayList<>();
        int position = 0;
        boolean validIdentity = false;
        boolean incompleteEventEvidence = false;
        for (JsonNode node : results) {
            position++;
            if (items.size() >= limit) {
                break;
            }
            String id = TrendSourceValues.positiveId(node.path("id").asText());
            String title = TrendSourceValues.text(node.path("movie".equals(board.mediaType()) ? "title" : "name"));
            if (id == null || title == null || node.path("adult").asBoolean(false)) {
                continue;
            }
            validIdentity = true;
            Episode episode = "tv".equals(board.mediaType())
                    ? episodes.find(id, "airing-today".equals(board.name())) : null;
            if ("airing-today".equals(board.name())
                    && (episode == null || episode.date().isAfter(today) || episode.date().isBefore(today.minusDays(1)))) {
                incompleteEventEvidence = true;
                continue;
            }
            TrendItemPayload item = item(board, node, id, title, position, episode, observedAt, today);
            if ("recent-release".equals(board.name()) && item.signal().eventDate() == null) {
                continue;
            }
            items.add(item);
        }
        if (!results.isEmpty() && items.isEmpty()) {
            throw new TrendProviderFetchException(validIdentity ? "EVENT_DETAILS_UNAVAILABLE" : "INVALID_ITEMS");
        }
        return new TrendBoardPayload(name(), board.name(), board.mediaType(), board.window(),
                observedAt, "SUCCESS", incompleteEventEvidence ? "PARTIAL_EVENT_EVIDENCE" : null, items);
    }

    private TrendItemPayload item(Board board, JsonNode node, String id, String title, int position,
            Episode episode, Instant observedAt, LocalDate today) {
        boolean movie = "movie".equals(board.mediaType());
        LocalDate publishedAt = TrendSourceValues.date(node.path(movie ? "release_date" : "first_air_date").asText());
        if (!movie && episode != null && episode.season() > 1) publishedAt = null;
        LocalDate eventDate = movie ? publishedAt : episode == null ? null : episode.date();
        if (eventDate != null && eventDate.isAfter(today)) {
            eventDate = null;
        }
        if ("recent-release".equals(board.name()) && eventDate != null && eventDate.isBefore(today.minusDays(30))) {
            eventDate = null;
        }
        Long votes = node.hasNonNull("vote_count") && node.path("vote_count").asLong(-1) >= 0
                ? node.path("vote_count").asLong() : null;
        BigDecimal score = votes != null && votes <= 0 ? null : TrendSourceValues.decimal(node.path("vote_average"));
        if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.TEN) > 0)) {
            score = null;
        }
        String poster = TrendSourceValues.text(node.path("poster_path"));
        TrendSignalPayload signal = new TrendSignalPayload(name(), board.name(), board.mediaType(), id,
                "event".equals(board.window()) ? null : position, board.window(),
                TrendSourceValues.decimal(node.path("popularity")), votes, observedAt,
                eventDate, eventDate == null ? null : movie ? "RELEASE" : "EPISODE_AIR",
                episode == null ? null : episode.season(), episode == null ? null : episode.number(),
                null, "https://www.themoviedb.org/" + board.mediaType() + "/" + id,
                category(board.mediaType(), node.path("genre_ids")));
        return new TrendItemPayload(title,
                TrendSourceValues.text(node.path(movie ? "original_title" : "original_name")),
                TrendSourceValues.text(node.path("overview")),
                publishedAt == null ? null : Integer.toString(publishedAt.getYear()), publishedAt, score,
                poster == null || !poster.startsWith("/") ? null : IMAGE_BASE + poster,
                id, null, null, board.mediaType(), signal);
    }

    private String endpoint(Board board, String apiKey, LocalDate today) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(API_BASE + board.path())
                .queryParam("api_key", apiKey).queryParam("language", "zh-CN");
        if ("recent-release".equals(board.name())) {
            uri.queryParam("primary_release_date.gte", today.minusDays(30))
                    .queryParam("primary_release_date.lte", today)
                    .queryParam("sort_by", "primary_release_date.desc")
                    .queryParam("include_adult", false).queryParam("include_video", false);
        } else if ("airing-today".equals(board.name())) {
            uri.queryParam("timezone", SOURCE_ZONE.getId());
        }
        return uri.toUriString();
    }

    private JsonNode read(String url) {
        try {
            JsonNode root = objectMapper.readTree(httpClient.get(url));
            if (root == null || !root.isObject() || !root.path("success").asBoolean(true)) {
                throw new TrendProviderFetchException("INVALID_JSON_RESPONSE");
            }
            return root;
        } catch (TrendProviderFetchException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TrendProviderFetchException("INVALID_JSON_RESPONSE");
        }
    }

    private List<TrendBoardPayload> unavailableBoards(Instant at, String status, String code) {
        return BOARDS.stream().map(board -> TrendSourceValues.unavailable(name(), board.name(),
                board.mediaType(), board.window(), at, status, code)).toList();
    }

    private static String category(String mediaType, JsonNode genres) {
        List<Integer> ids = new ArrayList<>();
        if (genres.isArray()) {
            genres.forEach(genre -> ids.add(genre.asInt()));
        }
        if (ids.contains(99)) {
            return "documentary";
        }
        if (ids.contains(16)) {
            return "anime";
        }
        if (ids.contains(10764) || ids.contains(10767)) {
            return "variety";
        }
        return "movie".equals(mediaType) ? "movie" : "series";
    }

    private final class EpisodeLookup {
        private final String apiKey;
        private final int budget;
        private final Map<String, Episode> loaded = new LinkedHashMap<>();
        private int requests;

        private EpisodeLookup(String apiKey, int budget) {
            this.apiKey = apiKey;
            this.budget = Math.max(0, Math.min(30, budget));
        }

        private Episode find(String id, boolean allowFetch) {
            if (loaded.containsKey(id)) {
                return loaded.get(id);
            }
            if (!allowFetch || requests >= budget) {
                return null;
            }
            requests++;
            Episode result = null;
            try {
                JsonNode details = read(UriComponentsBuilder.fromUriString(API_BASE + "/tv/" + id)
                        .queryParam("api_key", apiKey).queryParam("language", "zh-CN").toUriString());
                JsonNode latest = details.path("last_episode_to_air");
                LocalDate date = TrendSourceValues.date(latest.path("air_date").asText());
                int season = latest.path("season_number").asInt(0);
                int number = latest.path("episode_number").asInt(0);
                if (id.equals(details.path("id").asText()) && date != null && season > 0 && number > 0) {
                    result = new Episode(date, season, number);
                }
            } catch (RuntimeException ex) {
                // Missing details never turn first_air_date into a new-episode claim.
            }
            loaded.put(id, result);
            return result;
        }
    }

    private record Board(String name, String mediaType, String window, String path) {
    }

    private record Episode(LocalDate date, int season, int number) {
    }
}
