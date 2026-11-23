package com.prodigalgal.ircs.metadata.catalog;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderRetryableException;
import com.prodigalgal.ircs.metadata.provider.domain.MetadataProviderTerminalException;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbCredential;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbCredentialRepository;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbHttpClient;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbProviderProperties;
import com.prodigalgal.ircs.metadata.provider.tmdb.TmdbRateLimiter;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Uses the existing pool-aware TMDB transport, public traffic limiter and shared key rate limiter. */
@Component
@RequiredArgsConstructor
public class CatalogTmdbMetadataSource implements CatalogMetadataSource {
    private final TmdbCredentialRepository credentials;
    private final TmdbRateLimiter rateLimiter;
    private final TmdbHttpClient http;
    private final TmdbProviderProperties properties;
    private final ObjectMapper mapper;
    private final AtomicLong cursor = new AtomicLong();
    private volatile List<TmdbCredential> cachedCredentials = List.of();
    private volatile long credentialCacheUntil;

    @Override
    public CatalogMetadataEvidence fetch(CatalogMetadataTarget target) {
        String expected = AuthoritativeIds.normalizeTmdb(target.tmdbId());
        if (expected == null) throw new CatalogMetadataUnavailableException("NO_VERIFIABLE_PROVIDER");
        return withCredential(credential -> fetchWithCredential(target, expected, credential));
    }

    AuthorityCatalogEvidence fetchAuthority(String mediaType, long tmdbId, int requestedSeason) {
        return withCredential(credential -> {
            String expected = Long.toString(tmdbId);
            boolean movie = "movie".equals(mediaType);
            JsonNode work = readWork(credential, mediaType, expected, !movie);
            LocalDate released = date(work.path(movie ? "release_date" : "first_air_date"));
            String category = categoryCode(work, movie);
            if (movie) {
                return new AuthorityCatalogEvidence(mediaType, tmdbId, -1, released, category,
                        metadata(work, work, expected, true), List.of(), List.of(), TmdbCatalogCredits.movie(work));
            }
            List<AuthorityCatalogEvidence.Season> seasons = seasonList(work);
            AuthorityCatalogEvidence.Season season = seasons.stream()
                    .filter(candidate -> requestedSeason == -1 ? candidate.number() > 0 : candidate.number() == requestedSeason)
                    .min(Comparator.comparingInt(AuthorityCatalogEvidence.Season::number))
                    .orElseThrow(() -> new CatalogMetadataUnavailableException("CATALOG_SEASON_UNVERIFIED"));
            JsonNode scoped = readSeason(credential, expected, season.number(), Long.toString(season.tmdbId()));
            var episodes = episodeList(scoped, season.number());
            return new AuthorityCatalogEvidence(mediaType, tmdbId, season.number(), released, category,
                    metadata(work, scoped, expected, false), seasons, episodes,
                    TmdbCatalogCredits.television(work, scoped, season.number()));
        });
    }

    JsonNode changes(String mediaType, LocalDate day, int page) {
        if (!Set.of("movie", "tv").contains(mediaType) || page < 1) throw new IllegalArgumentException("INVALID_CHANGE_SCOPE");
        return withCredential(credential -> {
            URI uri = TmdbChangePage.uri(properties.getBaseUrl(), credential.apiKey(), mediaType, day, page);
            return readJson(credential, uri);
        });
    }

    private <T> T withCredential(Function<TmdbCredential, T> load) {
        List<TmdbCredential> enabled = enabledCredentials();
        if (enabled.isEmpty()) throw new CatalogMetadataUnavailableException("CREDENTIAL_UNAVAILABLE");
        int start = Math.floorMod(cursor.getAndIncrement(), enabled.size());
        RuntimeException lastFailure = null;
        for (int index = 0; index < Math.min(2, enabled.size()); index++) {
            TmdbCredential credential = enabled.get((start + index) % enabled.size());
            try {
                return load.apply(credential);
            } catch (MetadataProviderRetryableException | MetadataProviderTerminalException ex) {
                lastFailure = ex;
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new CatalogMetadataUnavailableException("CREDENTIAL_UNAVAILABLE");
    }

    private CatalogMetadataEvidence fetchWithCredential(CatalogMetadataTarget target, String expected, TmdbCredential credential) {
        String type = target.mediaType();
        boolean movie = "movie".equals(type);
        JsonNode root = readWork(credential, type, expected, false);
        JsonNode scoped = root;
        if (!movie && target.season() > 0) {
            var season = seasonList(root).stream().filter(candidate -> candidate.number() == target.season())
                    .findFirst().orElseThrow(() -> new CatalogMetadataUnavailableException("CATALOG_SEASON_UNVERIFIED"));
            scoped = readSeason(credential, expected, season.number(), Long.toString(season.tmdbId()));
        }
        return new CatalogMetadataEvidence(expected, type, target.season(), metadata(root, scoped, expected, movie));
    }

    private JsonNode readWork(TmdbCredential credential, String type, String expected, boolean aggregateCredits) {
        if (!Set.of("movie", "tv").contains(type)) throw new CatalogMetadataUnavailableException("INVALID_MEDIA_TYPE");
        String append = aggregateCredits ? "aggregate_credits,external_ids" : "credits,external_ids";
        JsonNode root = read(credential, "/" + type + "/" + expected, append);
        String actualId = AuthoritativeIds.normalizeTmdb(root.path("id").asText(null));
        boolean movie = "movie".equals(type);
        boolean correctShape = movie ? root.hasNonNull("title") && root.has("release_date") && !root.has("first_air_date")
                : root.hasNonNull("name") && root.has("first_air_date") && !root.has("release_date");
        if (!expected.equals(actualId) || !correctShape
                || root.hasNonNull("media_type") && !type.equals(root.path("media_type").asText())) {
            throw new CatalogMetadataUnavailableException("CATALOG_IDENTITY_MISMATCH");
        }
        if (root.path("adult").asBoolean(false)) throw new CatalogMetadataUnavailableException("ADULT_SOURCE_RECORD");
        return root;
    }

    private JsonNode readSeason(TmdbCredential credential, String tmdbId, int number, String seasonId) {
        JsonNode scoped = read(credential, "/tv/" + tmdbId + "/season/" + number, "credits");
        if (!seasonId.equals(AuthoritativeIds.normalizeTmdb(scoped.path("id").asText(null)))
                || scoped.path("season_number").asInt(-1) != number) {
            throw new CatalogMetadataUnavailableException("CATALOG_SEASON_MISMATCH");
        }
        for (JsonNode episode : scoped.path("episodes")) {
            if (episode.hasNonNull("show_id") && !tmdbId.equals(episode.path("show_id").asText())) {
                throw new CatalogMetadataUnavailableException("CATALOG_SEASON_MISMATCH");
            }
        }
        return scoped;
    }

    private List<AuthorityCatalogEvidence.Season> seasonList(JsonNode root) {
        var seasons = new ArrayList<AuthorityCatalogEvidence.Season>();
        Set<Integer> numbers = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode node : root.path("seasons")) {
            int number = node.path("season_number").asInt(-1);
            long id = node.path("id").asLong(0);
            if (number < 0 || number > 1000 || id <= 0 || !numbers.add(number) || !ids.add(id)) {
                throw new CatalogMetadataUnavailableException("CATALOG_SEASON_MISMATCH");
            }
            seasons.add(new AuthorityCatalogEvidence.Season(number, id, text(node.path("name")), date(node.path("air_date"))));
        }
        return List.copyOf(seasons);
    }

    private List<AuthorityCatalogEvidence.Episode> episodeList(JsonNode season, int number) {
        JsonNode episodes = season.path("episodes");
        if (!episodes.isArray()) throw new CatalogMetadataUnavailableException("CATALOG_EPISODES_UNVERIFIED");
        var results = new ArrayList<AuthorityCatalogEvidence.Episode>();
        Set<Integer> numbers = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode node : episodes) {
            int episode = node.path("episode_number").asInt(0);
            long id = node.path("id").asLong(0);
            if (episode <= 0 || node.path("season_number").asInt(-1) != number
                    || id <= 0 || !numbers.add(episode) || !ids.add(id)) {
                throw new CatalogMetadataUnavailableException("CATALOG_EPISODE_MISMATCH");
            }
            results.add(new AuthorityCatalogEvidence.Episode(episode, id, text(node.path("name")), date(node.path("air_date"))));
        }
        return List.copyOf(results);
    }

    private String categoryCode(JsonNode root, boolean movie) {
        Set<Integer> genres = new HashSet<>();
        for (JsonNode genre : root.path("genres")) genres.add(genre.path("id").asInt());
        if (genres.contains(99)) return "documentary";
        if (movie) return "movie";
        if (genres.contains(16)) return "anime";
        return genres.contains(10764) || genres.contains(10767) ? "variety" : "series";
    }

    private EnrichedMetadataDTO metadata(JsonNode root, JsonNode scoped, String actualId, boolean movie) {
        EnrichedMetadataDTO metadata = new EnrichedMetadataDTO();
        metadata.setTmdbId(actualId);
        metadata.setImdbId(AuthoritativeIds.normalizeImdb(root.path("external_ids").path("imdb_id").asText(
                root.path("imdb_id").asText(null))));
        metadata.setTitle(text(root.path(movie ? "title" : "name")));
        metadata.setOriginalTitle(text(root.path(movie ? "original_title" : "original_name")));
        metadata.setDescription(firstText(scoped.path("overview"), root.path("overview")));
        LocalDate date = date(scoped != root ? scoped.path("air_date") : root.path(movie ? "release_date" : "first_air_date"));
        metadata.setPublishedAt(date);
        metadata.setYear(date == null ? null : Integer.toString(date.getYear()));
        String poster = firstText(scoped.path("poster_path"), root.path("poster_path"));
        if (poster != null && poster.startsWith("/")) {
            metadata.setPosterUrl(properties.getImageBaseUrl() + poster);
            metadata.setImageSource(ProviderType.TMDB);
        }
        if (root.path("vote_count").asLong(0) > 0 && root.hasNonNull("vote_average")) {
            try {
                BigDecimal score = new BigDecimal(root.path("vote_average").asText());
                if (score.signum() >= 0 && score.compareTo(BigDecimal.TEN) <= 0) metadata.setScore(score);
            } catch (NumberFormatException ex) {
                throw new CatalogMetadataUnavailableException("SOURCE_INVALID_SCORE");
            }
        }
        JsonNode countries = root.path("production_countries");
        if (countries.isArray() && !countries.isEmpty()) metadata.setArea(text(countries.get(0).path("iso_3166_1")));
        if (metadata.getArea() == null && root.path("origin_country").isArray() && !root.path("origin_country").isEmpty()) {
            metadata.setArea(text(root.path("origin_country").get(0)));
        }
        metadata.setLanguage(text(root.path("original_language")));
        metadata.setActorNames(displayNames(scoped, root, false));
        metadata.setDirectorNames(displayNames(scoped, root, true));
        for (JsonNode genre : root.path("genres")) {
            String name = text(genre.path("name"));
            if (name != null && metadata.getGenreNames().size() < 20) metadata.addGenre(name);
        }
        return metadata;
    }

    private Set<String> displayNames(JsonNode scoped, JsonNode work, boolean directors) {
        Set<String> names = displayNames(scoped.path("credits"), directors);
        if (!names.isEmpty()) return names;
        if (scoped != work) {
            names = displayNames(work.path("credits"), directors);
            if (!names.isEmpty()) return names;
        }
        return displayNames(work.path("aggregate_credits"), directors);
    }

    private Set<String> displayNames(JsonNode credits, boolean directors) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode person : credits.path(directors ? "crew" : "cast")) {
            if (names.size() >= 15) break;
            if (directors && !directs(person)) continue;
            String name = firstText(person.path("name"), person.path("original_name"));
            if (name != null) names.add(name);
        }
        return PersonNames.normalize(names);
    }

    private boolean directs(JsonNode person) {
        if ("Director".equals(person.path("job").asText())) return true;
        for (JsonNode job : person.path("jobs")) {
            if ("Director".equals(job.path("job").asText())) return true;
        }
        return false;
    }

    private JsonNode read(TmdbCredential credential, String path, String append) {
        URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl() + path)
                .queryParam("api_key", credential.apiKey()).queryParam("language", properties.getLanguage())
                .queryParam("append_to_response", append).build().encode().toUri();
        return readJson(credential, uri);
    }

    private JsonNode readJson(TmdbCredential credential, URI uri) {
        rateLimiter.acquire(credential);
        String body = http.getJson(uri).orElseThrow(() -> new CatalogMetadataUnavailableException("SOURCE_NOT_FOUND"));
        if (body.length() > 2_000_000) throw new CatalogMetadataUnavailableException("SOURCE_RESPONSE_TOO_LARGE");
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject()) throw new CatalogMetadataUnavailableException("SOURCE_INVALID_RESPONSE");
            return root;
        } catch (CatalogMetadataUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CatalogMetadataUnavailableException("SOURCE_INVALID_RESPONSE");
        }
    }

    private List<TmdbCredential> enabledCredentials() {
        long at = System.nanoTime();
        if (at < credentialCacheUntil) return cachedCredentials;
        synchronized (this) {
            if (System.nanoTime() >= credentialCacheUntil) {
                cachedCredentials = List.copyOf(credentials.findEnabled());
                credentialCacheUntil = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            }
            return cachedCredentials;
        }
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String text = node.asText().trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    private static String firstText(JsonNode preferred, JsonNode fallback) {
        String first = text(preferred);
        return first == null ? text(fallback) : first;
    }

    private static LocalDate date(JsonNode node) {
        String value = text(node);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
