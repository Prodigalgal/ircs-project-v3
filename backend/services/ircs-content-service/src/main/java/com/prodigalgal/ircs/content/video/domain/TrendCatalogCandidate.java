package com.prodigalgal.ircs.content.video.domain;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contracts.trend.TrendItemPayload;
import com.prodigalgal.ircs.contracts.trend.TrendSignalPayload;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Typed identity and conservative matching inputs, separate from the ranking evidence. */
public record TrendCatalogCandidate(
        TrendItemPayload item, String provider, String mediaType, String externalId,
        int season, int episode, String categoryCode, String titleKey) {

    private static final Set<String> TV_CATEGORIES = Set.of(
            "series", "anime", "short-drama", "variety", "documentary", "education", "music", "news", "sports");

    public static TrendCatalogCandidate from(TrendItemPayload item) {
        if (item == null || item.title() == null || item.title().isBlank() || item.title().length() > 500) {
            throw new IllegalArgumentException("INVALID_TREND_TITLE");
        }
        TrendSignalPayload signal = item.signal();
        String type = signal == null ? item.type() : signal.mediaType();
        if (!Set.of("movie", "tv").contains(type == null ? "" : type)) {
            throw new IllegalArgumentException("INVALID_TREND_MEDIA_TYPE");
        }
        if (item.type() != null && !item.type().equals(type)) {
            throw new IllegalArgumentException("CONFLICTING_TREND_MEDIA_TYPE");
        }
        String provider = signal == null ? legacyProvider(item) : signal.provider();
        String rawId = signal == null ? legacyId(item, provider) : signal.externalId();
        String id = switch (provider == null ? "" : provider) {
            case "TMDB" -> AuthoritativeIds.normalizeTmdb(rawId);
            case "Douban" -> AuthoritativeIds.normalizeDouban(rawId);
            case "IMDb" -> AuthoritativeIds.normalizeImdb(rawId);
            case "RottenTomatoes" -> AuthoritativeIds.normalizeRottenTomatoes(rawId);
            default -> null;
        };
        if (id == null) {
            throw new IllegalArgumentException("INVALID_TREND_EXTERNAL_ID");
        }
        String reportedId = switch (provider) {
            case "TMDB" -> AuthoritativeIds.normalizeTmdb(item.tmdbId());
            case "Douban" -> AuthoritativeIds.normalizeDouban(item.doubanId());
            case "IMDb" -> AuthoritativeIds.normalizeImdb(item.imdbId());
            default -> null;
        };
        if (reportedId != null && !reportedId.equals(id)) {
            throw new IllegalArgumentException("CONFLICTING_TREND_EXTERNAL_ID");
        }
        int season = signal == null || signal.season() == null ? 0 : signal.season();
        int episode = signal == null || signal.episode() == null ? 0 : signal.episode();
        if (season < 0 || season > 1000 || episode < 0 || episode > 10000 || ("movie".equals(type) && season > 0)) {
            throw new IllegalArgumentException("INVALID_TREND_SEASON_EPISODE");
        }
        String category = signal == null ? null : signal.categoryCode();
        if ("movie".equals(type)) {
            category = "movie";
        } else if (!TV_CATEGORIES.contains(category == null ? "" : category)) {
            category = "series";
        }
        return new TrendCatalogCandidate(item, provider, type, id, season, episode, category, titleKey(item.title()));
    }

    public static String titleKey(String title) {
        return title == null ? "" : Normalizer.normalize(title, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    public String identityKey() {
        return provider + ":" + mediaType + ":" + externalId + ":" + season;
    }

    private static String legacyProvider(TrendItemPayload item) {
        if (AuthoritativeIds.normalizeDouban(item.doubanId()) != null) return "Douban";
        if (AuthoritativeIds.normalizeTmdb(item.tmdbId()) != null) return "TMDB";
        return "IMDb";
    }

    private static String legacyId(TrendItemPayload item, String provider) {
        return switch (provider) {
            case "Douban" -> item.doubanId();
            case "TMDB" -> item.tmdbId();
            default -> item.imdbId();
        };
    }
}
