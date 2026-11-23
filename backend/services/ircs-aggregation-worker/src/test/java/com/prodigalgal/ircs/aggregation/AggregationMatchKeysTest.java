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
        SourceRecordAggregationRecord left = sourceRecord("Codex Signal 1080p", null);
        SourceRecordAggregationRecord right = sourceRecord("Codex Signal", null);

        List<String> leftKeys = AggregationMatchKeys.forSourceRecord(left);
        List<String> rightKeys = AggregationMatchKeys.forSourceRecord(right);

        assertThat(leftKeys).containsAnyElementsOf(rightKeys);
        assertThat(leftKeys).allMatch(key -> key.startsWith("title:"));
    }

    @Test
    void externalIdsProduceProviderSpecificKeysBeforeTitleFallback() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord("Codex Signal", "1234567");

        List<String> keys = AggregationMatchKeys.forSourceRecord(sourceRecord);

        assertThat(keys).hasSizeGreaterThan(1);
        assertThat(keys.getFirst()).startsWith("douban:");
        assertThat(keys).anyMatch(key -> key.startsWith("title:"));
    }

    @Test
    void rejectedDouban456DoesNotProduceAnIdentityBucketKey() {
        List<String> keys = AggregationMatchKeys.forSourceRecord(sourceRecord("Source title", "456"));

        assertThat(keys).noneMatch(key -> key.startsWith("douban:"));
        assertThat(keys).anyMatch(key -> key.startsWith("title:"));
    }

    @Test
    void canonicalExternalIdVariantsShareTheSameProviderLock() {
        List<String> padded = AggregationMatchKeys.forSourceRecord(sourceRecord("Source title", "0035000001"));
        List<String> canonical = AggregationMatchKeys.forSourceRecord(sourceRecord("Another title", "35000001"));
        assertThat(padded.stream().filter(key -> key.startsWith("douban:")).toList())
                .containsExactlyElementsOf(canonical.stream().filter(key -> key.startsWith("douban:")).toList())
                .hasSize(1);
    }

    @Test
    void dateAndSeasonSpellingsShareASportsFixtureLock() {
        SourceRecordAggregationRecord shortDate = sourceRecord("11月13日25-26赛季NBA常规赛黄蜂VS太阳", null);
        SourceRecordAggregationRecord fullDate = sourceRecord("NBA常规赛:黄蜂VS太阳20261113", null);
        List<String> fullDateEventKeys = AggregationMatchKeys.forSourceRecord(fullDate).stream()
                .filter(key -> key.startsWith("event:")).toList();

        assertThat(fullDateEventKeys).hasSize(1);
        assertThat(AggregationMatchKeys.forSourceRecord(shortDate)).containsAll(fullDateEventKeys);
        assertThat(AggregationRecallKeys.titles(fullDate)).contains("nba常规赛 黄蜂vs太阳");
    }

    @Test
    void clusterKeysKeepIdentityKeysWhenTruncating() {
        // Clusters can have more than MAX_KEYS provider/unified keys. They all protect a distinct
        // identity and must remain in the lock set even though title fallback keys are capped.
        List<SourceRecordAggregationRecord> members = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            members.add(sourceRecordWithIds(
                    "Codex Signal " + index,
                    "3500000" + index,
                    "7000" + index,
                    "tt013309" + index,
                    "rt-" + index));
        }
        List<UUID> unifiedIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        SourceRecordAggregationCluster cluster = new SourceRecordAggregationCluster(members.getFirst(), members, unifiedIds);

        List<String> keys = AggregationMatchKeys.forCluster(cluster);

        List<String> expectedIdentityKeys = new java.util.ArrayList<>();
        members.forEach(member -> expectedIdentityKeys.addAll(AggregationMatchKeys.forSourceRecord(member).stream()
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
        List<SourceRecordAggregationRecord> members = new java.util.ArrayList<>();
        for (int index = 0; index < 4; index++) {
            members.add(sourceRecordWithIds(
                    "Codex Signal " + index,
                    "3500000" + index,
                    "7000" + index,
                    "tt013309" + index,
                    "rt-" + index));
        }
        List<UUID> unifiedIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<String> keys = AggregationMatchKeys.forCluster(
                new SourceRecordAggregationCluster(members.getFirst(), members, unifiedIds));
        List<SourceRecordAggregationRecord> reversedMembers = new java.util.ArrayList<>(members);
        java.util.Collections.reverse(reversedMembers);
        List<UUID> reversedUnifiedIds = new java.util.ArrayList<>(unifiedIds);
        java.util.Collections.reverse(reversedUnifiedIds);
        List<String> rearrangedKeys = AggregationMatchKeys.forCluster(
                new SourceRecordAggregationCluster(reversedMembers.getFirst(), reversedMembers, reversedUnifiedIds));

        assertThat(rearrangedKeys).containsExactlyElementsOf(keys);
    }

    @Test
    void aliasSplitMatchesTheScoringStrategy() {
        // Scoring split aliases on separators while key generation digested the whole string, so a
        // pair could match on an alias yet hold no shared lock key for it.
        SourceRecordAggregationRecord sourceRecord = sourceRecordWithAlias("Codex Signal", "Signal Alias/Other Alias");

        List<String> keys = AggregationMatchKeys.forSourceRecord(sourceRecord);

        assertThat(keys).hasSizeGreaterThanOrEqualTo(2);
    }

    private SourceRecordAggregationRecord sourceRecordWithAlias(String title, String aliasTitle) {
        return new SourceRecordAggregationRecord(
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

    private SourceRecordAggregationRecord sourceRecordWithIds(
            String title, String doubanId, String tmdbId, String imdbId, String rottenTomatoesId) {
        return new SourceRecordAggregationRecord(
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

    private SourceRecordAggregationRecord sourceRecord(String title, String doubanId) {
        return new SourceRecordAggregationRecord(
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
