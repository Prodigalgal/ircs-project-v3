package com.prodigalgal.ircs.aggregation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.text.Normalizer;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class AuthorityCatalogBindingPolicy {

    private static final Pattern RELEASE_YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");

    AuthorityCatalogBindingDecision decide(
            RawVideoAggregationRecord raw,
            String mediaType,
            List<AuthorityCatalogProjection> projections) {
        return decide(raw, Set.of(mediaType), projections);
    }

    AuthorityCatalogBindingDecision decide(
            RawVideoAggregationRecord raw,
            Set<String> mediaTypes,
            List<AuthorityCatalogProjection> projections) {
        String rawYear = year(raw.year());
        Set<String> titleKeys = rawTitleKeys(raw);
        if (rawYear == null || titleKeys.isEmpty()) {
            return AuthorityCatalogBindingDecision.waitForAuthority();
        }
        List<AuthorityCatalogProjection> titleAndYearMatches = projections.stream()
                .filter(candidate -> mediaTypes.contains(candidate.mediaType()))
                .filter(candidate -> matchesTitle(candidate, titleKeys))
                .filter(candidate -> compatibleYear(raw, rawYear, candidate))
                .toList();
        if (titleAndYearMatches.isEmpty()) {
            return AuthorityCatalogBindingDecision.waitForAuthority();
        }

        List<AuthorityCatalogProjection> nonContradicting = titleAndYearMatches.stream()
                .filter(candidate -> !externalIdsContradict(raw, candidate))
                .toList();
        if (nonContradicting.isEmpty()) {
            return AuthorityCatalogBindingDecision.review("IDENTITY_CONFLICT");
        }
        long candidateWorks = nonContradicting.stream().map(AuthorityCatalogProjection::workId).distinct().count();
        if (candidateWorks > 1) {
            Set<java.util.UUID> idMatchedWorks = nonContradicting.stream()
                    .filter(candidate -> matchesProvidedExternalId(raw, candidate))
                    .map(AuthorityCatalogProjection::workId)
                    .collect(java.util.stream.Collectors.toSet());
            if (idMatchedWorks.size() != 1) {
                return AuthorityCatalogBindingDecision.review("MERGE_AMBIGUOUS");
            }
            nonContradicting = nonContradicting.stream()
                    .filter(candidate -> idMatchedWorks.contains(candidate.workId()))
                    .toList();
        }

        List<AuthorityCatalogProjection> seasonMatches = new ArrayList<>();
        for (AuthorityCatalogProjection candidate : nonContradicting) {
            Integer season = requestedSeason(raw, candidate);
            if (season != null && season == candidate.seasonNumber()) {
                seasonMatches.add(candidate);
            }
        }
        if (seasonMatches.size() == 1) {
            AuthorityCatalogProjection candidate = seasonMatches.getFirst();
            return AuthorityCatalogBindingDecision.bind(candidate.unifiedVideoId(), evidenceKind(raw, candidate));
        }
        if (seasonMatches.size() > 1) {
            return AuthorityCatalogBindingDecision.review("MERGE_AMBIGUOUS");
        }
        return AuthorityCatalogBindingDecision.waitForAuthority();
    }

    private Integer requestedSeason(RawVideoAggregationRecord raw, AuthorityCatalogProjection candidate) {
        if ("movie".equals(candidate.mediaType())) {
            return -1;
        }
        if (raw.season() != null) {
            return raw.season();
        }
        return Integer.valueOf(1).equals(candidate.regularSeasonCount()) ? 1 : null;
    }

    private boolean compatibleYear(
            RawVideoAggregationRecord raw,
            String rawYear,
            AuthorityCatalogProjection candidate) {
        Integer sourceYear = Integer.valueOf(rawYear);
        Integer workReleaseDateYear = candidate.workReleasedOn() == null
                ? null : candidate.workReleasedOn().getYear();
        Integer seasonYear = candidate.seasonReleasedOn() == null
                ? null : candidate.seasonReleasedOn().getYear();
        if (exactYear(sourceYear, candidate.releaseYear())
                || exactYear(sourceYear, workReleaseDateYear)
                || exactYear(sourceYear, seasonYear)) {
            return true;
        }
        boolean seasonKnown = "movie".equals(candidate.mediaType()) || raw.season() != null
                || Integer.valueOf(1).equals(candidate.regularSeasonCount());
        return seasonKnown && hasCorroboratingExternalId(raw, candidate)
                && (nearYear(sourceYear, candidate.releaseYear())
                        || nearYear(sourceYear, workReleaseDateYear)
                        || nearYear(sourceYear, seasonYear));
    }

    private boolean exactYear(Integer left, Integer right) {
        return right != null && left.equals(right);
    }

    private boolean nearYear(Integer left, Integer right) {
        return right != null && Math.abs(left - right) <= 1;
    }

    private boolean matchesTitle(AuthorityCatalogProjection candidate, Set<String> titleKeys) {
        return titleKeys.contains(candidate.titleKey())
                || candidate.originalTitleKey() != null && titleKeys.contains(candidate.originalTitleKey());
    }

    static Set<String> rawTitleKeys(RawVideoAggregationRecord raw) {
        java.util.stream.Stream<String> cleanTitles = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(raw.title(), AggregationRecallKeys.completeTitle(raw.title(), raw.subtitle())),
                AggregationTitleNormalizer.splitAliases(raw.aliasTitle()).stream());
        return cleanTitles
                .filter(StringUtils::hasText)
                .map(AuthorityCatalogBindingPolicy::normalizeAuthorityTitleKey)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static String normalizeAuthorityTitleKey(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    static OptionalLong importTmdbId(RawVideoAggregationRecord raw) {
        if (!StringUtils.hasText(raw.tmdbId())) {
            return OptionalLong.empty();
        }
        try {
            long value = Long.parseLong(raw.tmdbId().trim());
            return value > 0 ? OptionalLong.of(value) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    static boolean hasImportRequestEvidence(RawVideoAggregationRecord raw) {
        return StringUtils.hasText(raw.title()) && year(raw.year()) != null && !rawTitleKeys(raw).isEmpty();
    }

    private static String year(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = RELEASE_YEAR.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean externalIdsContradict(RawVideoAggregationRecord raw, AuthorityCatalogProjection candidate) {
        return differs(numericId(raw.tmdbId()), candidate.tmdbId() == null ? null : candidate.tmdbId().toString())
                || differs(raw.imdbId(), candidate.imdbId());
    }

    private String numericId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.toString(Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return value.trim();
        }
    }

    private boolean differs(String rawId, String authorityId) {
        return StringUtils.hasText(rawId) && StringUtils.hasText(authorityId)
                && !rawId.trim().toLowerCase(Locale.ROOT).equals(authorityId.trim().toLowerCase(Locale.ROOT));
    }

    private boolean hasCorroboratingExternalId(RawVideoAggregationRecord raw, AuthorityCatalogProjection candidate) {
        return matchesProvidedExternalId(raw, candidate);
    }

    private boolean matchesProvidedExternalId(RawVideoAggregationRecord raw, AuthorityCatalogProjection candidate) {
        return StringUtils.hasText(raw.tmdbId()) && candidate.tmdbId() != null
                        && numericId(raw.tmdbId()).equals(candidate.tmdbId().toString())
                || StringUtils.hasText(raw.imdbId()) && StringUtils.hasText(candidate.imdbId())
                        && raw.imdbId().trim().equalsIgnoreCase(candidate.imdbId().trim());
    }

    private String evidenceKind(RawVideoAggregationRecord raw, AuthorityCatalogProjection candidate) {
        if (StringUtils.hasText(raw.tmdbId()) && candidate.tmdbId() != null
                && numericId(raw.tmdbId()).equals(candidate.tmdbId().toString())) {
            return "TMDB_TITLE_YEAR_SEASON";
        }
        if (StringUtils.hasText(raw.imdbId()) && StringUtils.hasText(candidate.imdbId())
                && raw.imdbId().trim().equalsIgnoreCase(candidate.imdbId().trim())) {
            return "IMDB_TITLE_YEAR_SEASON";
        }
        return "TITLE_YEAR_SEASON";
    }
}
