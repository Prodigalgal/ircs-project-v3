package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRecordGraphClustererTest {

    private final SourceRecordGraphClusterer clusterer = new SourceRecordGraphClusterer(AggregationTestFixtures.matchingStrategy());

    @Test
    void clustersSameBatchSourceRecordsThroughJgraphtConnectedComponents() {
        SourceRecordAggregationRecord first = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord second = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord third = sourceRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Codex Signal: The Movie",
                "Codex Signal",
                "2026",
                "电影",
                null,
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(first, second, third));

        assertEquals(1, clusters.size());
        assertEquals(List.of(third.id(), first.id(), second.id()), clusters.getFirst().sourceRecordIds());
    }

    @Test
    void toleratesOneYearReleaseDifferenceAndUnknownYearWithoutInventingConflict() {
        SourceRecordAggregationRecord year2025 = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2025",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord year2026 = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord unknownYear = sourceRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Codex Signal",
                null,
                null,
                "电影",
                null,
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(year2025, year2026, unknownYear));

        assertEquals(1, clusters.size());
        assertEquals(List.of(year2026.id(), year2025.id(), unknownYear.id()), clusters.getFirst().sourceRecordIds());
    }

    @Test
    void keepsSeasonVersionAndExternalIdConflictsOutOfRawClusters() {
        SourceRecordAggregationRecord seasonOne = sourceRecord(
                UUID.randomUUID(),
                "Codex Chronicles S1",
                null,
                "2026",
                "电视剧",
                "111",
                null);
        SourceRecordAggregationRecord seasonTwo = sourceRecord(
                UUID.randomUUID(),
                "Codex Chronicles S2",
                null,
                "2026",
                "电视剧",
                "111",
                null);
        SourceRecordAggregationRecord realVersion = sourceRecord(
                UUID.randomUUID(),
                "Codex Legend 真人版",
                null,
                "2026",
                "电影",
                null,
                "真人版");
        SourceRecordAggregationRecord animeVersion = sourceRecord(
                UUID.randomUUID(),
                "Codex Legend 动画版",
                null,
                "2026",
                "电影",
                null,
                "动画版");
        SourceRecordAggregationRecord idConflict = sourceRecord(
                UUID.randomUUID(),
                "Codex Signal",
                null,
                "2026",
                "电影",
                "222",
                null);
        SourceRecordAggregationRecord sameTitleDifferentId = sourceRecord(
                UUID.randomUUID(),
                "Codex Signal",
                null,
                "2026",
                "电影",
                "333",
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(
                seasonOne,
                seasonTwo,
                realVersion,
                animeVersion,
                idConflict,
                sameTitleDifferentId));

        assertEquals(6, clusters.size());
        assertEquals(6, clusters.stream().filter(cluster -> !cluster.isMultiRawCluster()).count());
    }

    @Test
    void mixedGraphUsesUnifiedContextAsBridgeAndSkipsPureUnifiedComponents() {
        SourceRecordAggregationRecord first = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord second = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电影",
                null,
                null);
        CatalogEntryAggregationCandidate context = unified(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null);
        CatalogEntryAggregationCandidate unrelatedPureContext = unified(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                "Another Signal",
                null,
                "2026",
                "电影",
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(
                List.of(first, second),
                List.of(context, unrelatedPureContext));

        assertEquals(1, clusters.size());
        assertEquals(List.of(first.id(), second.id()), clusters.getFirst().sourceRecordIds());
        assertEquals(List.of(context.id()), clusters.getFirst().contextCatalogEntryIds());
    }

    @Test
    void mixedGraphDoesNotUseConflictingUnifiedContextAsBridge() {
        SourceRecordAggregationRecord raw = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                "111",
                null);
        CatalogEntryAggregationCandidate conflict = unified(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                "222");

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(raw), List.of(conflict));

        assertEquals(1, clusters.size());
        assertEquals(List.of(raw.id()), clusters.getFirst().sourceRecordIds());
        assertEquals(List.of(), clusters.getFirst().contextCatalogEntryIds());
    }

    @Test
    void starPeelingSplitsMembersBelowLeaderConsistencyThreshold() {
        SourceRecordAggregationRecord leader = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord strong = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord weakButConnected = sourceRecord(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "Codex Signal The Remote Cut",
                null,
                "2026",
                "电影",
                null,
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(leader, strong, weakButConnected));

        assertEquals(2, clusters.size());
        assertEquals(List.of(strong.id(), leader.id()), clusters.get(0).sourceRecordIds());
        assertEquals(List.of(weakButConnected.id()), clusters.get(1).sourceRecordIds());
    }

    @Test
    void categoryBucketPreventsCrossCategoryRawEdgesLikeV1() {
        SourceRecordAggregationRecord movie = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null,
                null);
        SourceRecordAggregationRecord series = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电视剧",
                null,
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(movie, series));

        assertEquals(2, clusters.size());
        assertEquals(List.of(movie.id()), clusters.get(0).sourceRecordIds());
        assertEquals(List.of(series.id()), clusters.get(1).sourceRecordIds());
    }

    @Test
    void categoryBucketPreventsCrossCategoryUnifiedContextBridge() {
        SourceRecordAggregationRecord raw = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                "电影",
                null,
                null);
        CatalogEntryAggregationCandidate context = unified(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Codex Signal",
                null,
                "2026",
                "电视剧",
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(raw), List.of(context));

        assertEquals(1, clusters.size());
        assertEquals(List.of(raw.id()), clusters.getFirst().sourceRecordIds());
        assertEquals(List.of(), clusters.getFirst().contextCatalogEntryIds());
    }

    @Test
    void unknownCategoryBridgesToConcreteCategoryBecauseMissingClassificationIsNotAConflict() {
        // A missing category means "not classified yet", not "a different work". Treating it as its own
        // bucket kept unclassified rows permanently isolated from their real work, so the raw row and
        // the unified context now merge on title alone.
        SourceRecordAggregationRecord unknown = sourceRecord(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null);
        SourceRecordAggregationRecord concrete = sourceRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal 1080p",
                null,
                "2026",
                "电影",
                null,
                null);
        CatalogEntryAggregationCandidate unknownContext = unified(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Codex Signal",
                null,
                "2026",
                null,
                null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(
                List.of(unknown, concrete),
                List.of(unknownContext));

        assertEquals(1, clusters.size());
        assertEquals(List.of(unknown.id(), concrete.id()), clusters.getFirst().sourceRecordIds());
        assertEquals(List.of(unknownContext.id()), clusters.getFirst().contextCatalogEntryIds());
    }

    @Test
    void actorDirectorMismatchPreventsSameTitleRawGraphEdgeLikeV1Scoring() {
        SourceRecordAggregationRecord first = sourceRecordWithMetadata(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                "2026",
                "电影",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());
        SourceRecordAggregationRecord second = sourceRecordWithMetadata(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal",
                "2026",
                "电影",
                "12",
                Set.of("Actor B"),
                Set.of("Director B"),
                Set.of());

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(first, second));

        assertEquals(2, clusters.size());
        assertEquals(List.of(first.id()), clusters.get(0).sourceRecordIds());
        assertEquals(List.of(second.id()), clusters.get(1).sourceRecordIds());
    }

    @Test
    void rawUnifiedContextBridgeRequiresV1MetadataScore() {
        SourceRecordAggregationRecord raw = sourceRecordWithMetadata(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                "2026",
                "电影",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());
        CatalogEntryAggregationCandidate context = unifiedWithMetadata(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Codex Signal",
                "2026",
                "电影",
                "12",
                Set.of("Actor B"),
                Set.of("Director B"),
                Set.of());

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(raw), List.of(context));

        assertEquals(1, clusters.size());
        assertEquals(List.of(raw.id()), clusters.getFirst().sourceRecordIds());
        assertEquals(List.of(), clusters.getFirst().contextCatalogEntryIds());
    }

    @Test
    void episodePenaltyPreventsSameTitleRawGraphEdgeLikeV1Scoring() {
        SourceRecordAggregationRecord oneEpisode = sourceRecordWithMetadata(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Codex Signal",
                "2026",
                "电影",
                "1",
                Set.of(),
                Set.of(),
                Set.of());
        SourceRecordAggregationRecord twelveEpisodes = sourceRecordWithMetadata(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Codex Signal",
                "2026",
                "电影",
                "12",
                Set.of(),
                Set.of(),
                Set.of());

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(oneEpisode, twelveEpisodes));

        assertEquals(2, clusters.size());
        assertEquals(List.of(oneEpisode.id()), clusters.get(0).sourceRecordIds());
        assertEquals(List.of(twelveEpisodes.id()), clusters.get(1).sourceRecordIds());
    }

    @Test
    void everyMemberPairMustMeetConsistencyThresholdEvenThroughMetadataPoorLeader() {
        SourceRecordAggregationRecord leader = sourceRecordWithMetadata(
                UUID.randomUUID(), "Codex Signal 1080p", "2026", "电影", "12",
                Set.of(), Set.of(), Set.of());
        SourceRecordAggregationRecord first = sourceRecordWithMetadata(
                UUID.randomUUID(), "Codex Signal", "2026", "电影", "12",
                Set.of("Actor A", "Actor B", "Actor C"), Set.of(), Set.of());
        SourceRecordAggregationRecord second = sourceRecordWithMetadata(
                UUID.randomUUID(), "Codex Signal", "2026", "电影", "12",
                Set.of("Actor A", "Actor B", "Actor D"), Set.of(), Set.of());

        // Leader agrees with each at .95; first/second agree only at .85.
        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(leader, first, second));

        assertEquals(2, clusters.size());
        assertEquals(2, clusters.stream().mapToInt(cluster -> cluster.members().size()).max().orElseThrow());
    }

    @Test
    void unknownCategoryCannotBridgeKnownConflictingCategories() {
        SourceRecordAggregationRecord unknown = sourceRecord(UUID.randomUUID(), "Codex Signal 1080p", null, "2026", null, null, null);
        SourceRecordAggregationRecord movie = sourceRecord(UUID.randomUUID(), "Codex Signal", null, "2026", "电影", null, null);
        SourceRecordAggregationRecord series = sourceRecord(UUID.randomUUID(), "Codex Signal", null, "2026", "电视剧", null, null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(unknown, movie, series));

        assertEquals(2, clusters.size());
        assertEquals(2, clusters.stream().mapToInt(cluster -> cluster.members().size()).max().orElseThrow());
    }

    @Test
    void unknownYearCannotBridgeKnownYearsOutsideReleaseTolerance() {
        SourceRecordAggregationRecord unknown = sourceRecord(UUID.randomUUID(), "Codex Signal 1080p", null, null, "电影", null, null);
        SourceRecordAggregationRecord older = sourceRecord(UUID.randomUUID(), "Codex Signal", null, "2025", "电影", null, null);
        SourceRecordAggregationRecord newer = sourceRecord(UUID.randomUUID(), "Codex Signal", null, "2027", "电影", null, null);

        List<SourceRecordAggregationCluster> clusters = clusterer.cluster(List.of(unknown, older, newer));

        assertEquals(2, clusters.size());
        assertEquals(2, clusters.stream().mapToInt(cluster -> cluster.members().size()).max().orElseThrow());
    }

    private SourceRecordAggregationRecord sourceRecord(
            UUID id,
            String title,
            String aliasTitle,
            String year,
            String categoryName,
            String doubanId,
            String remarks) {
        return new SourceRecordAggregationRecord(
                id,
                title,
                aliasTitle,
                "Aggregation description",
                year,
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "12",
                "45m",
                remarks,
                null,
                null,
                categoryName,
                doubanId,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private CatalogEntryAggregationCandidate unified(
            UUID id,
            String title,
            String aliasTitle,
            String year,
            String categoryName,
            String doubanId) {
        return new CatalogEntryAggregationCandidate(
                id,
                title,
                aliasTitle,
                null,
                year,
                "12",
                "45m",
                null,
                categoryName,
                doubanId,
                null,
                null,
                null,
                null);
    }

    private SourceRecordAggregationRecord sourceRecordWithMetadata(
            UUID id,
            String title,
            String year,
            String categoryName,
            String totalEpisodes,
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaNames) {
        return new SourceRecordAggregationRecord(
                id,
                title,
                null,
                "Aggregation description",
                year,
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                totalEpisodes,
                "45m",
                null,
                null,
                null,
                categoryName,
                actorNames,
                directorNames,
                areaNames,
                null,
                null,
                null,
                null,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private CatalogEntryAggregationCandidate unifiedWithMetadata(
            UUID id,
            String title,
            String year,
            String categoryName,
            String totalEpisodes,
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaNames) {
        return new CatalogEntryAggregationCandidate(
                id,
                title,
                null,
                null,
                year,
                totalEpisodes,
                "45m",
                null,
                categoryName,
                actorNames,
                directorNames,
                areaNames,
                null,
                null,
                null,
                null,
                null);
    }
}
