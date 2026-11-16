package com.prodigalgal.ircs.normalization;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Selects text rules without turning genre guesses into a persisted classification. */
enum NormalizationProfile {
    MOVIE, SERIES, ANIME, VARIETY, DOCUMENTARY, SPORTS, SHORT_DRAMA, NEWS, EDUCATION, MUSIC, ADULT, OTHER, CONSERVATIVE;

    private static final Set<String> CONSERVATIVE_CATEGORIES = Set.of("news", "education", "music", "adult");
    private static final Set<String> SPORTS_LABELS = Set.of(
            "sports", "sport", "体育", "体育赛事", "赛事", "足球", "篮球", "网球", "排球",
            "乒乓球", "羽毛球", "斯诺克", "赛车", "nba", "cba", "f1", "电竞");
    private static final Set<String> SHORT_DRAMA_LABELS = Set.of(
            "short-drama", "shortdrama", "短剧", "微短剧");

    static NormalizationProfile select(RawVideoRecord record, JsonNode metadata) {
        NormalizationProfile stored = fromCode(record.categoryCode());
        if (stored != CONSERVATIVE && stored != OTHER
                || CONSERVATIVE_CATEGORIES.contains(normalize(record.categoryCode()))) {
            return stored;
        }
        // Numeric source IDs and broad genre labels (都市/言情/重生) do not establish a profile.
        NormalizationProfile byStoredLabel = explicitSourceLabel(record.sourceCategoryName());
        if (byStoredLabel != CONSERVATIVE) {
            return byStoredLabel;
        }
        for (String key : new String[]{"rawTypeName", "sourceCategoryName", "categoryName", "category", "type", "videoType"}) {
            JsonNode node = metadata.get(key);
            if (node != null && node.isTextual()) {
                NormalizationProfile byLabel = explicitSourceLabel(node.asText());
                if (byLabel != CONSERVATIVE) {
                    return byLabel;
                }
            }
        }
        return stored;
    }

    private static NormalizationProfile fromCode(String code) {
        return switch (normalize(code)) {
            case "movie" -> MOVIE;
            case "series" -> SERIES;
            case "anime" -> ANIME;
            case "variety" -> VARIETY;
            case "documentary" -> DOCUMENTARY;
            case "sports" -> SPORTS;
            case "short-drama" -> SHORT_DRAMA;
            case "news" -> NEWS;
            case "education" -> EDUCATION;
            case "music" -> MUSIC;
            case "adult" -> ADULT;
            case "other" -> OTHER;
            default -> CONSERVATIVE;
        };
    }

    private static NormalizationProfile explicitSourceLabel(String value) {
        String label = normalize(value);
        if (SPORTS_LABELS.contains(label) || label.contains("体育") || label.contains("赛事")) {
            return SPORTS;
        }
        if (SHORT_DRAMA_LABELS.contains(label) || label.contains("短剧")) {
            return SHORT_DRAMA;
        }
        // Only unambiguous source type labels select a policy; genre guesses remain excluded.
        if (Set.of("movie", "电影", "动作片", "喜剧片", "爱情片", "科幻片", "恐怖片", "战争片", "剧情片").contains(label)) return MOVIE;
        if (Set.of("series", "tv", "剧集", "电视剧", "连续剧", "国产剧", "欧美剧", "日韩剧", "泰国剧").contains(label)) return SERIES;
        if (Set.of("anime", "动漫", "动画", "国产动漫", "日韩动漫", "欧美动漫").contains(label)) return ANIME;
        if (Set.of("variety", "综艺", "大陆综艺", "日韩综艺", "港台综艺", "欧美综艺").contains(label)) return VARIETY;
        if (Set.of("documentary", "纪录片", "记录片").contains(label)) return DOCUMENTARY;
        if (Set.of("news", "新闻", "新闻资讯").contains(label)) return NEWS;
        if (Set.of("education", "教育", "教育知识", "课程", "教程").contains(label)) return EDUCATION;
        if (Set.of("music", "音乐", "音乐演出", "演唱会", "mv").contains(label)) return MUSIC;
        if (Set.of("adult", "成人", "成人视频").contains(label)) return ADULT;
        return CONSERVATIVE;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    boolean preserveIdentityPunctuation() {
        return this == SPORTS || this == SHORT_DRAMA || this == VARIETY || this == DOCUMENTARY
                || this == NEWS || this == EDUCATION || this == MUSIC || this == ADULT || this == OTHER || this == CONSERVATIVE;
    }

    boolean inferBareSequence() {
        return this == SERIES || this == ANIME;
    }

    boolean usesScreenSeasons() {
        return this == SERIES || this == ANIME || this == VARIETY || this == DOCUMENTARY || this == SHORT_DRAMA;
    }

    boolean retainsUnstructuredIdentity() {
        return this == NEWS || this == EDUCATION || this == MUSIC || this == ADULT || this == OTHER || this == CONSERVATIVE;
    }
}
