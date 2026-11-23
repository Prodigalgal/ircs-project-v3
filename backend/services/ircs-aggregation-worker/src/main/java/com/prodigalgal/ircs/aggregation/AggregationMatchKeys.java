package com.prodigalgal.ircs.aggregation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.util.StringUtils;

final class AggregationMatchKeys {

    private static final int MAX_KEYS = 12;
    private static final AggregationSafetyPolicy DEFAULT_SAFETY_POLICY = new AggregationSafetyPolicy(false, "douban:123,douban:456");

    private AggregationMatchKeys() {
    }

    static List<String> forSourceRecord(SourceRecordAggregationRecord sourceRecord) {
        return forSourceRecord(sourceRecord, DEFAULT_SAFETY_POLICY);
    }

    static List<String> forSourceRecord(SourceRecordAggregationRecord sourceRecord, AggregationSafetyPolicy safetyPolicy) {
        if (sourceRecord == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addExternalId(keys, "douban", sourceRecord.doubanId(), safetyPolicy);
        addExternalId(keys, "tmdb", sourceRecord.tmdbId(), safetyPolicy);
        addExternalId(keys, "imdb", sourceRecord.imdbId(), safetyPolicy);
        addExternalId(keys, "rt", sourceRecord.rottenTomatoesId(), safetyPolicy);
        String completeTitle = AggregationTitleNormalizer.completeIdentityTitle(sourceRecord.title(), sourceRecord.subtitle());
        if (AggregationCategoryProfile.from(AggregationCategoryProfile.category(sourceRecord)) == AggregationCategoryProfile.SPORTS
                || AggregationSportsIdentity.looksLikeEvent(completeTitle)) {
            AggregationSportsIdentity event = AggregationSportsIdentity.parse(completeTitle, null);
            // The year is intentionally omitted: month/day sources can match an explicitly dated
            // source, and both writers must share one lock before a unified row exists.
            if (event.date() != null) {
                keys.add("event:" + digest(event.date() + "|" + event.details()));
                // Keep the old key during a rollout while serializing the spellings accepted
                // by the new comparison under the same lock.
                keys.add("event:" + digest(event.date() + "|" + event.identityDetails()));
            }
        }
        addTitleKeys(keys, sourceRecord.title(), sourceRecord.aliasTitle(), sourceRecord.subtitle(), sourceRecord.season());
        if (keys.isEmpty() && sourceRecord.id() != null) {
            keys.add("raw:" + sourceRecord.id());
        }
        return limit(keys);
    }

    static List<String> forCluster(SourceRecordAggregationCluster cluster) {
        return forCluster(cluster, DEFAULT_SAFETY_POLICY);
    }

    static List<String> forCluster(SourceRecordAggregationCluster cluster, AggregationSafetyPolicy safetyPolicy) {
        if (cluster == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (SourceRecordAggregationRecord sourceRecord : cluster.members()) {
            keys.addAll(forSourceRecord(sourceRecord, safetyPolicy));
        }
        for (UUID catalogEntryId : cluster.contextCatalogEntryIds()) {
            if (catalogEntryId != null) {
                keys.add("unified:" + catalogEntryId);
            }
        }
        return limit(keys);
    }

    static List<String> withMatchPlan(Collection<String> keys, AggregationMatchPlan matchPlan) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        if (keys != null) {
            all.addAll(keys);
        }
        if (matchPlan != null) {
            if (matchPlan.rootCatalogEntryId() != null) {
                all.add("unified:" + matchPlan.rootCatalogEntryId());
            }
            for (UUID victimCatalogEntryId : matchPlan.victimCatalogEntryIds()) {
                if (victimCatalogEntryId != null) {
                    all.add("unified:" + victimCatalogEntryId);
                }
            }
        }
        return limit(all);
    }

    private static void addExternalId(Set<String> keys, String provider, String value, AggregationSafetyPolicy safetyPolicy) {
        if (!safetyPolicy.isUsableExternalId(provider, value)) {
            return;
        }
        String normalized = safetyPolicy.normalizeExternalId(provider, value);
        keys.add(provider + ":" + digest(normalized));
    }

    private static void addTitleKeys(
            Set<String> keys,
            String title,
            String aliasTitle,
            String subtitle,
            Integer season) {
        List<String> titles = new ArrayList<>();
        addIfText(titles, AggregationTitleNormalizer.completeIdentityTitle(title, subtitle));
        addIfText(titles, title);
        AggregationTitleNormalizer.splitAliases(aliasTitle).stream()
                .filter(AggregationTitleNormalizer::isDistinctiveSecondaryTitle).forEach(titles::add);
        if (AggregationTitleNormalizer.isDistinctiveSecondaryTitle(subtitle)) { titles.add(subtitle); }
        Integer parsedSeason = season;
        for (String rawTitle : titles) {
            if (parsedSeason == null) {
                parsedSeason = AggregationTitleNormalizer.parseSeason(rawTitle);
            }
        }
        for (String rawTitle : titles) {
            String normalized = AggregationTitleNormalizer.normalizeTitle(rawTitle);
            if (StringUtils.hasText(normalized)) {
                // Year gaps of one and NORMAL/explicit variants may still match. A shared
                // title lock must cover those paths; unknown season remains its own identity.
                keys.add("title:" + digest(normalized + "|season=" + (parsedSeason == null ? "unknown" : parsedSeason)));
            }
        }
    }

    private static void addIfText(List<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value);
        }
    }

    /**
     * Identity keys must survive truncation: a dropped {@code douban:}/{@code tmdb:}/{@code unified:}
     * key means the match lock no longer covers that work, and two replicas can then create
     * duplicate unified rows for it. Title keys are the only surplus we are willing to drop.
     */
    private static List<String> limit(Collection<String> keys) {
        List<String> ordered = keys.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .sorted(Comparator.comparingInt(AggregationMatchKeys::keyPriority)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        List<String> identityKeys = ordered.stream()
                .filter(key -> !key.startsWith("title:"))
                .toList();
        if (identityKeys.size() >= MAX_KEYS) {
            return identityKeys;
        }

        int remainingTitleSlots = MAX_KEYS - identityKeys.size();
        List<String> titleKeys = ordered.stream()
                .filter(key -> key.startsWith("title:"))
                .limit(remainingTitleSlots)
                .toList();
        List<String> result = new ArrayList<>(identityKeys.size() + titleKeys.size());
        result.addAll(identityKeys);
        result.addAll(titleKeys);
        return List.copyOf(result);
    }

    private static int keyPriority(String key) {
        return key.startsWith("title:") ? 1 : 0;
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

}
