package com.prodigalgal.ircs.metadata.result.domain;

import com.prodigalgal.ircs.contracts.metadata.EnrichedMetadataDTO;
import com.prodigalgal.ircs.contracts.metadata.ProviderType;
import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import com.prodigalgal.ircs.contentmetadata.PersonNames;
import java.lang.Character.UnicodeScript;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MetadataResultEvidence {

    private static final int SHORT_TITLE_LENGTH = 4;
    private static final int MIN_COMPARABLE_CAST = 2;

    private MetadataResultEvidence() {}

    public static Rejection assess(RawVideoMetadataRecord raw, ProviderType provider, EnrichedMetadataDTO candidate) {
        if (provider == null || candidate == null) {
            return Rejection.INVALID_PROVIDER_ID;
        }
        IdPair douban = new IdPair(AuthoritativeIds.normalizeDouban(raw.getDoubanId()),
                AuthoritativeIds.normalizeDouban(candidate.getDoubanId()));
        IdPair tmdb = new IdPair(AuthoritativeIds.normalizeTmdb(raw.getTmdbId()),
                AuthoritativeIds.normalizeTmdb(candidate.getTmdbId()));
        IdPair imdb = new IdPair(AuthoritativeIds.normalizeImdb(raw.getImdbId()),
                AuthoritativeIds.normalizeImdb(candidate.getImdbId()));
        IdPair rottenTomatoes = new IdPair(AuthoritativeIds.normalizeRottenTomatoes(raw.getRottenTomatoesId()),
                AuthoritativeIds.normalizeRottenTomatoes(candidate.getRottenTomatoesId()));
        boolean hasProviderId = switch (provider) {
            case DOUBAN -> douban.incoming() != null;
            case TMDB -> tmdb.incoming() != null;
            case ROTTEN_TOMATOES -> rottenTomatoes.incoming() != null;
            default -> false;
        };
        if (!hasProviderId) {
            return Rejection.INVALID_PROVIDER_ID;
        }
        List<IdPair> identifiers = List.of(douban, tmdb, imdb, rottenTomatoes);
        if (identifiers.stream().anyMatch(IdPair::conflicts)) {
            return Rejection.PROVIDER_ID_CONFLICT;
        }
        if (isShortTitle(raw.getTitle()) && identifiers.stream().noneMatch(IdPair::matches)
                && !sharesPerson(raw.getDirectorNames(), candidate.getDirectorNames())
                && hasDisjointComparableCast(raw.getActorNames(), candidate.getActorNames())) {
            return Rejection.PROVIDER_CAST_CONFLICT;
        }
        return null;
    }

    private static boolean isShortTitle(String title) {
        if (title == null) {
            return false;
        }
        long length = title.codePoints().filter(Character::isLetterOrDigit).limit(SHORT_TITLE_LENGTH + 1).count();
        return length > 0 && length <= SHORT_TITLE_LENGTH;
    }

    private static boolean hasDisjointComparableCast(Set<String> source, Set<String> incoming) {
        Set<String> sourceKeys = personKeys(source);
        Set<String> incomingKeys = personKeys(incoming);
        if (sourceKeys.size() < MIN_COMPARABLE_CAST || incomingKeys.size() < MIN_COMPARABLE_CAST
                || !Collections.disjoint(sourceKeys, incomingKeys)) {
            return false;
        }
        // Different writing systems often reflect transliteration rather than different people.
        Map<UnicodeScript, Long> sourceScripts = castByScript(sourceKeys);
        Map<UnicodeScript, Long> incomingScripts = castByScript(incomingKeys);
        return sourceScripts.entrySet().stream().anyMatch(entry -> entry.getValue() >= MIN_COMPARABLE_CAST
                && incomingScripts.getOrDefault(entry.getKey(), 0L) >= MIN_COMPARABLE_CAST);
    }

    private static Map<UnicodeScript, Long> castByScript(Set<String> names) {
        return names.stream().collect(Collectors.groupingBy(
                name -> UnicodeScript.of(name.codePointAt(0)), Collectors.counting()));
    }

    private static boolean sharesPerson(Set<String> source, Set<String> incoming) {
        return !Collections.disjoint(personKeys(source), personKeys(incoming));
    }

    private static Set<String> personKeys(Set<String> names) {
        return PersonNames.normalize(names).stream()
                .map(name -> Normalizer.normalize(name, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT))
                .map(name -> name.replaceAll("[^\\p{L}\\p{M}]", ""))
                .filter(name -> name.codePointCount(0, name.length()) >= 2)
                .collect(Collectors.toSet());
    }

    public enum Rejection {
        INVALID_PROVIDER_ID("Provider result did not contain a usable provider identifier"),
        PROVIDER_ID_CONFLICT("Provider identifiers conflict with the existing raw video identity"),
        PROVIDER_CAST_CONFLICT("Short-title result conflicts with the raw video's comparable cast");

        private final String reason;

        Rejection(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    private record IdPair(String existing, String incoming) {
        private boolean conflicts() {
            return existing != null && incoming != null && !existing.equals(incoming);
        }

        private boolean matches() {
            return existing != null && existing.equals(incoming);
        }
    }
}
