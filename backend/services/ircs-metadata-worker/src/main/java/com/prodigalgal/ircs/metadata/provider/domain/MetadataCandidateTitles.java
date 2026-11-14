package com.prodigalgal.ircs.metadata.provider.domain;

import com.prodigalgal.ircs.contracts.metadata.MetadataSearchContext;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Title evidence shared by providers; a prefix alone cannot identify a work or season. */
public final class MetadataCandidateTitles {

    private static final Pattern SEASON = Pattern.compile(
            "第\\s*([0-9零一二三四五六七八九十两]+)\\s*季|\\bseason\\s*(\\d+)\\b|\\bs(\\d{1,3})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELEASE_LABEL = Pattern.compile(
            "(?:正片|高清|超清|蓝光|标清|完整版|国语|粤语|中字|中文字幕|HD|BD|SD|4K|8K|HDR|\\d{3,4}P)+",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_ALIAS_KEYS = 8;

    private MetadataCandidateTitles() {}

    public static boolean matches(MetadataSearchContext source, String... candidateTitles) {
        Integer expectedSeason = source.getSeason();
        if (expectedSeason == null || expectedSeason < 0) {
            expectedSeason = season(source.getFullTitle());
        }
        Set<String> sourceTitles = sourceTitles(source);
        for (String candidate : candidateTitles) {
            Integer candidateSeason = season(candidate);
            if (expectedSeason != null && candidateSeason != null && !expectedSeason.equals(candidateSeason)) {
                return false;
            }
        }
        for (String candidate : candidateTitles) {
            if (sourceTitles.stream().anyMatch(title -> equalsTitle(candidate, title))) {
                return true;
            }
            if (expectedSeason != null && expectedSeason.equals(season(candidate))) {
                String baseTitle = SEASON.matcher(candidate).replaceAll("");
                if (sourceTitles.stream().anyMatch(title -> equalsTitle(baseTitle, title))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> sourceTitles(MetadataSearchContext source) {
        Set<String> titles = new LinkedHashSet<>();
        titles.add(source.getFullTitle());
        String subtitle = normalize(source.getSubtitle());
        boolean identitySubtitle = !subtitle.isEmpty() && !RELEASE_LABEL.matcher(subtitle).matches()
                && !normalize(SEASON.matcher(source.getSubtitle()).replaceAll("")).isEmpty();
        // A distinctive subtitle identifies a different work; the bare parent name is insufficient.
        if (!identitySubtitle || normalize(source.getTitle()).contains(subtitle)) {
            titles.add(source.getTitle());
        }
        if (source.getAliasTitle() != null && !source.getAliasTitle().isBlank()) {
            titles.add(source.getAliasTitle());
            String[] aliases = source.getAliasTitle().split("[/|;；]+", MAX_ALIAS_KEYS + 1);
            for (int index = 0; index < Math.min(aliases.length, MAX_ALIAS_KEYS); index++) {
                if (!aliases[index].isBlank()) {
                    titles.add(aliases[index].trim());
                }
            }
        }
        return titles;
    }

    private static boolean equalsTitle(String left, String right) {
        String normalized = normalize(left);
        return !normalized.isEmpty() && normalized.equals(normalize(right));
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    private static Integer season(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = SEASON.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC));
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.group(1) != null ? matcher.group(1)
                : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        try {
            return Integer.valueOf(number);
        } catch (NumberFormatException ignored) {
            String digits = "零一二三四五六七八九";
            number = number.replace('两', '二');
            int ten = number.indexOf('十');
            if (ten >= 0) {
                int tens = ten == 0 ? 1 : digits.indexOf(number.charAt(0));
                int units = ten == number.length() - 1 ? 0 : digits.indexOf(number.charAt(ten + 1));
                return tens > 0 && units >= 0 ? tens * 10 + units : null;
            }
            return number.length() == 1 && digits.indexOf(number.charAt(0)) >= 0
                    ? digits.indexOf(number.charAt(0)) : null;
        }
    }
}
