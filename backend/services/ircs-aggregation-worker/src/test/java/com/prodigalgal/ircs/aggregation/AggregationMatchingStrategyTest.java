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
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Aggregation S1 1080p",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate candidate = candidate(
                catalogEntryId,
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
                catalogEntryId,
                strategy.findBestMatch(sourceRecord, List.of(candidate)).orElseThrow());
    }

    @Test
    void externalIdConflictBlocksOtherwiseSimilarCandidate() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Aggregation",
                null,
                "2026",
                "111111",
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate candidate = candidate(
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

        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void seasonSensitiveCandidateDoesNotMergeIncorrectly() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Chronicles S2",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate candidate = candidate(
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

        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void versionSensitiveCandidateDoesNotMergeIncorrectly() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Legend 真人版",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                "真人版");
        CatalogEntryAggregationCandidate candidate = candidate(
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

        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void similarTitleBindsToExistingUnifiedCandidate() {
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate candidate = candidate(
                catalogEntryId,
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
                catalogEntryId,
                strategy.findBestMatch(sourceRecord, List.of(candidate)).orElseThrow());
    }

    @Test
    void matchPlanKeepsAmbiguousCandidatesSeparateWhenOnlyTitlesLinkThem() {
        // Previously this merged victim into root purely because both resembled the raw. Resemblance
        // makes the RAW ambiguous; it is not evidence that the two existing works are the same, and
        // merging them migrates favourites/watch history before deleting the victim row.
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate root = candidate(
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
        CatalogEntryAggregationCandidate other = candidate(
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

        AggregationMatchPlan plan = strategy.findMatchPlan(sourceRecord, List.of(other, root));

        assertFalse(plan.hasRoot());
        assertTrue(plan.isAmbiguous());
        assertEquals(List.of(), plan.victimCatalogEntryIds());
    }

    @Test
    void matchPlanMergesCandidatesOnlyWithSharedUsableIdAndTitleCorroboration() {
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID victimId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal",
                null,
                "2026",
                "9001",
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate root = candidate(
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
        CatalogEntryAggregationCandidate victim = candidate(
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

        AggregationMatchPlan plan = strategy.findMatchPlan(sourceRecord, List.of(victim, root));

        assertEquals(rootId, plan.rootCatalogEntryId());
        assertEquals(List.of(victimId), plan.victimCatalogEntryIds());
    }

    @Test
    void equivalentAuthorityBackedCandidatesResolveWithoutMergingAnUncorroboratedTitleMatch() {
        SourceRecordAggregationRecord raw = sourceRecord("Codex Signal", null, "2026",
                null, "9001", null, null, null, null);
        UUID rootId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID alternativeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var root = candidate(rootId, "Codex Signal", null, "2026", null, "9001", "tt0133093", null, null, null);
        var alternative = candidate(alternativeId, "Codex Signal", null, "2026", null, "9001", null, null, null, null);
        var titleMatch = candidate(UUID.randomUUID(), "Codex Signal", null, "2026",
                null, null, null, null, null, null);

        var plan = strategy.findMatchPlan(raw, List.of(alternative, titleMatch, root));

        assertEquals(rootId, plan.rootCatalogEntryId());
        assertEquals(List.of(), plan.victimCatalogEntryIds());

        var conflictingAlternative = candidate(alternativeId, "Codex Signal", null, "2026",
                null, "9001", "tt0234215", null, null, null);
        assertTrue(strategy.findMatchPlan(raw, List.of(root, conflictingAlternative, titleMatch)).isAmbiguous());
    }

    @Test
    void exactReleaseAndTwoSharedCastMembersCanDisambiguateWithoutAnExternalId() {
        String title = "Codex Signal";
        Set<String> cast = Set.of("Alice Winters", "Noah Stone");
        var raw = sourceRecordWithMetadata(title, "2026", "12", cast, Set.of(), Set.of());
        var corroborated = candidateWithMetadata(UUID.randomUUID(), title, "2026", "12", cast, Set.of(), Set.of());
        var sparse = candidateWithMetadata(UUID.randomUUID(), title, "2026", "12", Set.of(), Set.of(), Set.of());
        var previousYear = candidateWithMetadata(UUID.randomUUID(), title, "2025", "12", cast, Set.of(), Set.of());

        var plan = strategy.findMatchPlan(raw, List.of(sparse, previousYear, corroborated));

        assertEquals(corroborated.id(), plan.rootCatalogEntryId());
        assertEquals(List.of(), plan.victimCatalogEntryIds());
        var sameEvidence = candidateWithMetadata(UUID.randomUUID(), title, "2026", "12", cast, Set.of(), Set.of());
        assertTrue(strategy.findMatchPlan(raw, List.of(corroborated, sameEvidence)).isAmbiguous());
    }

    @Test
    void exactTitleFallsBackToTitleConfidenceWhenMetadataIsEmptyLikeV1() {
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal",
                null,
                "2026",
                null,
                null,
                null,
                null,
                null,
                null);
        CatalogEntryAggregationCandidate candidate = candidate(
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

        assertEquals(0.95, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
    }

    @Test
    void metadataMismatchUsesV1WeightedFormulaAndCanBlockGraphEdges() {
        SourceRecordAggregationRecord sourceRecord = sourceRecordWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());
        CatalogEntryAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor B"),
                Set.of("Director B"),
                Set.of());

        double score = strategy.calculateSimilarity(sourceRecord, candidate);

        assertEquals(0.70, score, 0.0001);
        assertTrue(score < 0.85);
        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void sharedDirectorAndPartialCastContributeSeparateRoleEvidence() {
        SourceRecordAggregationRecord sourceRecord = sourceRecordWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A", "Actor B"),
                Set.of("Director A"),
                Set.of());
        CatalogEntryAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of("Actor A"),
                Set.of("Director A"),
                Set.of());

        double score = strategy.calculateSimilarity(sourceRecord, candidate);

        assertEquals(0.91, score, 0.0001);
        assertTrue(score >= 0.85);
    }

    @Test
    void episodePenaltyCanDropTitleOnlyScoreBelowGraphEdgeThreshold() {
        SourceRecordAggregationRecord sourceRecord = sourceRecordWithMetadata(
                "Codex Signal",
                "2026",
                "1",
                Set.of(),
                Set.of(),
                Set.of());
        CatalogEntryAggregationCandidate candidate = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of());

        double score = strategy.calculateSimilarity(sourceRecord, candidate);

        assertEquals(0.80, score, 0.0001);
        assertTrue(score < 0.85);
    }

    @Test
    void areaPenaltyAppliesOnlyWhenBothSidesHaveNonMatchingAreas() {
        SourceRecordAggregationRecord sourceRecord = sourceRecordWithMetadata(
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("日本"));
        CatalogEntryAggregationCandidate differentArea = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("美国"));
        CatalogEntryAggregationCandidate compatibleArea = candidateWithMetadata(
                UUID.randomUUID(),
                "Codex Signal",
                "2026",
                "12",
                Set.of(),
                Set.of(),
                Set.of("日本地区"));

        assertEquals(0.85, strategy.calculateSimilarity(sourceRecord, differentArea), 0.0001);
        assertEquals(0.95, strategy.calculateSimilarity(sourceRecord, compatibleArea), 0.0001);
    }

    @Test
    void sharedPlaceholderExternalIdDoesNotMergeUnrelatedTitles() {
        // Production carried douban_id=123 on 317 raws across 305 different titles; the same-id
        // shortcut used to return 1.0 without ever looking at the title.
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "全民转职", null, "2025", "123", null, null, null, null, null);
        CatalogEntryAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "寅娜知音", null, "2025", "123", null, null, null, null, null);

        assertEquals(0.0, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void sharedExternalIdStillMergesWhenTitlesAgree() {
        // A usable shared ID and matching title remain positive cross-source evidence.
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal", null, "2026", "4200", null, null, null, null, null);
        CatalogEntryAggregationCandidate candidate = candidate(
                catalogEntryId, "Codex Signal 全集", null, "2026", "4200", null, null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
    }

    @Test
    void differentFixturesOnTheSameDateDoNotMergeDespiteNearIdenticalTitles() {
        // 25-26赛季 sport titles share a long suffix that inflates Jaro-Winkler; measured pairs such
        // as 中乙联赛 vs 中甲联赛 scored 0.89 and collapsed 1,870 raws into one unified row.
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "6月13日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");
        CatalogEntryAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "6月14日25-26赛季中甲联赛", null, "2026", null, null, null, null, null, "正片");

        assertEquals(0.0, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
        assertTrue(strategy.findBestMatch(sourceRecord, List.of(candidate)).isEmpty());
    }

    @Test
    void sameFixtureFromDifferentSourcesStillMerges() {
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "7月4日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");
        CatalogEntryAggregationCandidate candidate = candidate(
                catalogEntryId, "7月4日25-26赛季中乙联赛", null, "2026", null, null, null, null, null, "正片");

        assertTrue(strategy.findMatchPlan(sourceRecord, List.of(candidate)).hasRoot());
    }

    @Test
    void leagueKeywordDifferenceAloneBlocksMerge() {
        // Same date, different competition: the league token is the discriminator.
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "7月4日25-26赛季中超联赛", null, "2026", null, null, null, null, null, "正片");
        CatalogEntryAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "7月4日25-26赛季内蒙古足球超级联赛", null, "2026", null, null, null, null, null, "正片");

        assertEquals(0.0, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
    }

    @Test
    void nonEventTitlesAreUnaffectedByEventIdentityGuard() {
        UUID catalogEntryId = UUID.randomUUID();
        SourceRecordAggregationRecord sourceRecord = sourceRecord(
                "Codex Signal", null, "2026", null, null, null, null, null, null);
        CatalogEntryAggregationCandidate candidate = candidate(
                catalogEntryId, "Codex Signal", null, "2026", null, null, null, null, null, null);

        assertEquals(0.95, strategy.calculateSimilarity(sourceRecord, candidate), 0.0001);
    }

    @Test
    void danglingMembersDoNotJoinStarClusterBelowMergeThreshold() {
        // A chained pair (leader-candidate >= 0.90) used to be enough because the membership test was
        // `> 0.0`. Members must independently clear the merge bar against the new candidate.
        AggregationGraphCandidate leader = AggregationGraphCandidate.raw(sourceRecord(
                "Codex Signal", null, "2026", null, null, null, null, null, null));
        AggregationGraphCandidate unrelated = AggregationGraphCandidate.raw(sourceRecord(
                "Another Codex", null, "2026", null, null, null, null, null, null));

        assertFalse(strategy.isMergeable(leader, unrelated));
    }

    @Test
    void identicalMembersRemainMergeable() {
        AggregationGraphCandidate left = AggregationGraphCandidate.raw(sourceRecord(
                "Codex Signal", null, "2026", null, null, null, null, null, null));
        AggregationGraphCandidate right = AggregationGraphCandidate.raw(sourceRecord(
                "Codex Signal", null, "2026", null, null, null, null, null, null));

        assertTrue(strategy.isMergeable(left, right));
    }

    @Test
    void unknownSeasonDoesNotImplyFirstSeason() {
        SourceRecordAggregationRecord unknown = sourceRecord(
                "Codex Chronicles", null, "2026", "9001", null, null, null, null, null, "剧集");
        CatalogEntryAggregationCandidate firstSeason = candidate(
                UUID.randomUUID(), "Codex Chronicles S1", null, "2026", "9001", null, null, null, 1, null, "剧集");

        assertEquals(0.0, strategy.calculateSimilarity(unknown, firstSeason));
        assertTrue(strategy.isCompatibleBinding(unknown, firstSeason));
        SourceRecordAggregationRecord secondSeason = sourceRecord(
                "Codex Chronicles S2", null, "2026", "9001", null, null, null, 2, null, "剧集");
        assertFalse(strategy.isCompatibleBinding(secondSeason, firstSeason));
        assertEquals("SEASON_CONFLICT", strategy.bindingConflictReason(secondSeason, firstSeason));
    }

    @Test
    void crossLanguageTitlesCanUseSharedIdAndIndependentNameCorroboration() {
        SourceRecordAggregationRecord raw = sourceRecord(
                "流浪地球", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("Actor A"), Set.of("Director A"), Set.of());
        CatalogEntryAggregationCandidate translated = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("Actor A"), Set.of("Director A"), Set.of());

        assertEquals(1.0, strategy.calculateSimilarity(raw, translated));
    }

    @Test
    void sharedProviderIdsNeedCorroborationForCrossLanguageTitles() {
        SourceRecordAggregationRecord raw = sourceRecord(
                "流浪地球", null, "2026", "9001", "70001", null, null, null, null);
        CatalogEntryAggregationCandidate twoProviders = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", "70001", null, null, null, null);
        CatalogEntryAggregationCandidate onlyOne = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null);
        CatalogEntryAggregationCandidate translatedAlias = candidate(
                UUID.randomUUID(), "The Wandering Earth", "流浪地球", "2026", "9001", "70001", null, null, null, null);

        assertEquals(0.0, strategy.calculateSimilarity(raw, twoProviders));
        assertEquals(0.0, strategy.calculateSimilarity(raw, onlyOne));
        assertEquals("SHARED_IDS_WITHOUT_CORROBORATION",
                strategy.findMatchPlan(raw, List.of(twoProviders)).resolutionReason());
        assertEquals(1.0, strategy.calculateSimilarity(raw, translatedAlias));
    }

    @Test
    void rejectedIdDoesNotVetoIndependentUsableMatchingProvider() {
        SourceRecordAggregationRecord raw = sourceRecord(
                "Codex Signal", null, "2026", "123", "70001", null, null, null, null);
        CatalogEntryAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", "70001", null, null, null, null);

        assertEquals(1.0, strategy.calculateSimilarity(raw, candidate));
    }

    @Test
    void bindingRefreshAllowsTitleChangesButRejectsKnownIdentityConflict() {
        SourceRecordAggregationRecord renamed = sourceRecord(
                "流浪地球", null, "2026", "9001", null, null, null, null, null);
        CatalogEntryAggregationCandidate previous = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null);
        CatalogEntryAggregationCandidate conflict = candidate(
                UUID.randomUUID(), "流浪地球", null, "2026", "9002", null, null, null, null, null);

        assertTrue(strategy.isCompatibleBinding(renamed, previous));
        assertFalse(strategy.isCompatibleBinding(renamed, conflict));
    }

    @Test
    void placeholderPeopleNamesDoNotCorroborateCrossLanguageIdentity() {
        SourceRecordAggregationRecord raw = sourceRecord(
                "流浪地球", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("未知"), Set.of("unknown"), Set.of());
        CatalogEntryAggregationCandidate candidate = candidate(
                UUID.randomUUID(), "The Wandering Earth", null, "2026", "9001", null, null, null, null, null)
                .withMetadata(Set.of("未知"), Set.of("unknown"), Set.of());

        assertEquals(0.0, strategy.calculateSimilarity(raw, candidate));
    }

    @Test
    void releaseYearCompatibilityIsSharedByRawAndUnifiedMatching() {
        SourceRecordAggregationRecord first = sourceRecord(
                "Codex Signal", null, "2025", "9001", null, null, null, null, null);
        SourceRecordAggregationRecord nextYear = sourceRecord(
                "Codex Signal", null, "2026", "9001", null, null, null, null, null);
        SourceRecordAggregationRecord distantYear = sourceRecord(
                "Codex Signal", null, "2027", "9001", null, null, null, null, null);
        CatalogEntryAggregationCandidate nextUnified = candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", null, null, null, null, null);
        CatalogEntryAggregationCandidate distantUnified = candidate(
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
        AggregationGraphCandidate left = AggregationGraphCandidate.raw(sourceRecord(
                "Codex Signal 1080p", "Codex Alias", "2026", "9001", null, null, null, null, null));
        AggregationGraphCandidate right = AggregationGraphCandidate.unified(candidate(
                UUID.randomUUID(), "Codex Signal", null, "2026", "9001", null, null, null, null, null));
        AggregationGraphCandidate conflicting = AggregationGraphCandidate.raw(sourceRecord(
                "Codex Signal", null, "2026", "9002", null, null, null, null, null));
        var session = strategy.prepareSimilaritySession(List.of(left, right, conflicting, left));

        assertEquals(3, session.preparedCandidateCount());
        for (int repeat = 0; repeat < 10; repeat++) {
            assertEquals(strategy.calculateSimilarity(left, right), session.calculateSimilarity(left, right));
            assertEquals(strategy.calculateSimilarity(right, left), session.calculateSimilarity(right, left));
            assertEquals(0.0, session.calculateSimilarity(left, conflicting));
        }
        AggregationGraphCandidate outside = AggregationGraphCandidate.raw(sourceRecord(
                "Another work", null, "2026", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> session.calculateSimilarity(left, outside));
        assertEquals(3, session.preparedCandidateCount());
    }

    private SourceRecordAggregationRecord sourceRecord(
            String title,
            String aliasTitle,
            String year,
            String doubanId,
            String tmdbId,
            String imdbId,
            String rottenTomatoesId,
            Integer season,
            String remarks) {
        return sourceRecord(title, aliasTitle, year, doubanId, tmdbId, imdbId, rottenTomatoesId, season, remarks, "电影");
    }

    private SourceRecordAggregationRecord sourceRecord(
            String title, String aliasTitle, String year, String doubanId, String tmdbId,
            String imdbId, String rottenTomatoesId, Integer season, String remarks, String category) {
        return new SourceRecordAggregationRecord(
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
                category,
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                "READY",
                "SUCCESS",
                "PROCESSING");
    }

    private CatalogEntryAggregationCandidate candidate(
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
        return candidate(id, title, aliasTitle, year, doubanId, tmdbId, imdbId, rottenTomatoesId,
                season, remarks, "电影");
    }

    private CatalogEntryAggregationCandidate candidate(
            UUID id, String title, String aliasTitle, String year, String doubanId, String tmdbId,
            String imdbId, String rottenTomatoesId, Integer season, String remarks, String category) {
        return new CatalogEntryAggregationCandidate(
                id,
                title,
                aliasTitle,
                null,
                year,
                "12",
                "45m",
                remarks,
                category,
                doubanId,
                tmdbId,
                imdbId,
                rottenTomatoesId,
                season);
    }

    private SourceRecordAggregationRecord sourceRecordWithMetadata(
            String title,
            String year,
            String totalEpisodes,
            Set<String> actorNames,
            Set<String> directorNames,
            Set<String> areaNames) {
        return new SourceRecordAggregationRecord(
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

    private CatalogEntryAggregationCandidate candidateWithMetadata(
            UUID id,
            String title,
            String year,
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
