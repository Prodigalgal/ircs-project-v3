package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AllContentCategoryStrategiesTest {
    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();

    @Test
    void movieFranchisesAndNumericWorkNamesKeepTheirIdentity() {
        for (String[] titles : new String[][]{{"叶问", "叶问2"}, {"叶问 第2部", "叶问 第3部"},
                {"Rocky II", "Rocky III"}, {"RockyⅡ", "RockyⅢ"}, {"1917", "1918"}}) {
            distinct(raw("movie", titles[0], null, Set.of("演员甲")), raw("movie", titles[1], null, Set.of("演员甲")));
        }
        same(raw("movie", "叶问2", null, Set.of()), raw("movie", "叶问2", null, Set.of()));
    }

    @Test
    void seriesSeasonsAndAnimationFormatsAreSeparateIdentityAxes() {
        distinct(raw("series", "真实剧集 第1季", null, Set.of()), raw("series", "真实剧集 第2季", null, Set.of()));
        same(raw("series", "真实剧集 第2季", null, Set.of()), raw("series", "真实剧集 第2季", null, Set.of()));
        distinct(raw("anime", "星河物语 Part 1", null, Set.of()), raw("anime", "星河物语 Part 2", null, Set.of()));
        distinct(raw("anime", "星河物语", null, Set.of()), raw("anime", "星河物语 OVA", null, Set.of()));
        same(raw("anime", "星河物语 OVA", null, Set.of()), raw("anime", "星河物语 OVA", null, Set.of()));
    }

    @Test
    void programmeEditionAndDocumentaryChapterAreNotReleaseNoise() {
        distinct(raw("variety", "年度声音2025", null, Set.of()), raw("variety", "年度声音2026", null, Set.of()));
        same(raw("variety", "年度声音2026", null, Set.of()), raw("variety", "年度声音2026", null, Set.of()));
        distinct(raw("documentary", "历史长河", "蜀国篇", Set.of()), raw("documentary", "历史长河", "吴国篇", Set.of()));
        same(raw("documentary", "历史长河", "蜀国篇", Set.of()), raw("documentary", "历史长河", "蜀国篇", Set.of()));
    }

    @Test
    void newsNeedsAConcretePublicationDayInsteadOfAnUndatedGenericHeadline() {
        same(raw("news", "20260618 城市交通报道", null, Set.of()), raw("news", "2026-06-18 城市交通报道", null, Set.of()));
        distinct(raw("news", "20260618 城市交通报道", null, Set.of()), raw("news", "20260619 城市交通报道", null, Set.of()));
        distinct(raw("news", "城市交通报道", null, Set.of()), raw("news", "城市交通报道", null, Set.of()));
    }

    @Test
    void educationUsesLecturerAndCompleteCourseChapter() {
        same(raw("education", "数据库课程 第1章", null, Set.of("讲师甲")), raw("education", "数据库课程 第1章", null, Set.of("讲师甲")));
        distinct(raw("education", "数据库课程 第1章", null, Set.of("讲师甲")), raw("education", "数据库课程 第2章", null, Set.of("讲师甲")));
        distinct(raw("education", "数据库课程 第1章", null, Set.of("讲师甲")), raw("education", "数据库课程 第1章", null, Set.of("讲师乙")));
        distinct(raw("education", "数据库课程", null, Set.of()), raw("education", "数据库课程", null, Set.of()));
    }

    @Test
    void musicSeparatesArtistsAndPerformanceVersions() {
        same(raw("music", "同一首歌 Live", null, Set.of("艺人甲")), raw("music", "同一首歌 Live", null, Set.of("艺人甲")));
        distinct(raw("music", "同一首歌 Live", null, Set.of("艺人甲")), raw("music", "同一首歌 Studio", null, Set.of("艺人甲")));
        distinct(raw("music", "同一首歌", null, Set.of("艺人甲")), raw("music", "同一首歌", null, Set.of("艺人乙")));
        distinct(raw("music", "同一首歌", null, Set.of()), raw("music", "同一首歌", null, Set.of()));
    }

    @Test
    void sourceOnlyAndUnclassifiedTypesDoNotFuzzyMergeDifferentCompleteTitles() {
        for (String type : new String[]{"adult", "other", "unknown"}) {
            distinct(raw(type, "独立资源记录第二集", null, Set.of()), raw(type, "独立资源记录第三集", null, Set.of()));
            same(raw(type, "独立资源记录第二集", null, Set.of()), raw(type, "独立资源记录第二集", null, Set.of()));
        }
    }

    private void same(RawVideoAggregationRecord left, RawVideoAggregationRecord right) {
        assertTrue(strategy.calculateSimilarity(left, right) >= 0.90);
        assertFalse(strategy.findBestMatch(left, java.util.List.of(unified(right))).isEmpty());
    }

    private void distinct(RawVideoAggregationRecord left, RawVideoAggregationRecord right) {
        assertTrue(strategy.calculateSimilarity(left, right) < 0.80);
        assertTrue(strategy.findBestMatch(left, java.util.List.of(unified(right))).isEmpty());
    }

    private RawVideoAggregationRecord raw(String type, String title, String subtitle, Set<String> people) {
        return new RawVideoAggregationRecord(UUID.randomUUID(), title, null, null, "2026", null, null,
                null, null, null, subtitle, null, type, type, people, Set.of(), Set.of(),
                null, null, null, null, "READY", "SUCCESS", "PENDING");
    }

    private UnifiedVideoAggregationCandidate unified(RawVideoAggregationRecord raw) {
        return new UnifiedVideoAggregationCandidate(UUID.randomUUID(), raw.title(), null, raw.subtitle(), raw.year(),
                null, null, null, raw.categoryName(), raw.actorNames(), raw.directorNames(), raw.areaNames(),
                null, null, null, null, raw.season());
    }
}
