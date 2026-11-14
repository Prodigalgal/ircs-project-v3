package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationMatchingStrategyTest {

    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();

    @Test
    void matchesTitleYearCandidateWhenExternalIdsAreAbsent() {
        UUID unifiedVideoId = UUID.randomUUID();
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Aggregation S1 1080p",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                unifiedVideoId,
                "Codex Aggregation S1",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(
                unifiedVideoId,
                strategy.findBestMatch(rawVideo, List.of(candidate)).orElseThrow());
    }

    @Test
    void externalIdConflictBlocksOtherwiseSimilarCandidate() {
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Aggregation",
                null,
                "2026",
                "111111",
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(),
                "Codex Aggregation",
                null,
                "2026",
                "222222",
                null,
                null,
                null,
                null,
                null);

        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void seasonSensitiveCandidateDoesNotMergeIncorrectly() {
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Chronicles S2",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(),
                "Codex Chronicles S1",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);

        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void versionSensitiveCandidateDoesNotMergeIncorrectly() {
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Legend 真人版",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                "真人版");
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(),
                "Codex Legend 动画版",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                "动画版");

        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void similarTitleBindsToExistingUnifiedCandidate() {
        UUID unifiedVideoId = UUID.randomUUID();
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                unifiedVideoId,
                "Codex Signal: The Movie",
                "Codex Signal",
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(
                unifiedVideoId,
                strategy.findBestMatch(rawVideo, List.of(candidate)).orElseThrow());
    }

    @Test
    void matchPlanKeepsAmbiguousCandidatesSeparateWhenOnlyTitlesLinkThem() {
        // Previously this merged victim into root purely because both resembled the raw. Resemblance
        // makes the RAW ambiguous; it is not evidence that the two existing works are the same, and
        // merging them migrates favourites/watch history before deleting the victim row.
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate root = candidate(
                rootId,
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate other = candidate(
                otherId,
                "Codex Signal: The Movie",
                "Codex Signal",
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);

        AggregationMatchPlan plan = strategy.findMatchPlan(rawVideo, List.of(other, root));

        assertFalse(plan.hasRoot());
        assertTrue(plan.isAmbiguous());
        assertEquals(List.of(), plan.victimUnifiedVideoIds());
    }

    @Test
    void matchPlanMergesCandidatesOnlyWithSharedUsableIdAndTitleCorroboration() {
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID victimId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal",
                null,
                "2026",
                "9001",
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate root = candidate(
                rootId,
                "Codex Signal",
                null,
                "2026",
                "9001",
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate victim = candidate(
                victimId,
                "Codex Signal 全集",
                null,
                "2026",
                "9001",
                null,
                null,
                null,
                null,
                null);

        AggregationMatchPlan plan = strategy.findMatchPlan(rawVideo, List.of(victim, root));

        assertEquals(rootId, plan.rootUnifiedVideoId());
        assertEquals(List.of(victimId), plan.victimUnifiedVideoIds());
    }

    @Test
    void exactTitleFallsBackToTitleConfidenceWhenMetadataIsEmptyLikeV1() {
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(),
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(0.95, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
    }

    @Test
    void metadataMismatchUsesV1WeightedFormulaAndCanBlockGraphEdges() {
        RawVideoAggregationRecord rawVideo = rawVideoWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());
        UnifiedVideoAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor B"),
                Set.of("Director B"),
                Set.of());

        double score = strategy.calculateSimilarity(rawVideo, candidate);

        assertEquals(0.70, score, 0.0001);
        assertTrue(score < 0.85);
        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void sharedActorDirectorMetadataRaisesScoreLikeV1Jaccard() {
        RawVideoAggregationRecord rawVideo = rawVideoWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A", "Actor B"),
                Set.of("Director A"),
                Set.of());
        UnifiedVideoAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());

        double score = strategy.calculateSimilarity(rawVideo, candidate);

        assertEquals(0.90, score, 0.0001);
        assertTrue(score >= 0.85);
    }

    @Test
    void episodePenaltyCanDropTitleOnlyScoreBelowGraphEdgeThreshold() {
        RawVideoAggregationRecord rawVideo = rawVideoWithMetadata(
                "Codex Signal",
                "2026",
                "1",
                Set.of(),
                Set.of(),
                Set.of());
        UnifiedVideoAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of());

        double score = strategy.calculateSimilarity(rawVideo, candidate);

        assertEquals(0.80, score, 0.0001);
        assertTrue(score < 0.85);
    }

    @Test
    void areaPenaltyAppliesOnlyWhenBothSidesHaveNonMatchingAreas() {
        RawVideoAggregationRecord rawVideo = rawVideoWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("日本"));
        UnifiedVideoAggregationCandidate differentArea = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("美国"));
        UnifiedVideoAggregationCandidate compatibleArea = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("日本地区"));

        assertEquals(0.85, strategy.calculateSimilarity(rawVideo, differentArea), 0.0001);
        assertEquals(0.95, strategy.calculateSimilarity(rawVideo, compatibleArea), 0.0001);
    }

    @Test
    void sharedPlaceholderExternalIdDoesNotMergeUnrelatedTitles() {
        // Production carried douban_id=123 on 317 raws across 305 different titles; the same-id
        // shortcut used to return 1.0 without ever looking at the title.
        RawVideoAggregationRecord rawVideo = rawVideo(
                "全民转职", null, "2025", "123", null, null, null, null, null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "寅娜知音", null, "2025", "123", null, null, null, null, null);

        assertEquals(0.0, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void sharedExternalIdStillMergesWhenTitlesAgree() {
        // A usable shared ID and matching title remain positive cross-source evidence.
        UUID unifiedVideoId = UUID.randomUUID();
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal", null, "2026", "4200", null, null, null, null, null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                unifiedVideoId, "Codex Signal 全集", null, "2026", "4200", null, null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
    }

    @Test
    void differentFixturesOnTheSameDateDoNotMergeDespiteNearIdenticalTitles() {
        // 25-26赛季 sport titles share a long suffix that inflates Jaro-Winkler; measured pairs such
        // as 中乙联赛 vs 中甲联赛 scored 0.89 and collapsed 1,870 raws into one unified row.
        RawVideoAggregationRecord rawVideo = rawVideo(
                "6月13日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "6月14日25-26赛季中甲联赛", null, "2026", null, null, null, null, null, "正片");

        assertEquals(0.0, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
        assertTrue(strategy.findBestMatch(rawVideo, List.of(candidate)).isEmpty());
    }

    @Test
    void sameFixtureFromDifferentSourcesStillMerges() {
        UUID unifiedVideoId = UUID.randomUUID();
        RawVideoAggregationRecord rawVideo = rawVideo(
                "7月4日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");
        UnifiedVideoAggregationCandidate candidate = candidate(
                unifiedVideoId, "7月4日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");

        assertTrue(strategy.findMatchPlan(rawVideo, List.of(candidate)).hasRoot());
    }

    @Test
    void leagueKeywordDifferenceAloneBlocksMerge() {
        // Same date, different competition: the league token is the discriminator.
        RawVideoAggregationRecord rawVideo = rawVideo(
                "7月4日25-26赛季中超联赛", null, "2026", null, null, null, null, null, "正片");
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "7月4日25-26赛季内蒙古足球超级联赛", null, "2026", null, null, null, null, null, "正片");

        assertEquals(0.0, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
    }

    @Test
    void nonEventTitlesAreUnaffectedByEventIdentityGuard() {
        UUID unifiedVideoId = UUID.randomUUID();
        RawVideoAggregationRecord rawVideo = rawVideo(
                "Codex Signal", null, "2026", null, null, null, null, null, null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                unifiedVideoId, "Codex Signal", null, "2026", null, null, null, null, null, null);

        assertEquals(0.95, strategy.calculateSimilarity(rawVideo, candidate), 0.0001);
    }

    @Test
    void danglingMembersDoNotJoinStarClusterBelowMergeThreshold() {
        // A chained pair (leader-candidate >= 0.90) used to be enough because the membership test was
        // `> 0.0`. Members must independently clear the merge bar against the new candidate.
        AggregationGraphCandidate leader = AggregationGraphCandidate.raw(rawVideo(
                "Codex Signal", null, "2026", null, null, null, null, null, null));
        AggregationGraphCandidate unrelated = AggregationGraphCandidate.raw(rawVideo(
                "Another Codex", null, "2026", null, null, null, null, null, null));

        assertFalse(strategy.isMergeable(leader, unrelated));
    }

    @Test
    void identicalMembersRemainMergeable() {
        AggregationGraphCandidate left = AggregationGraphCandidate.raw(rawVideo(
                "Codex Signal", null, "2026", null, null, null, null, null, null));
        AggregationGraphCandidate right = AggregationGraphCandidate.raw(rawVideo(
                "Codex Signal", null, "2026", null, null, null, null, null, null));

        assertTrue(strategy.isMergeable(left, right));
    }

    @Test
    void unknownSeasonDoesNotImplyFirstSeason() {
        RawVideoAggregationRecord unknown = rawVideo(
                "Codex Chronicles", null, "2026", "9001", null, null, null, null, null);
        UnifiedVideoAggregationCandidate firstSeason = candidate(
                UUID.randomUUID(), "Codex Chronicles S1", null, "2026", "9001", null, null, null, 1, null);

        assertEquals(0.0, strategy.calculateSimilarity(unknown, firstSeason));
        assertFalse(strategy.isCompatibleBinding(unknown, firstSeason));
    }

    @Test
    void crossLanguageTitlesCanUseSharedIdAndIndependentNameCorroboration() {
        RawVideoAggregationRecord raw = rawVideo(
                "流浪地球", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("Actor A"), Set.of("Director A"), Set.of());
        UnifiedVideoAggregationCandidate translated = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("Actor A"), Set.of("Director A"), Set.of());

        assertEquals(1.0, strategy.calculateSimilarity(raw, translated));
    }

    @Test
    void twoSharedProvidersCorroborateCrossLanguageTitlesButOneUnverifiedIdDoesNot() {
        RawVideoAggregationRecord raw = rawVideo(
                "流浪地球", null, "2026", "9001", "70001", null, null, null, null);
        UnifiedVideoAggregationCandidate twoProviders = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", "70001", null, null, null, null);
        UnifiedVideoAggregationCandidate onlyOne = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(raw, twoProviders));
        assertEquals(0.0, strategy.calculateSimilarity(raw, onlyOne));
    }

    @Test
    void rejectedIdDoesNotVetoIndependentUsableMatchingProvider() {
        RawVideoAggregationRecord raw = rawVideo(
                "Codex Signal", null, "2026", "123", "70001", null, null, null, null);
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", "70001", null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(raw, candidate));
    }

    @Test
    void bindingRefreshAllowsTitleChangesButRejectsKnownIdentityConflict() {
        RawVideoAggregationRecord renamed = rawVideo(
                "流浪地球", null, "2026", "9001", null, null, null, null, null);
        UnifiedVideoAggregationCandidate previous = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null);
        UnifiedVideoAggregationCandidate conflict = candidate(
                UUID.randomUUID(), "流浪地球", null, "2026", "9002", null, null, null, null, null);

        assertTrue(strategy.isCompatibleBinding(renamed, previous));
        assertFalse(strategy.isCompatibleBinding(renamed, conflict));
    }

    @Test
    void placeholderPeopleNamesDoNotCorroborateCrossLanguageIdentity() {
        RawVideoAggregationRecord raw = rawVideo(
                "流浪地球", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("未知"), Set.of("unknown"), Set.of());
        UnifiedVideoAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("未知"), Set.of("unknown"), Set.of());

        assertEquals(0.0, strategy.calculateSimilarity(raw, candidate));
    }

    @Test
    void releaseYearCompatibilityIsSharedByRawAndUnifiedMatching() {
        RawVideoAggregationRecord first = rawVideo(
                "Codex Signal", null, "2025", "9001", null, null, null, null, null);
        RawVideoAggregationRecord nextYear = rawVideo(
                "Codex Signal", null, "2026", "9001", null, null, null, null, null);
        RawVideoAggregationRecord distantYear = rawVideo(
                "Codex Signal", null, "2027", "9001", null, null, null, null, null);
        UnifiedVideoAggregationCandidate nextUnified = candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", null, null, null, null, null);
        UnifiedVideoAggregationCandidate distantUnified = candidate(
                UUID.randomUUID(), "Codex Signal", null, "2027", "9001", null, null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(first, nextYear));
        assertEquals(1.0, strategy.calculateSimilarity(first, nextUnified));
        assertEquals(0.0, strategy.calculateSimilarity(first, distantYear));
        assertEquals(0.0, strategy.calculateSimilarity(first, distantUnified));
        assertTrue(strategy.isYearCompatible("2026", null));
        assertTrue(strategy.isYearCompatible("unknown", "2026"));
        assertTrue(strategy.isYearCompatible("20260", "2026"));
    }

    @Test
    void preparedSimilaritySessionIsBoundedAndPreservesAllScoringAndHardGates() {
        AggregationGraphCandidate left = AggregationGraphCandidate.raw(rawVideo(
                "Codex Signal 1080p", "Codex Alias", "2026", "9001", null, null, null, null, null));
        AggregationGraphCandidate right = AggregationGraphCandidate.unified(candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", null, null, null, null, null));
        AggregationGraphCandidate conflicting = AggregationGraphCandidate.raw(rawVideo(
                "Codex Signal", null, "2026", "9002", null, null, null, null, null));
        var session = strategy.prepareSimilaritySession(List.of(left, right, conflicting, left));

        assertEquals(3, session.preparedCandidateCount());
        for (int repeat = 0; repeat < 10; repeat++) {
            assertEquals(strategy.calculateSimilarity(left, right), session.calculateSimilarity(left, right));
            assertEquals(strategy.calculateSimilarity(right, left), session.calculateSimilarity(right, left));
            assertEquals(0.0, session.calculateSimilarity(left, conflicting));
        }
        AggregationGraphCandidate outside = AggregationGraphCandidate.raw(rawVideo(
                "Another work", null, "2026", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> session.calculateSimilarity(left, outside));
        assertEquals(3, session.preparedCandidateCount());
    }

    private RawVideoAggregationRecord rawVideo(
            String title,
            String aliasTitle,
            String year,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            Integer season,
            String remarks) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
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
                season,
                "电影",
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private UnifiedVideoAggregationCandidate candidate(
            UUID id,
            String title,
            String aliasTitle,
            String year,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            Integer season,
            String remarks) {
        return new UnifiedVideoAggregationCandidate(
                id,
                title,
                aliasTitle,
                null,
                year,
                "12",
                "45m",
                remarks,
                "电影",
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                season);
    }

    private RawVideoAggregationRecord rawVideoWithMetadata(
            String title,
            String year,
            String totalEpisodes,
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaNames) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
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
                "电影",
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

    private UnifiedVideoAggregationCandidate candidateWithMetadata(
            UUID id,
            String title,
            String year,
            String totalEpisodes,
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaNames) {
        return new UnifiedVideoAggregationCandidate(
                id,
                title,
                null,
                null,
                year,
                totalEpisodes,
                "45m",
                null,
                "电影",
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
