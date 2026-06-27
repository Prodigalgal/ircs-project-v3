package com.prodigalgal.ircs.common.normalization;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class StandardContentCategoryClassifier {

    public static final String MOVIE = "movie";
    public static final String SERIES = "series";
    public static final String SHORT_DRAMA = "short-drama";
    public static final String ANIME = "anime";
    public static final String VARIETY = "variety";
    public static final String DOCUMENTARY = "documentary";

    private static final Set<String> ALLOWED_CODES = Set.of(
            MOVIE,
            SERIES,
            SHORT_DRAMA,
            ANIME,
            VARIETY,
            DOCUMENTARY);
    private static final Set<String> TOP_CATEGORY_NAMES = Set.of(
            "movie", "film", "cinema", "电影", "影片",
            "series", "tv", "teleplay", "电视剧", "连续剧", "剧集",
            "short-drama", "shortdrama", "shorts", "短剧", "微短剧",
            "anime", "animation", "cartoon", "动漫", "动画", "番剧",
            "variety", "综艺",
            "documentary", "docu", "纪录", "纪录片", "记录", "记录片");
    private static final List<CategoryRule> RULES = List.of(
            rule(SHORT_DRAMA, "短剧", 100,
                    Set.of("shortdrama", "shorts", "短剧", "微短剧", "爽剧", "爽文", "漫剧", "ai漫剧",
                            "逆袭", "女频", "男频", "重生", "赘婿", "脑洞", "闪婚", "都市",
                            "总裁", "言情", "穿越", "年代", "复仇", "战神", "神医", "萌宝",
                            "甜宠", "虐恋", "仙侠", "反转", "霸总", "龙王", "兵王"),
                    Set.of("电影", "影片", "电视剧", "连续剧", "剧集", "动漫", "动画", "番剧")),
            rule(ANIME, "动漫", 95,
                    Set.of("anime", "animation", "cartoon", "动漫", "动画", "番剧", "国漫", "日漫",
                            "卡通", "二次元", "剧场版", "有声动漫", "国产动漫", "日本动漫",
                            "日韩动漫", "欧美动漫"),
                    Set.of("短剧", "真人秀", "综艺")),
            rule(DOCUMENTARY, "纪录片", 95,
                    Set.of("documentary", "docu", "纪录", "纪录片", "记录", "记录片", "纪实"),
                    Set.of("资讯", "新闻")),
            rule(VARIETY, "综艺", 90,
                    Set.of("variety", "综艺", "大陆综艺", "港台综艺", "日韩综艺", "欧美综艺",
                            "真人秀", "晚会", "演唱会", "访谈", "脱口秀", "选秀", "相声", "小品"),
                    Set.of("纪录", "记录")),
            rule(SERIES, "电视剧", 80,
                    Set.of("series", "tv", "teleplay", "电视剧", "连续剧", "剧集", "剧场", "国产剧",
                            "内地剧", "大陆剧", "国剧", "港剧", "香港剧", "港澳剧", "台剧",
                            "台湾剧", "韩剧", "韩国剧", "日剧", "日本剧", "美剧", "欧美剧",
                            "英剧", "海外剧", "泰剧", "马泰剧", "网剧", "自制剧"),
                    Set.of("短剧", "微短剧", "动漫", "动画", "电影", "影片", "纪录", "记录")),
            rule(MOVIE, "电影", 70,
                    Set.of("movie", "film", "cinema", "电影", "影片", "大片", "微电影", "短片",
                            "剧情片", "喜剧片", "动作片", "爱情片", "科幻片", "恐怖片", "战争片",
                            "犯罪片", "灾难片", "悬疑片", "惊悚片", "奇幻片", "冒险片", "历史片",
                            "古装片", "家庭片", "西部片", "伦理片", "情色片", "邵氏"),
                    Set.of("电视剧", "连续剧", "剧集", "短剧", "微短剧", "动漫", "动画", "综艺",
                            "纪录", "记录")));

    private StandardContentCategoryClassifier() {
    }

    public static boolean isAllowedCode(String code) {
        return ALLOWED_CODES.contains(normalizeCode(code));
    }

    public static Optional<Match> classify(String sourceCode, String sourceName) {
        Optional<Match> byCode = classifySingle(sourceCode);
        if (byCode.isPresent()) {
            return byCode;
        }
        return classifySingle(sourceName);
    }

    public static Optional<Match> classifySingle(String value) {
        String normalized = normalizeForMatch(value);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        String code = normalizeCode(value);
        if (ALLOWED_CODES.contains(code)) {
            return Optional.of(matchForCode(code));
        }
        if ("tv".equals(code) || "teleplay".equals(code)) {
            return Optional.of(matchForCode(SERIES));
        }
        if ("shortdrama".equals(normalized) || "shorts".equals(normalized)) {
            return Optional.of(matchForCode(SHORT_DRAMA));
        }

        Match best = null;
        int bestScore = 0;
        for (CategoryRule rule : RULES) {
            int score = rule.score(normalized);
            if (score > bestScore) {
                bestScore = score;
                best = new Match(rule.code(), rule.name());
            }
        }
        return Optional.ofNullable(best);
    }

    public static Optional<String> inferCode(String sourceCode, String sourceName) {
        return classify(sourceCode, sourceName).map(Match::code);
    }

    public static Optional<String> inferName(String sourceName) {
        return classifySingle(sourceName).map(Match::name);
    }

    public static boolean isTopCategoryName(String value) {
        String normalized = normalizeForMatch(value);
        if (normalized.isBlank()) {
            return false;
        }
        return TOP_CATEGORY_NAMES.stream()
                .map(StandardContentCategoryClassifier::normalizeForMatch)
                .anyMatch(normalized::equals);
    }

    private static CategoryRule rule(String code, String name, int weight, Set<String> keywords, Set<String> negatives) {
        return new CategoryRule(code, name, weight, keywords, negatives);
    }

    private static Match matchForCode(String code) {
        return switch (code) {
            case MOVIE -> new Match(MOVIE, "电影");
            case SERIES -> new Match(SERIES, "电视剧");
            case SHORT_DRAMA -> new Match(SHORT_DRAMA, "短剧");
            case ANIME -> new Match(ANIME, "动漫");
            case VARIETY -> new Match(VARIETY, "综艺");
            case DOCUMENTARY -> new Match(DOCUMENTARY, "纪录片");
            default -> throw new IllegalArgumentException("Unsupported content category code: " + code);
        };
    }

    private static String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
    }

    private static String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-:/|，,。·.]+", "");
    }

    public record Match(String code, String name) {
    }

    private record CategoryRule(String code, String name, int weight, Set<String> keywords, Set<String> negatives) {

        private int score(String normalizedInput) {
            for (String negative : negatives) {
                if (normalizedInput.contains(normalizeForMatch(negative))) {
                    return -1;
                }
            }
            int best = 0;
            for (String keyword : keywords) {
                String normalizedKeyword = normalizeForMatch(keyword);
                if (normalizedInput.equals(normalizedKeyword)) {
                    best = Math.max(best, weight + 50);
                } else if (normalizedInput.contains(normalizedKeyword)) {
                    best = Math.max(best, weight);
                }
            }
            return best;
        }
    }
}
