package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Behaviour fixtures from the 2026-11-16 audit plus independent positive/boundary examples. */
class AggregationCategoryProfileTest {

    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();

    @Test
    void compactCalendarDatesAndUndatedOpponentsExposeTheWorldCupMixedCluster() {
        SourceRecordAggregationRecord reference = raw("sports", "回放)世界杯小组赛", "乌兹别克斯坦VS哥伦比亚20260618");
        for (SourceRecordAggregationRecord other : List.of(
                raw("sports", "世界杯小组赛", "奥地利VS约旦20260617"),
                raw("sports", "世界杯小组赛", "捷克VS南非20260619"),
                raw("sports", "回放)世界杯小组赛", "墨西哥VS南非"),
                raw("sports", "回放)世界杯1_8决赛", "巴拉圭VS法国"))) {
            assertDistinct(reference, other);
            assertFalse(strategy.isCompatibleBinding(other, unified(reference)));
        }
    }

    @Test
    void sexRoundAndTennisLocationConflictsAreHardConstraintsEvenWithSharedIds() {
        for (List<SourceRecordAggregationRecord> pair : List.of(
                List.of(raw("sports", "未来之星高中邀请赛", "女子组决赛 精华女子高等学校vs阳明高中20260628"),
                        raw("sports", "未来之星高中邀请赛", "男子组决赛 Kyungbock高中vs鸟取城北高中20260628")),
                List.of(raw("sports", "WTA500伦敦站女单第二轮", "约维奇2-0伊埃拉20260610"),
                        raw("sports", "WTA500柏林站女单第二轮", "约维奇2-0伊埃拉20260610")),
                List.of(raw("sports", "WTA250诺丁汉站女单第二轮", "吉布森VS郑钦文20260618"),
                        raw("sports", "WTA250诺丁汉站女单第二轮", "布兹科娃2-0克鲁格曼20260618")),
                List.of(raw("sports", "全国青年篮球联赛男子组决赛", "甲队VS乙队20260618"),
                        raw("sports", "全国青年篮球联赛女子组决赛", "甲队VS乙队20260618")),
                List.of(raw("sports", "WTA500伦敦站女单第一轮", "郑钦文VS高芙20260618"),
                        raw("sports", "WTA500伦敦站女单第二轮", "郑钦文VS高芙20260618")))) {
            assertDistinct(pair.getFirst(), pair.getLast());
            assertEquals("EVENT_CONFLICT", strategy.bindingConflictReason(pair.getFirst(), unified(pair.getLast())));
            assertDistinct(withEvidence(pair.getFirst(), "2026", null, Set.of(), Set.of(), "35000001"),
                    withEvidence(pair.getLast(), "2026", null, Set.of(), Set.of(), "35000001"));
        }
    }

    @Test
    void sameFixtureMatchesAcrossDateFormatsAndChangingScores() {
        SourceRecordAggregationRecord reference = raw("sports", "WTA250诺丁汉站女单第二轮", "卡 普利斯科娃VS麦克纳莉20260618");
        for (SourceRecordAggregationRecord equivalent : List.of(
                raw("sports", "6月18日WTA250诺丁汉站女单第二轮", "卡普利斯科娃2-0麦克纳莉"),
                raw("sports", "2026-06-18 WTA250诺丁汉站女单第二轮", "卡普利斯科娃 暂0-0麦克纳莉"),
                raw("sports", "2026-06-18 WTA250诺丁汉站女单第二轮", "卡普利斯科娃(2-0)麦克纳莉"),
                raw("sports", "2026-06-18 WTA250诺丁汉站女单第二轮", "卡普利斯科娃VS麦克纳莉（2-0）"),
                raw("sports", "2026年6月18日 WTA250诺丁汉站女单第二轮 卡普利斯科娃VS麦克纳莉", null))) {
            assertSameEvent(reference, equivalent);
        }
        assertSameEvent(raw("sports", "7月4日25-26赛季浙BA", "桐庐76VS74临安"),
                raw("sports", "7月4日25-26赛季浙BA 桐庐VS临安", null));
        SourceRecordAggregationRecord hornetsTitle = raw("sports", "NBA常规赛:黄蜂VS太阳20261113", null);
        SourceRecordAggregationRecord sourceSeasonNoise = raw("sports", "11月13日25-26赛季NBA常规赛黄蜂VS太阳", null);
        assertSameEvent(hornetsTitle, sourceSeasonNoise);
        assertDistinct(sourceSeasonNoise, raw("sports", "11月13日26-27赛季NBA常规赛黄蜂VS太阳", null));
        assertDistinct(hornetsTitle, raw("sports", "11月13日25-26赛季NBA常规赛黄蜂VS国王", null));
    }

    @Test
    void competitionSeasonOnlyAnchorsWhenBothSourcesAgreeAndNoYearIsAvailable() {
        SourceRecordAggregationRecord first = withEvidence(raw("sports", "11月13日25-26赛季NBA常规赛黄蜂VS太阳", null),
                null, null, Set.of(), Set.of(), null);
        SourceRecordAggregationRecord sameSeason = withEvidence(raw("sports", "11月13日25-26赛季NBA常规赛黄蜂VS太阳", null),
                null, null, Set.of(), Set.of(), null);
        SourceRecordAggregationRecord expandedSeason = withEvidence(raw("sports", "11月13日2025 - 2026 赛季NBA常规赛黄蜂VS太阳", null),
                null, null, Set.of(), Set.of(), null);
        SourceRecordAggregationRecord otherSeason = withEvidence(raw("sports", "11月13日26-27赛季NBA常规赛黄蜂VS太阳", null),
                null, null, Set.of(), Set.of(), null);
        assertSameEvent(first, sameSeason);
        assertSameEvent(first, expandedSeason);
        assertDistinct(first, otherSeason);
    }

    @Test
    void monthDaySourcesDoNotRequireAnInventedYearAndSportSeasonsAreNotScreenSeasons() {
        SourceRecordAggregationRecord first = withEvidence(raw("体育", "6月18日25-26赛季中乙联赛", "甲队VS乙队"),
                null, 25, Set.of(), Set.of(), null);
        SourceRecordAggregationRecord second = withEvidence(raw("体育", "6月18日25-26赛季中乙联赛 甲队VS乙队", null),
                null, 26, Set.of(), Set.of(), null);
        assertSameEvent(first, second);
        assertSameEvent(first, raw("体育", "2026年6月18日25-26赛季中乙联赛 甲队VS乙队", null));
        SourceRecordAggregationRecord noYearOrSeason = withEvidence(raw("sports", "6月18日中乙联赛", "甲队VS乙队"),
                null, null, Set.of(), Set.of(), null);
        assertDistinct(noYearOrSeason, withEvidence(raw("sports", "6月18日中乙联赛甲队VS乙队", null),
                null, null, Set.of(), Set.of(), null));
        assertTrue(strategy.isCompatibleBinding(noYearOrSeason,
                unified(raw("sports", "2026年6月18日中乙联赛", "甲队VS乙队"))));
    }

    @Test
    void annualReplaysAndSameDayDifferentOpponentsCannotShareAnIdentity() {
        SourceRecordAggregationRecord first = raw("sports", "世界杯小组赛", "甲队VS乙队20260618");
        assertDistinct(first, raw("sports", "世界杯小组赛", "甲队VS乙队20250618"));
        assertEquals("YEAR_CONFLICT", strategy.bindingConflictReason(first,
                unified(raw("sports", "世界杯小组赛", "甲队VS乙队20250618"))));
        assertDistinct(first, raw("sports", "世界杯小组赛", "甲队VS丙队20260618"));
        assertDistinct(first, raw("sports", "世界杯小组赛", "甲队VS乙队20260619"));
    }

    @Test
    void missingFixtureDateCannotCreateAMatchButDoesNotBreakAnExistingCompatibleBinding() {
        SourceRecordAggregationRecord first = raw("sports", "世界杯小组赛", "甲队VS乙队");
        SourceRecordAggregationRecord sameUndated = raw("sports", "回放)世界杯小组赛 甲队VS乙队", null);
        SourceRecordAggregationRecord dated = raw("sports", "世界杯小组赛", "甲队VS乙队20260618");
        assertDistinct(first, sameUndated);
        assertDistinct(first, dated);
        assertTrue(strategy.isCompatibleBinding(first, unified(sameUndated)));
        assertTrue(strategy.isCompatibleBinding(first, unified(dated)));
        assertFalse(strategy.isCompatibleBinding(first, unified(raw("sports", "世界杯小组赛", "甲队VS丙队"))));
    }

    @Test
    void missingRoundAndSameLeagueHeadingAreAbsentEvidenceDuringBindingRefresh() {
        SourceRecordAggregationRecord detailed = raw("sports", "6月18日WTA250诺丁汉站女单第二轮", "郑钦文VS高芙");
        SourceRecordAggregationRecord missingRound = raw("sports", "6月18日WTA250诺丁汉站女单", "郑钦文VS高芙");
        assertDistinct(detailed, missingRound);
        assertTrue(strategy.isCompatibleBinding(missingRound, unified(detailed)));
        SourceRecordAggregationRecord heading = raw("sports", "6月18日世界杯小组赛", null);
        assertDistinct(heading, raw("sports", "6月18日世界杯小组赛", null));
        assertTrue(strategy.isCompatibleBinding(heading,
                unified(raw("sports", "6月18日世界杯小组赛", "甲队VS乙队"))));
    }

    @Test
    void namedNonFixtureEventsRemainMatchableAndParticipantNamesArePreserved() {
        assertSameEvent(raw("sports", "NBA 总冠军纽约尼克斯夺冠巡游20260618", null),
                raw("sports", "2026年6月18日 NBA 总冠军纽约尼克斯夺冠巡游", null));
        AggregationSportsIdentity schoolMatch = AggregationSportsIdentity.parse(
                "未来之星高中邀请赛 女子组决赛 精华女子高等学校vs阳明高中20260628", null);
        assertTrue(schoolMatch.participants().contains("精华女子高等学校"));
    }

    @Test
    void fullShortDramaTitleIsRequiredEvenWhenSubtitlesAreAlmostIdenticalOrIdsAndPeopleAgree() {
        SourceRecordAggregationRecord first = raw("短剧", "重生后我成为豪门掌权人", "她带着三个萌宝归来");
        SourceRecordAggregationRecord different = raw("短剧", "重生后我成为豪门掌权人", "她带着四个萌宝归来");
        assertDistinct(first, different);
        assertDistinct(withEvidence(first, "2026", null, Set.of("演员甲"), Set.of(), "35000001"),
                withEvidence(different, "2026", null, Set.of("演员甲"), Set.of(), "35000001"));
        assertEquals("SHORT_DRAMA_TITLE_CONFLICT", strategy.bindingConflictReason(first, unified(different)));
        assertDistinct(first, raw("短剧", first.title(), null));
        assertTrue(strategy.isCompatibleBinding(raw("短剧", first.title(), null), unified(first)));
        assertSameEvent(first, raw("短剧", "重生后我成为豪门掌权人，她带着三个萌宝归来", null));
    }

    @Test
    void splitChineseCastProvidesSameRoleEvidenceWithoutPromotingPlaceholdersOrRoleSwaps() {
        SourceRecordAggregationRecord combined = withEvidence(raw("短剧", "搬空祖产", "穿书大小姐遇上冷面煞君"),
                "2026", null, Set.of("演员甲＆演员乙"), Set.of(), null);
        SourceRecordAggregationRecord split = withEvidence(raw("短剧", combined.title(), combined.subtitle()),
                "2026", null, Set.of("演员甲", "演员乙"), Set.of(), null);
        assertTrue(strategy.calculateSimilarity(combined, split) >= 0.99);
        SourceRecordAggregationRecord chinese = withEvidence(raw("movie", "霸王别姬", null),
                "2026", null, Set.of("内详", "未知"), Set.of(), "35000001");
        SourceRecordAggregationRecord unrelated = withEvidence(raw("movie", "Titanic", null),
                "2026", null, Set.of("内详", "未知"), Set.of(), "35000001");
        assertDistinct(chinese, unrelated);
        assertDistinct(withEvidence(chinese, "2026", null, Set.of("演员甲"), Set.of("导演乙"), "35000001"),
                withEvidence(unrelated, "2026", null, Set.of("导演乙"), Set.of("演员甲"), "35000001"));
    }

    @Test
    void categoryDisagreementAndSeasonPartCourChapterConflictsRemainBlocked() {
        assertDistinct(raw("sports", "6月18日世界杯小组赛", "甲队VS乙队"),
                raw("movie", "6月18日世界杯小组赛", "甲队VS乙队"));
        assertDistinct(raw("movie", "作品名称", null), raw("series", "作品名称", null));
        assertDistinct(raw("综艺", "三国小剧", null), raw("综艺", "三国", "英雄风云 蜀国篇"));
        for (List<String> titles : List.of(List.of("作品名称第一季", "作品名称第二季"),
                List.of("作品名称 cour 1", "作品名称 cour 2"),
                List.of("作品名称 Part 1", "作品名称 Part 2"),
                List.of("真实节目第一期", "真实节目第二期"),
                List.of("三国：英雄风云 蜀国篇", "三国：英雄风云 吴国篇"))) {
            assertDistinct(raw("anime", titles.getFirst(), null), raw("anime", titles.getLast(), null));
        }
        assertSameEvent(raw("纪录片", "真实作品名称", null), raw("纪录片", "真实作品名称", null));
        assertSameEvent(raw("综艺", "真实节目名称第二季", null), raw("综艺", "真实节目名称第二季", null));
    }

    @Test
    void graphKeepsDifferentFixturesSeparateAndScoreVariantsShareAMatchLock() {
        SourceRecordAggregationRecord first = raw("sports", "7月4日25-26赛季浙BA", "桐庐76VS74临安");
        SourceRecordAggregationRecord scoreless = raw("sports", "7月4日25-26赛季浙BA 桐庐VS临安", null);
        SourceRecordAggregationRecord second = raw("sports", "7月4日25-26赛季浙BA", "庆元56VS61景宁");
        assertEquals(2, new SourceRecordGraphClusterer(strategy).cluster(List.of(first, scoreless, second)).size());
        Set<String> locks = new java.util.HashSet<>(AggregationMatchKeys.forSourceRecord(first));
        locks.retainAll(AggregationMatchKeys.forSourceRecord(scoreless));
        assertTrue(locks.stream().anyMatch(key -> key.startsWith("event:")));
    }

    private void assertDistinct(SourceRecordAggregationRecord left, SourceRecordAggregationRecord right) {
        assertTrue(strategy.calculateSimilarity(left, right) < 0.80);
        assertTrue(strategy.calculateSimilarity(left, unified(right)) < 0.80);
        assertTrue(strategy.findBestMatch(left, List.of(unified(right))).isEmpty());
    }

    private void assertSameEvent(SourceRecordAggregationRecord left, SourceRecordAggregationRecord right) {
        assertTrue(strategy.calculateSimilarity(left, right) >= 0.90);
        CatalogEntryAggregationCandidate candidate = unified(right);
        assertTrue(strategy.calculateSimilarity(left, candidate) >= 0.90);
        assertEquals(candidate.id(), strategy.findBestMatch(left, List.of(candidate)).orElseThrow());
        assertTrue(strategy.isCompatibleBinding(left, candidate));
    }

    private SourceRecordAggregationRecord raw(String category, String title, String subtitle) {
        return new SourceRecordAggregationRecord(UUID.randomUUID(), title, null, null, "2026", null, null,
                null, null, null, subtitle, null, category, category, Set.of(), Set.of(), Set.of(),
                null, null, null, null, "READY", "SUCCESS", "PENDING");
    }

    private SourceRecordAggregationRecord withEvidence(SourceRecordAggregationRecord raw, String year, Integer season,
            Set<String> actors, Set<String> directors, String douban) {
        return new SourceRecordAggregationRecord(raw.id(), raw.title(), raw.aliasTitle(), null, year, null, null,
                null, null, null, raw.subtitle(), season, raw.categoryName(), raw.categoryCode(), actors, directors,
                Set.of(), douban, null, null, null, "READY", "SUCCESS", "PENDING");
    }

    private CatalogEntryAggregationCandidate unified(SourceRecordAggregationRecord raw) {
        return new CatalogEntryAggregationCandidate(UUID.randomUUID(), raw.title(), raw.aliasTitle(), raw.subtitle(),
                raw.year(), raw.totalEpisodes(), raw.duration(), raw.remarks(), raw.categoryName(), raw.actorNames(),
                raw.directorNames(), raw.areaNames(), raw.doubanId(), raw.tmdbId(), raw.imdbId(), raw.rottenTomatoesId(), raw.season());
    }
}
