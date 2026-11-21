package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AggregationTitleNormalizerTest {

    @Test
    void genericSecondaryTitlesRemainGenericAfterReleaseNoiseAndSeasonRemoval() {
        for (String value : new String[] {"动态漫画 第1季", "完整版1080P", "正片 国语", "动态漫画·完整版", "S2"}) {
            assertFalse(AggregationTitleNormalizer.isDistinctiveSecondaryTitle(value), value);
        }
        assertTrue(AggregationTitleNormalizer.isDistinctiveSecondaryTitle("The Matrix"));
        assertTrue(AggregationTitleNormalizer.isDistinctiveSecondaryTitle("动画大师"));
    }

    @Test
    void audioLabelsDoNotChangeWorkIdentity() {
        String title = "你却爱着一个他";
        assertEquals(title, AggregationTitleNormalizer.completeIdentityTitle(title, "原声"));
        assertEquals(AggregationTitleNormalizer.normalizeTitle(title),
                AggregationTitleNormalizer.normalizeTitle(title + " (泰语版)"));
        assertFalse(AggregationTitleNormalizer.isDistinctiveSecondaryTitle("粤语配音版"));
        assertEquals("古战场传奇 吾血之亲",
                AggregationTitleNormalizer.completeIdentityTitle("古战场传奇", "吾血之亲"));
    }

    @Test
    void seasonParsingPreservesZeroAndTreatsMalformedHugeNumbersAsUnknown() {
        assertEquals(Integer.valueOf(0), AggregationTitleNormalizer.parseSeason("第零季"));
        assertEquals(Integer.valueOf(12), AggregationTitleNormalizer.parseSeason("第十二季"));
        assertEquals(Integer.valueOf(47), AggregationTitleNormalizer.parseSeason("47季"));
        assertEquals(AggregationTitleNormalizer.parseSeason("第47季"),
                AggregationTitleNormalizer.parseSeason("47季"));
        assertEquals(AggregationTitleNormalizer.normalizeTitle("新人教练金软景第2季"),
                AggregationTitleNormalizer.normalizeTitle("新人教练金软景2季"));
        assertNull(AggregationTitleNormalizer.parseSeason("新人教练金软景2"));
        assertNull(AggregationTitleNormalizer.parseSeason("2026"));
        assertNull(AggregationTitleNormalizer.parseSeason("2026季"));
        assertNull(AggregationTitleNormalizer.parseSeason("第二季度"));
        assertNull(AggregationTitleNormalizer.parseSeason("25-26赛季"));
        assertNull(AggregationTitleNormalizer.parseSeason("S999999999999999999999"));
    }

    @Test
    void eventDiscriminatorKeepsProvinceParticipantsSeasonAndYear() {
        assertNotEquals(
                AggregationTitleNormalizer.eventIdentityKey("NBA常规赛:黄蜂VS太阳20261113"),
                AggregationTitleNormalizer.eventIdentityKey("11月13日25-26赛季NBA常规赛黄蜂VS太阳"));
        assertNotEquals(
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季广东省城市足球超级联赛"),
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季福建省城市足球超级联赛"));
        assertNotEquals(
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季中乙联赛甲队VS乙队"),
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季中乙联赛丙队VS丁队"));
        assertNotEquals(
                AggregationTitleNormalizer.eventIdentityKey("2025年6月13日中乙联赛"),
                AggregationTitleNormalizer.eventIdentityKey("2026年6月13日中乙联赛"));
        assertEquals(
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季中乙联赛"),
                AggregationTitleNormalizer.eventIdentityKey("6月13日25-26赛季中乙联赛 1080p"));
        assertTrue(AggregationTitleNormalizer.eventIdentityKey("6月13日的故事").isEmpty());
    }
}
