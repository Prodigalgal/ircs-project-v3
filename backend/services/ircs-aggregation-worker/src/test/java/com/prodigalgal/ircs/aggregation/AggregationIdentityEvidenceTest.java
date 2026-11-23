package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregationIdentityEvidenceTest {

    private static final String SPORTS_HEADING = "6月28日25-26赛季新疆足球超级联赛";
    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();

    @Test
    void differentParticipantsCannotMergeThroughSharedSportsHeadingOrAnExistingBinding() {
        SourceRecordAggregationRecord left = raw(SPORTS_HEADING, "巴州队VS和田队");
        SourceRecordAggregationRecord otherFixture = raw(SPORTS_HEADING, "伊犁队VS喀什队");
        CatalogEntryAggregationCandidate unified = unified(otherFixture);

        assertEquals(0.0, strategy.calculateSimilarity(left, otherFixture));
        assertEquals(0.0, strategy.calculateSimilarity(left, unified));
        assertTrue(strategy.findBestMatch(left, List.of(unified)).isEmpty());
        assertFalse(strategy.isCompatibleBinding(left, unified));
        assertEquals("EVENT_CONFLICT", strategy.bindingConflictReason(left, unified));

        SourceRecordAggregationRecord dirtyIdLeft = raw(SPORTS_HEADING, "巴州队VS和田队", null,
                Set.of(), Set.of(), "111111");
        SourceRecordAggregationRecord dirtyIdRight = raw(SPORTS_HEADING, "伊犁队VS喀什队", null,
                Set.of(), Set.of(), "111111");
        assertEquals(0.0, strategy.calculateSimilarity(dirtyIdLeft, unified(dirtyIdRight)));
    }

    @Test
    void matchingDateLeagueAndParticipantsCanMatchAcrossStructuredAndCompleteTitles() {
        SourceRecordAggregationRecord structured = raw(SPORTS_HEADING, "巴州队VS和田队");
        SourceRecordAggregationRecord complete = raw(SPORTS_HEADING + " 巴州队 VS 和田队", null);
        CatalogEntryAggregationCandidate unified = unified(complete);

        assertTrue(strategy.calculateSimilarity(structured, complete) >= 0.80);
        assertEquals(unified.id(), strategy.findBestMatch(structured, List.of(unified)).orElseThrow());
        assertTrue(strategy.isCompatibleBinding(structured, unified));
    }

    @Test
    void differentDistinctiveShortDramaSubtitlesCannotWinOnTheirSharedHeadingAlone() {
        SourceRecordAggregationRecord left = raw("被家族抛弃", "我觉醒九亿属性点");
        SourceRecordAggregationRecord right = raw("被家族抛弃", "她远赴海外寻找真爱");

        assertTrue(strategy.calculateSimilarity(left, right) < 0.80);
        assertTrue(strategy.calculateSimilarity(left, unified(right)) < 0.80);
        assertTrue(strategy.findBestMatch(left, List.of(unified(right))).isEmpty());
    }

    @Test
    void identicalCompleteDramaIdentityMatchesWhenOneSourceSplitsItsCommaTitle() {
        SourceRecordAggregationRecord structured = raw("被家族抛弃", "我觉醒九亿属性点");
        SourceRecordAggregationRecord complete = raw("被家族抛弃，我觉醒九亿属性点", null);

        assertTrue(strategy.calculateSimilarity(structured, complete) >= 0.80);
        assertTrue(strategy.calculateSimilarity(structured, unified(complete)) >= 0.80);
        assertTrue(strategy.calculateSimilarity(complete, unified(structured)) >= 0.80);
    }

    @Test
    void sharedExternalIdCannotUseAnActorDirectorRoleSwapToCorroborateUnrelatedTitles() {
        SourceRecordAggregationRecord left = raw("霸王别姬", null, null,
                Set.of("演员甲"), Set.of("导演乙"), "111111");
        SourceRecordAggregationRecord right = raw("Titanic", null, null,
                Set.of("导演乙"), Set.of("演员甲"), "111111");
        CatalogEntryAggregationCandidate leftUnified = unified(left);
        CatalogEntryAggregationCandidate rightUnified = unified(right);

        assertEquals(0.0, strategy.calculateSimilarity(left, right));
        assertEquals(0.0, strategy.calculateSimilarity(left, rightUnified));
        assertFalse(strategy.isSafeUnifiedMerge(List.of(leftUnified, rightUnified),
                Set.of(leftUnified.id(), rightUnified.id())));
    }

    @Test
    void sameRolePeopleCanCorroborateSharedIdDespiteDifferentLanguageTitles() {
        SourceRecordAggregationRecord chinese = raw("霸王别姬", null, null,
                Set.of("演员甲"), Set.of(), "111111");
        SourceRecordAggregationRecord translated = raw("Farewell My Concubine", null, null,
                Set.of("演员甲"), Set.of(), "111111");

        assertTrue(strategy.calculateSimilarity(chinese, translated) >= 0.80);
        assertTrue(strategy.calculateSimilarity(chinese, unified(translated)) >= 0.80);
    }

    @Test
    void actorAndDirectorAgreementAreMeasuredSeparatelyRatherThanAsOneUnion() {
        SourceRecordAggregationRecord reference = raw("独立作品名称", null, null,
                Set.of("演员甲"), Set.of("导演乙"), null);
        SourceRecordAggregationRecord actorAgrees = raw("独立作品名称", null, null,
                Set.of("演员甲"), Set.of("导演丙"), null);
        SourceRecordAggregationRecord directorAgrees = raw("独立作品名称", null, null,
                Set.of("演员丁"), Set.of("导演乙"), null);
        SourceRecordAggregationRecord roleSwap = raw("独立作品名称", null, null,
                Set.of("导演乙"), Set.of("演员甲"), null);

        assertTrue(strategy.calculateSimilarity(reference, directorAgrees)
                > strategy.calculateSimilarity(reference, roleSwap));
        assertTrue(strategy.calculateSimilarity(reference, actorAgrees)
                > strategy.calculateSimilarity(reference, roleSwap));
        assertTrue(strategy.calculateSimilarity(reference, roleSwap) < 0.80);
    }

    @Test
    void missingPeopleFieldsRemainAbsentEvidenceAndDoNotBecomeHardConflicts() {
        SourceRecordAggregationRecord reference = raw("独立作品名称", null, null,
                Set.of("演员甲"), Set.of("导演乙"), null);
        SourceRecordAggregationRecord actorOnly = raw("独立作品名称", null, null,
                Set.of("演员甲"), Set.of(), null);
        SourceRecordAggregationRecord directorOnly = raw("独立作品名称", null, null,
                Set.of(), Set.of("导演乙"), null);
        SourceRecordAggregationRecord noPeople = raw("独立作品名称", null);

        for (SourceRecordAggregationRecord incomplete : List.of(actorOnly, directorOnly, noPeople)) {
            assertTrue(strategy.calculateSimilarity(reference, incomplete) >= 0.80);
            assertTrue(strategy.calculateSimilarity(reference, unified(incomplete)) >= 0.80);
            assertTrue(strategy.isCompatibleBinding(reference, unified(incomplete)));
        }
    }

    @Test
    void recallPrioritizesCompleteIdentityAndKeepsAliasFanoutBounded() {
        SourceRecordAggregationRecord raw = raw("被家族抛弃", "我觉醒九亿属性点",
                "黎明之城/远山来信/海边归途/冬日恋歌/雨夜追踪/长街往事/明日之约/夏日旅程",
                Set.of(), Set.of(), null);
        List<String> keys = AggregationRecallKeys.titles(raw);

        assertEquals("被家族抛弃 我觉醒九亿属性点", keys.getFirst());
        assertTrue(keys.contains("被家族抛弃"));
        assertTrue(keys.contains("黎明之城"));
        assertTrue(keys.size() <= 6);
        assertEquals(keys.size(), keys.stream().map(AggregationTitleNormalizer::normalizeTitle).distinct().count());
    }

    @Test
    void genericSubtitleAndAliasesCannotCreateIdentityRecallKeys() {
        SourceRecordAggregationRecord raw = raw("真实作品名称", "正片", "正片/特别篇/完整版/预告片",
                Set.of(), Set.of(), null);

        assertEquals("真实作品名称", AggregationRecallKeys.completeTitle(raw.title(), raw.subtitle()));
        assertEquals(List.of("真实作品名称"), AggregationRecallKeys.titles(raw));
        assertEquals("真实作品名称 副标题", AggregationRecallKeys.completeTitle("真实作品名称 副标题", "副标题"));
    }

    @Test
    void unknownPeopleAreFilteredBeforeApplyingTheRecallLimit() {
        assertEquals(List.of("演员甲"), AggregationRecallKeys.people(
                Set.of(" UNKNOWN ", "未知", "不详", "null", "N/A", "-", "0", "暂无", "演员甲")));
        List<String> bounded = AggregationRecallKeys.people(Set.of(
                "演员甲", "演员乙", "演员丙", "演员丁", "演员戊", "演员己", "演员庚", "演员辛", "演员壬", "未知"));
        assertEquals(8, bounded.size());
        assertFalse(bounded.contains("未知"));
        assertTrue(AggregationRecallKeys.people(null).isEmpty());
    }

    private SourceRecordAggregationRecord raw(String title, String subtitle) {
        return raw(title, subtitle, null, Set.of(), Set.of(), null);
    }

    private SourceRecordAggregationRecord raw(String title, String subtitle, String aliases,
            Set<String> actors, Set<String> directors, String doubanId) {
        return new SourceRecordAggregationRecord(UUID.randomUUID(), title, aliases, null, "2026",
                null, null, null, null, null, subtitle, null, null, null,
                actors, directors, Set.of(), doubanId, null, null, null,
                "READY", "SUCCESS", "PENDING");
    }

    private CatalogEntryAggregationCandidate unified(SourceRecordAggregationRecord raw) {
        return new CatalogEntryAggregationCandidate(UUID.randomUUID(), raw.title(), raw.aliasTitle(),
                raw.subtitle(), raw.year(), raw.totalEpisodes(), raw.duration(), raw.remarks(),
                raw.categoryName(), raw.actorNames(), raw.directorNames(), raw.areaNames(),
                raw.doubanId(), raw.tmdbId(), raw.imdbId(), raw.rottenTomatoesId(), raw.season());
    }
}
