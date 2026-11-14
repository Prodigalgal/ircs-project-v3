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
    void seasonParsingPreservesZeroAndTreatsMalformedHugeNumbersAsUnknown() {
        assertEquals(Integer.valueOf(0), AggregationTitleNormalizer.parseSeason("第零季"));
        assertEquals(Integer.valueOf(12), AggregationTitleNormalizer.parseSeason("第十二季"));
        assertNull(AggregationTitleNormalizer.parseSeason("S999999999999999999999"));
    }

    @Test
    void eventDiscriminatorKeepsProvinceParticipantsSeasonAndYear() {
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
