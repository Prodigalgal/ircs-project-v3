package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationMatchKeysTest {

    @Test
    void titleVariantsShareFallbackMatchKey() {
        RawVideoAggregationRecord left = rawVideo("Codex Signal 1080p", null);
        RawVideoAggregationRecord right = rawVideo("Codex Signal", null);

        List<String> leftKeys = AggregationMatchKeys.forRawVideo(left);
        List<String> rightKeys = AggregationMatchKeys.forRawVideo(right);

        assertThat(leftKeys).containsAnyElementsOf(rightKeys);
        assertThat(leftKeys).allMatch(key -> key.startsWith("title:"));
    }

    @Test
    void externalIdsProduceProviderSpecificKeysBeforeTitleFallback() {
        RawVideoAggregationRecord rawVideo = rawVideo("Codex Signal", "1234567");

        List<String> keys = AggregationMatchKeys.forRawVideo(rawVideo);

        assertThat(keys).hasSizeGreaterThan(1);
        assertThat(keys.getFirst()).startsWith("douban:");
        assertThat(keys).anyMatch(key -> key.startsWith("title:"));
    }

    @Test
    void clusterKeysKeepIdentityKeysWhenTruncating() {
        // Clusters can have more than MAX_KEYS provider/unified keys. They all protect a distinct
        // identity and must remain in the lock set even though title fallback keys are capped.
        List<RawVideoAggregationRecord> members = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            members.add(rawVideoWithIds(
                    "Codex Signal " + index,
                    "douban-" + index,
                    "tmdb-" + index,
                    "imdb-" + index,
                    "rt-" + index));
        }
        List<UUID> unifiedIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        RawVideoAggregationCluster cluster = new RawVideoAggregationCluster(members.getFirst(), members, unifiedIds);

        List<String> keys = AggregationMatchKeys.forCluster(cluster);

        List<String> expectedIdentityKeys = new java.util.ArrayList<>();
        members.forEach(member -> expectedIdentityKeys.addAll(AggregationMatchKeys.forRawVideo(member).stream()
                .filter(key -> !key.startsWith("title:"))
                .toList()));
        unifiedIds.forEach(id -> expectedIdentityKeys.add("unified:" + id));
        assertThat(expectedIdentityKeys).hasSizeGreaterThan(12);
        assertThat(keys).containsAll(expectedIdentityKeys);
        assertThat(keys).noneMatch(key -> key.startsWith("title:"));
    }

    @Test
    void mixedIdentityAndTitleKeysUseOnlyRemainingTitleCapacity() {
        UUID rootId = UUID.randomUUID();
        UUID firstVictimId = UUID.randomUUID();
        UUID secondVictimId = UUID.randomUUID();
        List<String> rawKeys = new java.util.ArrayList<>(List.of(
                "provider:" + UUID.randomUUID(),
                "source:" + UUID.randomUUID()));
        for (int index = 0; index < 20; index++) {
            rawKeys.add("title:" + String.format("%02d", index));
        }
        AggregationMatchPlan plan = AggregationMatchPlan.rootWithVictims(
                rootId, List.of(firstVictimId, secondVictimId));

        List<String> keys = AggregationMatchKeys.withMatchPlan(rawKeys, plan);

        assertThat(keys).hasSize(12);
        assertThat(keys).contains(
                rawKeys.get(0), rawKeys.get(1),
                "unified:" + rootId,
                "unified:" + firstVictimId,
                "unified:" + secondVictimId);
        assertThat(keys.stream().filter(key -> key.startsWith("title:")).toList())
                .containsExactly("title:00", "title:01", "title:02", "title:03", "title:04",
                        "title:05", "title:06");
    }

    @Test
    void identityKeySetsRemainCompleteAndOrderedWhenInputsAreRearranged() {
        List<RawVideoAggregationRecord> members = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            members.add(rawVideoWithIds(
                    "Codex Signal " + index,
                    "douban-" + index,
                    "tmdb-" + index,
                    "imdb-" + index,
                    "rt-" + index));
        }
        List<UUID> unifiedIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<String> keys = AggregationMatchKeys.forCluster(
                new RawVideoAggregationCluster(members.getFirst(), members, unifiedIds));
        List<RawVideoAggregationRecord> reversedMembers = new java.util.ArrayList<>(members);
        java.util.Collections.reverse(reversedMembers);
        List<UUID> reversedUnifiedIds = new java.util.ArrayList<>(unifiedIds);
        java.util.Collections.reverse(reversedUnifiedIds);
        List<String> rearrangedKeys = AggregationMatchKeys.forCluster(
                new RawVideoAggregationCluster(reversedMembers.getFirst(), reversedMembers, reversedUnifiedIds));

        assertThat(rearrangedKeys).containsExactlyElementsOf(keys);
    }

    @Test
    void aliasSplitMatchesTheScoringStrategy() {
        // Scoring split aliases on separators while key generation digested the whole string, so a
        // pair could match on an alias yet hold no shared lock key for it.
        RawVideoAggregationRecord rawVideo = rawVideoWithAlias("Codex Signal", "Signal Alias/Other Alias");

        List<String> keys = AggregationMatchKeys.forRawVideo(rawVideo);

        assertThat(keys).hasSizeGreaterThanOrEqualTo(2);
    }

    private RawVideoAggregationRecord rawVideoWithAlias(String title, String aliasTitle) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
                title,
                aliasTitle,
                "Aggregation description",
                "2026",
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                null,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private RawVideoAggregationRecord rawVideoWithIds(
            String title, String doubanId, String tmdbId, String imdbId, String rottenTomatoesId) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                "Aggregation description",
                "2026",
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                null,
                null,
                null,
                null,
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private RawVideoAggregationRecord rawVideo(String title, String doubanId) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                "Aggregation description",
                "2026",
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                null,
                null,
                null,
                "电影",
                doubanId,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }
}
