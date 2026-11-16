package com.prodigalgal.ircs.metadata.provider.domain;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Selects an appropriate authority without changing source categories or inventing an identity. */
public final class MetadataEnrichmentPolicy {
    private static final Set<String> SOURCE_ONLY = Set.of("sports", "news", "education", "music", "adult", "other");
    private static final Set<String> TELEVISION = Set.of("series", "tv", "variety", "short-drama");

    private MetadataEnrichmentPolicy() {}

    public static boolean supports(MetadataSearchContext context, ProviderType provider) {
        if (context == null || provider == null || !StringUtils.hasText(context.getFullTitle())) {
            return false;
        }
        if (provider != ProviderType.DOUBAN && provider != ProviderType.TMDB && provider != ProviderType.ROTTEN_TOMATOES) {
            return false;
        }
        String category = category(context);
        if (SOURCE_ONLY.contains(category)) {
            return false;
        }
        // Domestic short-form dramas have no general RT coverage; exact TV/title/season validation
        // remains the responsibility of the TMDB and Douban adapters.
        return !"short-drama".equals(category) || provider != ProviderType.ROTTEN_TOMATOES;
    }

    public static String searchType(MetadataSearchContext context) {
        String category = category(context);
        if (SOURCE_ONLY.contains(category)) {
            return "none";
        }
        if ("movie".equals(category)) {
            return "movie";
        }
        if (TELEVISION.contains(category) || context != null && context.getSeason() != null && context.getSeason() >= 0) {
            return "tv";
        }
        // Animation/documentaries may be either films or series. Bound multi-search then checks
        // the concrete candidate type and year; a display category alone cannot choose the format.
        return "multi";
    }

    public static String strategy(MetadataSearchContext context) {
        String category = category(context);
        return switch (category) {
            case "movie" -> "MOVIE_AUTHORITY";
            case "series", "tv" -> "SERIES_AUTHORITY";
            case "anime" -> "ANIMATION_AUTHORITY";
            case "short-drama" -> "SHORT_DRAMA_AUTHORITY";
            case "variety" -> "PROGRAMME_AUTHORITY";
            case "documentary" -> "DOCUMENTARY_AUTHORITY";
            case "sports" -> "SPORTS_SOURCE";
            case "news" -> "NEWS_SOURCE";
            case "education" -> "EDUCATION_SOURCE";
            case "music" -> "MUSIC_SOURCE";
            case "adult" -> "ADULT_SOURCE";
            case "other" -> "OTHER_SOURCE";
            default -> "UNKNOWN_BOUNDED_AUTHORITY";
        };
    }

    private static String category(MetadataSearchContext context) {
        return context == null || context.getCategorySlug() == null ? ""
                : context.getCategorySlug().trim().toLowerCase(Locale.ROOT);
    }
}
