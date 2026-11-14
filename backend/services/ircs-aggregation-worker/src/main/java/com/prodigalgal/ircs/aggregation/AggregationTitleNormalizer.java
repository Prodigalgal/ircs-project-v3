package com.prodigalgal.ircs.aggregation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Single source of truth for aggregation title fingerprinting. Previously duplicated between
 * {@link AggregationMatchingStrategy} and {@link AggregationMatchKeys}, which had already drifted
 * (alias splitting and the Chinese digit table differed), so the similarity score and the match-key
 * lock could disagree about the same pair of videos.
 */
final class AggregationTitleNormalizer {

    static final Pattern SEASON_PATTERN = Pattern.compile(
            "(?i)(?:season|s)\\s*(\\d+)|第\\s*([0-9零一二三四五六七八九十百两]+)\\s*[季部]");
    static final Pattern VERSION_REAL = Pattern.compile("(?i)(真人版|剧版|live\\s*action)");
    static final Pattern VERSION_ANIME = Pattern.compile("(?i)(动画版|动漫|anime|animation)");
    static final Pattern RELEASE_NOISE = Pattern.compile(
            "(?i)(全集|更新至|完结|国语|粤语|中字|4k|1080p|2160p|bluray|web-dl|第\\d+集|\\d+集全)");
    private static final Pattern GENERIC_SECONDARY_TITLE = Pattern.compile(
            "(?i)(?:动态漫画|漫画|动漫|动画|动画版|完整版|完整|正片|电影版|剧场版|特别篇|番外篇|预告片|合集|"
                    + "第?[0-9零一二三四五六七八九十百两]+[部季集篇卷]|[上中下]部|part[0-9ivx]+|season\\d+|s\\d+)+");

    /**
     * Date-stamped event titles (sports fixtures, live broadcasts). These share a long common suffix
     * ("月X日" + "NN-NN赛季" + league) that Jaro-Winkler scores as near-identical even though the
     * fixture is different, so the discriminator has to be lifted out of the similarity string.
     */
    private static final Pattern EVENT_DATE = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern EVENT_MARKER = Pattern.compile(
            "(?i)(赛季|联赛|比赛|世界杯|世预赛|杯(?:赛|小组赛|决赛|半决赛|预选赛|资格赛|\\s|$)|\\bvs\\b)");

    private static final Map<Character, Integer> CHINESE_DIGITS = Map.ofEntries(
            Map.entry('零', 0),
            Map.entry('一', 1),
            Map.entry('二', 2),
            Map.entry('两', 2),
            Map.entry('三', 3),
            Map.entry('四', 4),
            Map.entry('五', 5),
            Map.entry('六', 6),
            Map.entry('七', 7),
            Map.entry('八', 8),
            Map.entry('九', 9));

    private AggregationTitleNormalizer() {
    }

    static String normalizeTitle(String value) {
        String normalized = RELEASE_NOISE.matcher(value).replaceAll(" ");
        normalized = SEASON_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = VERSION_REAL.matcher(normalized).replaceAll(" ");
        normalized = VERSION_ANIME.matcher(normalized).replaceAll(" ");
        normalized = normalized.replaceAll("[\\p{P}\\p{Z}\\s]+", "");
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isDistinctiveSecondaryTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return isDistinctiveSecondaryTitle(value, normalizeTitle(value));
    }

    static boolean isDistinctiveSecondaryTitle(String value, String normalized) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String original = value.trim().replaceAll("[\\p{P}\\p{Z}\\s]+", "");
        return StringUtils.hasText(normalized)
                && !GENERIC_SECONDARY_TITLE.matcher(original).matches()
                && !GENERIC_SECONDARY_TITLE.matcher(normalized).matches();
    }

    /** Splits a release-ready alias list into individually comparable secondary titles. */
    static java.util.List<String> splitAliases(String aliasTitle) {
        if (!StringUtils.hasText(aliasTitle)) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(aliasTitle.split("[,，、/|;；]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    static Integer parseSeason(String value) {
        Matcher matcher = SEASON_PATTERN.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException invalidSeason) {
                return null;
            }
        }
        return parseChineseNumber(token);
    }

    private static Integer parseChineseNumber(String token) {
        int result = 0;
        int current = 0;
        for (char c : token.toCharArray()) {
            if (CHINESE_DIGITS.containsKey(c)) {
                current = CHINESE_DIGITS.get(c);
            } else if (c == '十') {
                result += current == 0 ? 10 : current * 10;
                current = 0;
            } else if (c == '百') {
                result += current == 0 ? 100 : current * 100;
                current = 0;
            }
        }
        int parsed = result + current;
        return parsed == 0 && !"零".equals(token) ? null : parsed;
    }

    /**
     * Conservative discriminator for event titles. Retains season, geographical qualifiers and
     * participants: month/day plus a league suffix is not a fixture identity. Equal keys are only
     * compatible evidence, never proof that two events are the same.
     */
    static Optional<String> eventIdentityKey(String value) {
        if (!StringUtils.hasText(value) || !EVENT_MARKER.matcher(value).find()) {
            return Optional.empty();
        }
        Matcher date = EVENT_DATE.matcher(value);
        if (!date.find()) {
            return Optional.empty();
        }
        String details = normalizeTitle(EVENT_DATE.matcher(value).replaceFirst(" "));
        return Optional.of("event:" + Integer.parseInt(date.group(1)) + "-" + Integer.parseInt(date.group(2))
                + "|" + details);
    }
}
