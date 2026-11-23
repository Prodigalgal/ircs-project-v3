package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
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
            SourceRecordAggregationRecord raw,
            String mediaType,
            List<AuthorityCatalogProjection> projections) {
        return decide(raw, Set.of(mediaType), projections);
    }

    AuthorityCatalogBindingDecision decide(
            SourceRecordAggregationRecord raw,
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
            return AuthorityCatalogBindingDecision.bind(candidate.catalogEntryId(), evidenceKind(raw, candidate));
        }
        if (seasonMatches.size() > 1) {
            return AuthorityCatalogBindingDecision.review("MERGE_AMBIGUOUS");
        }
        return AuthorityCatalogBindingDecision.waitForAuthority();
    }

    private Integer requestedSeason(SourceRecordAggregationRecord raw, AuthorityCatalogProjection candidate) {
        if ("movie".equals(candidate.mediaType())) {
            return -1;
        }
        if (raw.season() != null) {
            return raw.season();
        }
        return Integer.valueOf(1).equals(candidate.regularSeasonCount()) ? 1 : null;
    }

    private boolean compatibleYear(
            SourceRecordAggregationRecord raw,
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

    static Set<String> rawTitleKeys(SourceRecordAggregationRecord raw) {
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

    static OptionalLong importTmdbId(SourceRecordAggregationRecord raw) {
        Long normalized = normalizeTmdbId(raw.tmdbId());
        return normalized == null ? OptionalLong.empty() : OptionalLong.of(normalized);
    }

    static Long normalizeTmdbId(String value) {
        String normalized = AuthoritativeIds.normalizeTmdb(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String normalizeImdbId(String value) {
        return AuthoritativeIds.normalizeImdb(value);
    }

    static boolean hasImportRequestEvidence(SourceRecordAggregationRecord raw) {
        return StringUtils.hasText(raw.title()) && year(raw.year()) != null && !rawTitleKeys(raw).isEmpty();
    }

    private static String year(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = RELEASE_YEAR.matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Long normalizedTmdbId(SourceRecordAggregationRecord raw) {
        return normalizeTmdbId(raw.tmdbId());
    }

    private String normalizedImdbId(SourceRecordAggregationRecord raw) {
        return normalizeImdbId(raw.imdbId());
    }

    private boolean externalIdsContradict(SourceRecordAggregationRecord raw, AuthorityCatalogProjection candidate) {
        return differs(normalizedTmdbId(raw), candidate.tmdbId())
                || differs(normalizedImdbId(raw), normalizeImdbId(candidate.imdbId()));
    }

    private boolean differs(Long rawId, Long authorityId) {
        return rawId != null && authorityId != null && !rawId.equals(authorityId);
    }

    private boolean differs(String rawId, String authorityId) {
        return rawId != null && authorityId != null && !rawId.equals(authorityId);
    }

    private boolean hasCorroboratingExternalId(SourceRecordAggregationRecord raw, AuthorityCatalogProjection candidate) {
        return matchesProvidedExternalId(raw, candidate);
    }

    private boolean matchesProvidedExternalId(SourceRecordAggregationRecord raw, AuthorityCatalogProjection candidate) {
        Long tmdbId = normalizedTmdbId(raw);
        String imdbId = normalizedImdbId(raw);
        return tmdbId != null && candidate.tmdbId() != null && tmdbId.equals(candidate.tmdbId())
                || imdbId != null && imdbId.equals(normalizeImdbId(candidate.imdbId()));
    }

    private String evidenceKind(SourceRecordAggregationRecord raw, AuthorityCatalogProjection candidate) {
        Long tmdbId = normalizedTmdbId(raw);
        if (tmdbId != null && tmdbId.equals(candidate.tmdbId())) {
            return "TMDB_TITLE_YEAR_SEASON";
        }
        String imdbId = normalizedImdbId(raw);
        if (imdbId != null && imdbId.equals(normalizeImdbId(candidate.imdbId()))) {
            return "IMDB_TITLE_YEAR_SEASON";
        }
        return "TITLE_YEAR_SEASON";
    }

}
