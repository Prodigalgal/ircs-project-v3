package com.prodigalgal.ircs.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Rule-level regression over curated title excerpts and synthetic boundary cases. These fixtures
 * exercise matching and graph clustering; they do not prove production error rates or historical
 * causation and do not execute the database merge pipeline.
 */
class AggregationProductionRegressionTest {

    private final AggregationMatchingStrategy strategy = AggregationTestFixtures.matchingStrategy();
    private final RawVideoGraphClusterer clusterer = new RawVideoGraphClusterer(strategy);

    /** Curated date/league title examples retained from the original investigation. */
    private static final List<String> SPORTS_TITLES = List.of(
            "6月10日25-26赛季世界杯热身赛",
            "6月10日25-26赛季土伦杯小组赛",
            "6月13日25-26赛季中乙联赛",
            "6月13日25-26赛季广东省城市足球超级联赛",
            "6月14日25-26赛季中甲联赛",
            "6月14日25-26赛季贵州村超联赛",
            "6月14日25-26赛季新疆足球超级联赛",
            "7月4日25-26赛季中超联赛",
            "7月4日25-26赛季湖北省城市足球联赛",
            "7月4日25-26赛季内蒙古足球超级联赛",
            "7月5日25-26赛季中乙联赛");

    /** Curated title examples exercising rejected douban_id=123. */
    private static final List<String> DIRTY_ID_TITLES = List.of(
            "全民转职", "寅娜知音", "一品高手", "万族之劫", "万界跪拜的我",
            "三角洲求生", "世界之外", "丧尸末世", "中东暑假工", "一秒一个技能点",
            "为了守望我的本命继兄", "末日狠人");

    @Test
    void sportsClusterSplitsIntoDistinctFixtures() {
        List<RawVideoAggregationRecord> raws = SPORTS_TITLES.stream()
                .map(title -> rawVideo(title, "sports", "2026", null, null, null))
                .toList();

        List<RawVideoAggregationCluster> clusters = clusterer.cluster(raws);

        // The same fixture from different sources must still collapse (6-13 中乙 appears once here,
        // 7-5 中乙 once), but different dates and different leagues must not.
        assertThat(clusters).hasSize(SPORTS_TITLES.size());
        assertThat(clusters).allMatch(cluster -> cluster.rawVideoIds().size() == 1);
    }

    @Test
    void repeatedSameFixtureStillMerges() {
        // Same league, same date, from two sources: this is the merge the aggregation exists for.
        List<RawVideoAggregationRecord> raws = List.of(
                rawVideo("7月4日25-26赛季中乙联赛", "sports", "2026", null, null, null),
                rawVideo("7月4日25-26赛季中乙联赛", "sports", "2026", null, null, null));

        List<RawVideoAggregationCluster> clusters = clusterer.cluster(raws);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().rawVideoIds()).hasSize(2);
    }

    @Test
    void placeholderDoubanIdNoLongerCollapsesUnrelatedWorks() {
        // Pre-fix, every one of these scored 1.0 against the others via the same-id shortcut and the
        // whole set collapsed into the single 全民诡异 row.
        List<RawVideoAggregationRecord> raws = DIRTY_ID_TITLES.stream()
                .map(title -> rawVideo(title, "anime", "2026", "123", null, null))
                .toList();

        List<RawVideoAggregationCluster> clusters = clusterer.cluster(raws);

        assertThat(clusters).hasSize(DIRTY_ID_TITLES.size());
        assertThat(clusters).allMatch(cluster -> cluster.rawVideoIds().size() == 1);
    }

    @Test
    void genuineCrossSourceDuplicateStillMergesWithSharedId() {
        List<RawVideoAggregationRecord> raws = List.of(
                rawVideo("罗浮剑主", "anime", "2026", "35000001", null, null),
                rawVideo("罗浮剑主", "anime", "2026", "35000001", null, null),
                rawVideo("罗浮剑主", "anime", "2026", "35000001", null, null));

        List<RawVideoAggregationCluster> clusters = clusterer.cluster(raws);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().rawVideoIds()).hasSize(3);
    }

    @Test
    void rankingOfDamagedFixturesMatchesExpectedIdentityCount() {
        // Sanity on the identity key itself over the real fixture titles.
        List<String> keys = SPORTS_TITLES.stream()
                .map(AggregationTitleNormalizer::eventIdentityKey)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();
        assertThat(keys).hasSize(SPORTS_TITLES.size());
    }

    @Test
    void genericSubtitleWithSeasonOrQualityDoesNotBridgeDifferentPrimaryTitles() {
        for (String subtitle : List.of("动态漫画 第1季", "完整版1080P", "正片 国语")) {
            List<RawVideoAggregationRecord> raws = List.of(
                    rawVideoWithSubtitle("天宫试炼", "anime", "2026", null, null, null, subtitle),
                    rawVideoWithSubtitle("沙漠猎人", "anime", "2026", null, null, null, subtitle));

            assertThat(clusterer.cluster(raws)).as(subtitle).hasSize(2);
        }
    }

    @Test
    void sameDateAndLeagueCannotEraseParticipantOrProvinceDifferences() {
        List<RawVideoAggregationRecord> raws = List.of(
                rawVideo("6月13日25-26赛季广东省城市足球超级联赛", "sports", "2026", null, null, null),
                rawVideo("6月13日25-26赛季福建省城市足球超级联赛", "sports", "2026", null, null, null),
                rawVideo("6月13日25-26赛季中乙联赛甲队VS乙队", "sports", "2026", null, null, null),
                rawVideo("6月13日25-26赛季中乙联赛丙队VS丁队", "sports", "2026", null, null, null));

        assertThat(clusterer.cluster(raws)).hasSize(4);
    }

    private RawVideoAggregationRecord rawVideo(
            String title,
            String categoryName,
            String year,
            String doubanId,
            String tmdbId,
            Integer season) {
        return rawVideoWithSubtitle(title, categoryName, year, doubanId, tmdbId, season, null);
    }

    private RawVideoAggregationRecord rawVideoWithSubtitle(
            String title, String categoryName, String year, String doubanId, String tmdbId,
            Integer season, String subtitle) {
        return new RawVideoAggregationRecord(
                UUID.randomUUID(),
                title,
                null,
                "Aggregation description",
                year,
                new BigDecimal("8.9"),
                LocalDate.of(2026, 6, 4),
                "1",
                "45m",
                "正片",
                subtitle,
                season,
                categoryName,
                categoryName,
                doubanId,
                tmdbId,
                null,
                null,
                "READY",
                "SUCCESS",
                "PENDING");
    }
}
