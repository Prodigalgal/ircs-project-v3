package com.prodigalgal.ircs.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CategoryAwareNormalizationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RawVideoTextNormalizer normalizer = new RawVideoTextNormalizer(
            mapper, new DescriptionMetadataExtractor(), new RawRelationAliasPolicy());

    @Test
    void sportsPreservesBothTeamsScoresDatesRoundsAndSeasonRanges() throws Exception {
        for (String date : new String[]{"6月28日", "20261115", "[20261115]"}) {
            String event = date + "25-26赛季新疆足球超级联赛 第23轮";
            RawVideoPatch patch = normalize("sports", event + " 巴州队VS和田队 3:2", "{}", "[]", null);
            assertEquals(event, patch.title());
            assertEquals("巴州队VS和田队 3:2", patch.subtitle());
            assertNull(patch.season());
        }
        RawVideoPatch score = normalize("sports", "湖人 123:122 勇士 第7场", "{\"season\":23}", "[]", null);
        assertEquals("湖人 123:122 勇士 第7场", score.title());
        assertNull(score.season());
    }

    @Test
    void sameDayDifferentMatchupsRetainDistinctIdentityAndExplicitSubtitle() throws Exception {
        RawVideoPatch first = normalize("sports", "20261115 NBA 湖人VS勇士", "{}", "[]", null);
        RawVideoPatch second = normalize("sports", "20261115 NBA 湖人VS快船", "{}", "[]", null);
        assertEquals(first.title(), second.title());
        assertNotEquals(first.subtitle(), second.subtitle());
        RawVideoPatch explicit = normalize("sports", "20261115 NBA", "{\"subtitle\":\"湖人 123:122 勇士\"}", "[]", null);
        assertEquals("湖人 123:122 勇士", explicit.subtitle());
    }

    @Test
    void sportsLocksPreserveManualSeasonAndBothSubtitleLockSpellings() throws Exception {
        for (String lock : new String[]{"subtitle", "sub_title"}) {
            RawVideoPatch patch = normalize("sports", "20261115 NBA 湖人VS勇士",
                    "{\"subtitle\":\"来源副标题\"}", "[\"season\",\"" + lock + "\"]", "人工副标题");
            assertEquals(9, patch.season());
            assertEquals("人工副标题", patch.subtitle());
        }
    }

    @Test
    void shortDramaPreservesFullStructuredIdentityAndDoesNotInferBareNumbers() throws Exception {
        RawVideoPatch first = normalize("short-drama", "被家族抛弃，我觉醒九亿属性点", "{}", "[]", null);
        RawVideoPatch second = normalize("short-drama", "被家族抛弃，我继承万亿家产", "{}", "[]", null);
        assertEquals("被家族抛弃", first.title());
        assertEquals("我觉醒九亿属性点", first.subtitle());
        assertNotEquals(first.subtitle(), second.subtitle());
        assertEquals("归来23", normalize("short-drama", "归来23", "{}", "[]", null).title());
        assertNull(normalize("short-drama", "归来23", "{}", "[]", null).season());
        RawVideoPatch explicit = normalize("short-drama", "归来 第二季:新生", "{}", "[]", null);
        assertEquals(2, explicit.season());
        assertEquals("新生", explicit.subtitle());
        assertEquals("归来Ⅲ", normalize("short-drama", "归来Ⅲ", "{}", "[]", null).title());
    }

    @Test
    void otherAndMissingCategoriesDoNotTreatChineseTailNumbersAsSeasons() throws Exception {
        for (String category : new String[]{"other", "", "legacy"}) {
            RawVideoPatch patch = normalize(category, "活动23", "{}", "[]", null);
            assertEquals("活动23", patch.title());
            assertNull(patch.season());
        }
        assertNull(normalize("movie", "钢铁侠3", "{}", "[]", null).season());
        assertEquals(2, normalize("anime", "作品 第2季", "{}", "[]", null).season());
        assertEquals("Dune Part Ⅱ", normalize("movie", "Dune Part Ⅱ", "{}", "[]", null).title());
        assertEquals("ラブライブ", normalize("anime", "ラブライブ", "{}", "[]", null).title());
    }

    @Test
    void movieSequelNumbersAndPartsNeverBecomeTelevisionSeasons() throws Exception {
        for (String title : new String[]{"叶问2", "叶问 第2部", "Rocky Ⅲ", "Dune Part Ⅱ"}) {
            RawVideoPatch movie = normalize("movie", title, "{}", "[]", null);
            assertEquals(title, movie.title(), title);
            assertNull(movie.season(), title);
        }
        RawVideoPatch series = normalize("series", "庆余年 第2季", "{}", "[]", null);
        assertEquals("庆余年", series.title());
        assertEquals(2, series.season());
    }

    @Test
    void onlyExplicitCategoryLabelsSelectSpecialProfiles() throws Exception {
        assertNull(normalize("other", "甲23", "{\"rawTypeName\":\"都市\"}", "[]", null).season());
        RawVideoPatch sports = normalize("other", "湖人 123:122 勇士",
                "{\"rawTypeName\":\"体育赛事\"}", "[]", null);
        assertEquals("湖人 123:122 勇士", sports.title());
        RawVideoPatch drama = normalize("other", "归来23", "{\"categoryName\":\"微短剧\"}", "[]", null);
        assertEquals("归来23", drama.title());
        assertNull(drama.season());
    }

    @Test
    void persistedClassificationTakesPrecedenceOverUntrustedSourceLabel() throws Exception {
        RawVideoPatch patch = normalize("movie", "钢铁侠3", "{\"rawTypeName\":\"短剧\"}", "[]", null);
        assertEquals("钢铁侠3", patch.title());
        assertNull(patch.season());
        RawVideoPatch sports = normalize("sports", "湖人 123:122 勇士", "{\"rawTypeName\":\"电影\"}", "[]", null);
        assertEquals("湖人 123:122 勇士", sports.title());
    }

    @Test
    void personNamesPreserveGlyphsAndEnglishOriginalsWhileSplittingChineseAmpersands() throws Exception {
        RawVideoPatch patch = normalize("short-drama", "归来", """
                {"actorNames":["罗予彤＆孙乾钊", "罗予彤&孙乾钊", "Robert Downey Jr.", "R&D"],
                 "directorNames":"张三&amp;李四"}
                """, "[]", null);
        assertEquals(Set.of("罗予彤", "孙乾钊", "Robert Downey Jr.", "R&D"), patch.actorValues());
        assertEquals(Set.of("张三", "李四"), patch.directorValues());
        RawVideoPatch locked = normalize("short-drama", "归来", "{\"actorNames\":\"甲&乙\"}", "[\"actors\"]", null);
        assertEquals(Set.of(), locked.actorValues());
    }

    @Test
    void descriptionFallbackPreservesPersonGlyphsBeforeDictionaryConversion() throws Exception {
        RawVideoTextNormalizer local = new RawVideoTextNormalizer(mapper, new DescriptionMetadataExtractor() {
            @Override
            public ExtractedMetadata extract(String description) {
                assertEquals("罗予彤与孙乾钊出演", description);
                return new ExtractedMetadata(Set.of("罗予彤", "孙乾钊"), Set.of());
            }
        }, new RawRelationAliasPolicy());
        RawVideoRecord record = new RawVideoRecord(UUID.randomUUID(), "PENDING", 0, "{}", "[]",
                "作品", null, null, null, "罗予彤与孙乾钊出演", null, null, null, null, null,
                null, null, null, null, null, null, null, "hash", "short-drama", null);
        RawVideoPatch patch = local.normalize(record, mapper.readTree("{}"));
        assertEquals(Set.of("罗予彤", "孙乾钊"), patch.actorValues());
    }

    @Test
    void placeholderAuthorityIdsBecomeNullButSourceCategoryZeroSurvives() throws Exception {
        RawVideoPatch patch = normalize("movie", "作品", """
                {"doubanId":"0","tmdbId":"N/A","imdbId":"tt0","rtId":"unknown","rawTypeId":"0"}
                """, "[]", null);
        assertNull(patch.doubanId());
        assertNull(patch.tmdbId());
        assertNull(patch.imdbId());
        assertNull(patch.rottenTomatoesId());
        assertEquals("0", patch.rawCategorySourceCode());
    }

    @Test
    void authorityIdFieldLocksPreserveManualValues() throws Exception {
        RawVideoRecord record = new RawVideoRecord(UUID.randomUUID(), "PENDING", 0, "{}",
                "[\"doubanId\",\"tmdbId\",\"imdbId\",\"rottenTomatoesId\"]",
                "作品", null, null, null, null, null, null, null, null, null, null, null,
                "manual-douban", "manual-tmdb", "manual-imdb", "manual-rt", null, "hash", "movie", null);
        RawVideoPatch patch = normalizer.normalize(record, mapper.readTree("""
                {"doubanId":"0","tmdbId":"0","imdbId":"0","rtId":"0"}
                """));
        assertEquals("manual-douban", patch.doubanId());
        assertEquals("manual-tmdb", patch.tmdbId());
        assertEquals("manual-imdb", patch.imdbId());
        assertEquals("manual-rt", patch.rottenTomatoesId());
    }

    @Test
    void everyStableCategoryHasAnExplicitPolicyAndKeepsMeaningfulIdentityTokens() throws Exception {
        String[] categories = {"movie", "series", "anime", "short-drama", "sports", "variety", "documentary",
                "news", "education", "music", "adult", "other"};
        for (String category : categories) {
            String title = switch (category) {
                case "movie" -> "叶问2";
                case "series" -> "真实剧集 第2季";
                case "anime" -> "星河物语 Part 2";
                case "sports" -> "20260618 NBA 甲队VS乙队";
                case "variety" -> "年度声音2026";
                case "documentary" -> "历史长河 蜀国篇";
                case "news" -> "20260618 城市交通报道";
                case "education" -> "数据库课程 第2章";
                case "music" -> "同一首歌 Live 20260618";
                default -> "独立资源记录(2)";
            };
            RawVideoPatch patch = normalize(category, title, "{}", "[]", null);
            String complete = patch.title() + (patch.subtitle() == null ? "" : " " + patch.subtitle());
            if (category.equals("series")) assertEquals(2, patch.season());
            else {
                assertNull(patch.season(), category);
                org.junit.jupiter.api.Assertions.assertTrue(complete.replace(" ", "").contains(title.replace(" ", "")), category);
            }
        }
    }

    @Test
    void annualProgrammesPartsAndChaptersAreNotAutomaticallyTelevisionSeasons() throws Exception {
        assertNull(normalize("variety", "年度节目2026", "{}", "[]", null).season());
        assertNull(normalize("documentary", "历史长河 第2部", "{}", "[]", null).season());
        assertNull(normalize("anime", "星河物语 Part 2", "{}", "[]", null).season());
        assertEquals(0, normalize("anime", "星河物语 S00", "{}", "[]", null).season());
    }

    @Test
    void musicArtistsAndEducationLecturersUseThePerformerRoleWithoutRewritingNames() throws Exception {
        RawVideoPatch music = normalize("music", "现场音乐会", "{\"artistNames\":[\"罗予彤＆孙乾钊\"]}", "[]", null);
        assertEquals(Set.of("罗予彤", "孙乾钊"), music.actorValues());
        assertEquals(Set.of(), music.directorValues());
        RawVideoPatch course = normalize("education", "课程 第2章", "{\"lecturerNames\":[\"讲师甲\",\"讲师乙\"]}", "[]", null);
        assertEquals(Set.of("讲师甲", "讲师乙"), course.actorValues());
    }

    private RawVideoPatch normalize(String category, String title, String metadata, String locks, String subtitle)
            throws Exception {
        RawVideoRecord record = new RawVideoRecord(UUID.randomUUID(), "PENDING", 0, "{}", locks,
                title, null, 9, subtitle, null, "2026", null, null, null, null, null, null,
                null, null, null, null, null, "hash-v1", category, null);
        // The existing season is absent unless a field lock explicitly asks to retain it.
        if (!locks.contains("season")) {
            record = new RawVideoRecord(record.id(), "PENDING", 0, "{}", locks,
                    title, null, null, subtitle, null, "2026", null, null, null, null, null, null,
                    null, null, null, null, null, "hash-v1", category, null);
        }
        return normalizer.normalize(record, mapper.readTree(metadata));
    }
}
