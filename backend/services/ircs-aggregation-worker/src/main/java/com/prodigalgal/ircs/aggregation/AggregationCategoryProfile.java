package com.prodigalgal.ircs.aggregation;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Identity policy, independent of a source site's display taxonomy. Unknown never relaxes it. */
enum AggregationCategoryProfile {
    MOVIE, SERIES, ANIMATION, SHORT_DRAMA, SPORTS, PROGRAMME, DOCUMENTARY, NEWS, EDUCATION, MUSIC, ADULT, OTHER, UNKNOWN;

    static final String POLICY_VERSION = "category-identity-v3";
    private static final Pattern NUMBERED_PART = Pattern.compile(
            "(?i)(part|cour)\\s*([0-9]+)|第\\s*([0-9零一二三四五六七八九十百两]+)\\s*([篇章卷期部])");
    private static final Pattern EDITION_YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern FILM_SEQUENCE = Pattern.compile(
            "(?i)^(.+?)(?:第\\s*([0-9零一二三四五六七八九十百两]+)\\s*部|\\s*part\\s*([0-9]+)|(?<!\\d)([0-9]{1,2}))$");
    private static final Pattern FILM_ROMAN = Pattern.compile("(?i)^(.+?)\\s+(I|II|III|IV|V|VI|VII|VIII|IX|X)$");
    private static final Pattern NAMED_CHAPTER = Pattern.compile("(?:^|[\\s:：·])([^\\s:：·]{1,16}篇)(?:$|[\\s])");
    private static final Pattern DATED_RECORD = Pattern.compile(
            "(?<!\\d)((?:19|20)\\d{2})(?:[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?|(\\d{2})(\\d{2}))(?!\\d)");

    static AggregationCategoryProfile from(String category) {
        if (!StringUtils.hasText(category)) { return UNKNOWN; }
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "电影", "movie", "film" -> MOVIE;
            case "剧集", "电视剧", "连续剧", "tv", "series", "drama" -> SERIES;
            case "动漫", "动画", "anime", "animation" -> ANIMATION;
            case "短剧", "微短剧", "short_drama", "short-drama", "shortdrama" -> SHORT_DRAMA;
            case "体育", "体育赛事", "sports", "sport" -> SPORTS;
            case "综艺", "variety", "programme", "show" -> PROGRAMME;
            case "纪录片", "记录片", "documentary" -> DOCUMENTARY;
            case "news", "新闻", "新闻资讯" -> NEWS;
            case "education", "教育", "教育知识", "课程", "教程" -> EDUCATION;
            case "music", "音乐", "音乐演出", "演唱会" -> MUSIC;
            case "adult", "成人", "成人视频" -> ADULT;
            case "unknown_category", "unknown", "未知", "未分类" -> UNKNOWN;
            default -> OTHER;
        };
    }

    static String category(RawVideoAggregationRecord raw) {
        AggregationCategoryProfile code = from(raw.categoryCode());
        return StringUtils.hasText(raw.categoryCode()) && (code != OTHER && code != UNKNOWN
                || "other".equalsIgnoreCase(raw.categoryCode())) ? raw.categoryCode() : raw.categoryName();
    }

    /** Parts/cours/chapters are different axes; part 2 must not turn into season 2. */
    static Map<String, String> installments(String completeTitle) {
        if (!StringUtils.hasText(completeTitle)) { return Map.of(); }
        Map<String, String> parts = new java.util.HashMap<>();
        Matcher matcher = NUMBERED_PART.matcher(completeTitle);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                Integer number = AggregationTitleNormalizer.parseSeason("S" + matcher.group(2));
                if (number != null) { parts.put(matcher.group(1).toLowerCase(Locale.ROOT), number.toString()); }
            } else {
                Integer number = AggregationTitleNormalizer.parseSeason("第" + matcher.group(3) + "季");
                if (number != null) { parts.put(matcher.group(4), number.toString()); }
            }
        }
        Matcher chapter = NAMED_CHAPTER.matcher(completeTitle);
        if (!parts.containsKey("篇") && chapter.find()) { parts.put("chapter", chapter.group(1)); }
        for (String format : List.of("OVA", "OAD", "特别篇", "番外篇", "剧场版", "动态漫画")) {
            if (completeTitle.toUpperCase(Locale.ROOT).contains(format)) { parts.put("format", format); }
        }
        return Map.copyOf(parts);
    }

    static boolean installmentsConflict(Map<String, String> left, Map<String, String> right) {
        return left.entrySet().stream().anyMatch(part -> right.containsKey(part.getKey())
                && !part.getValue().equals(right.get(part.getKey())));
    }

    static Map<String, String> installments(String completeTitle, AggregationCategoryProfile profile) {
        Map<String, String> result = new java.util.HashMap<>(installments(completeTitle));
        if (profile == NEWS || profile == MUSIC) {
            String date = recordDate(completeTitle);
            if (date != null) { result.put("record-date", date); }
        }
        if (profile == MUSIC) {
            String text = completeTitle == null ? "" : completeTitle.toLowerCase(Locale.ROOT);
            if (text.contains("现场") || text.contains("演唱会") || text.matches(".*\\blive\\b.*")) result.put("music-version", "LIVE");
            else if (text.contains("录音室") || text.matches(".*\\bstudio\\b.*")) result.put("music-version", "STUDIO");
            else if (text.contains("伴奏") || text.matches(".*\\binstrumental\\b.*")) result.put("music-version", "INSTRUMENTAL");
            else if (text.contains("翻唱") || text.matches(".*\\bcover\\b.*")) result.put("music-version", "COVER");
            else if (text.matches(".*\\bremix\\b.*")) result.put("music-version", "REMIX");
        }
        return Map.copyOf(result);
    }

    private static String recordDate(String title) {
        Matcher matcher = DATED_RECORD.matcher(title == null ? "" : title);
        while (matcher.find()) {
            try {
                return java.time.LocalDate.of(Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2) == null ? matcher.group(4) : matcher.group(2)),
                        Integer.parseInt(matcher.group(3) == null ? matcher.group(5) : matcher.group(3))).toString();
            } catch (java.time.DateTimeException ignored) {
                // Malformed date strings are not evidence of a publication/performance day.
            }
        }
        return null;
    }

    boolean usesScreenSeason() {
        return this == SERIES || this == ANIMATION || this == SHORT_DRAMA || this == PROGRAMME || this == DOCUMENTARY;
    }

    boolean requiresCompleteTitle() {
        return this == SHORT_DRAMA || this == NEWS || this == EDUCATION || this == MUSIC
                || this == ADULT || this == OTHER || this == UNKNOWN;
    }

    static String editionYear(String completeTitle) {
        Matcher matcher = EDITION_YEAR.matcher(completeTitle == null ? "" : completeTitle);
        return matcher.find() ? matcher.group(1) : null;
    }

    String normalizeTitle(String value) {
        if (requiresCompleteTitle()) {
            String source = value == null ? "" : value;
            if (this == NEWS || this == MUSIC) {
                String date = recordDate(source);
                if (date != null) { source = DATED_RECORD.matcher(source).replaceAll(date); }
            }
            return java.text.Normalizer.normalize(source, java.text.Normalizer.Form.NFKC)
                    .replaceAll("[\\p{P}\\p{Z}\\s]+", "").toLowerCase(Locale.ROOT);
        }
        return AggregationTitleNormalizer.normalizeTitle(value == null ? "" : value);
    }

    static boolean filmSequenceConflict(String left, String right) {
        if (left.matches("\\d+") && right.matches("\\d+") && !left.equals(right)) { return true; }
        FilmSequence a = filmSequence(left), b = filmSequence(right);
        return !a.base().isEmpty() && a.base().equals(b.base()) && !a.number().equals(b.number());
    }

    private static FilmSequence filmSequence(String value) {
        String source = value == null ? "" : value;
        String romanSource = source.replaceAll("([\\u2160-\\u216B\\u2170-\\u217B])", " $1");
        Matcher roman = FILM_ROMAN.matcher(java.text.Normalizer.normalize(romanSource,
                java.text.Normalizer.Form.NFKC));
        if (roman.matches()) {
            int number = switch (roman.group(2).toUpperCase(Locale.ROOT)) {
                case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
                case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; default -> 10;
            };
            return new FilmSequence(MOVIE.normalizeTitle(roman.group(1)), number);
        }
        Matcher matcher = FILM_SEQUENCE.matcher(source);
        if (matcher.matches()) {
            String token = matcher.group(2) != null ? matcher.group(2)
                    : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            Integer number = AggregationTitleNormalizer.parseSeason("第" + token + "季");
            if (number != null) {
                return new FilmSequence(MOVIE.normalizeTitle(matcher.group(1)), number);
            }
        }
        return new FilmSequence(MOVIE.normalizeTitle(value), 1);
    }

    private record FilmSequence(String base, Integer number) {}
}
