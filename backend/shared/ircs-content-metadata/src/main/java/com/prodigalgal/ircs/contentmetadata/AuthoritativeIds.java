package com.prodigalgal.ircs.contentmetadata;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Format-only normalization for external catalog identifiers. */
public final class AuthoritativeIds {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "-", "0", "0.0", "-1", "null", "none", "undefined", "unknown", "n/a", "na",
            "无", "暂无", "未知", "未填写", "不详", "内详", "佚名");
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+");
    private static final Pattern IMDB = Pattern.compile("tt[0-9]{7,10}");
    private static final Pattern ROTTEN_TOMATOES_SLUG = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,49}");

    private AuthoritativeIds() {
    }

    /** Returns a positive decimal Douban ID without leading zeroes, or {@code null}. */
    public static String normalizeDouban(String value) {
        return normalizePositiveDecimalId(value);
    }

    /** Returns a positive decimal TMDB ID without leading zeroes, or {@code null}. */
    public static String normalizeTmdb(String value) {
        return normalizePositiveDecimalId(value);
    }

    /** Returns a canonical lower-case IMDb title ID while preserving its numeric zeroes. */
    public static String normalizeImdb(String value) {
        String normalized = trimUnicodeSpace(value);
        if (normalized == null || isPlaceholder(normalized)) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!IMDB.matcher(normalized).matches() || normalized.substring(2).chars().allMatch(character -> character == '0')) {
            return null;
        }
        return normalized;
    }

    /** Returns a Rotten Tomatoes movie/series path slug, or {@code null}. */
    public static String normalizeRottenTomatoes(String value) {
        String normalized = trimUnicodeSpace(value);
        if (normalized == null || isPlaceholder(normalized) || normalized.length() > 50
                || !ROTTEN_TOMATOES_SLUG.matcher(normalized).matches()) {
            return null;
        }
        if (DECIMAL.matcher(normalized).matches() && normalized.chars().allMatch(character -> character == '0')) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizePositiveDecimalId(String value) {
        String normalized = trimUnicodeSpace(value);
        if (normalized == null || !DECIMAL.matcher(normalized).matches()) {
            return null;
        }
        int firstNonZero = 0;
        while (firstNonZero < normalized.length() && normalized.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        if (firstNonZero == normalized.length()) {
            return null;
        }
        String significantDigits = normalized.substring(firstNonZero);
        if (significantDigits.length() > 19) {
            return null;
        }
        try {
            long parsed = Long.parseLong(significantDigits);
            return parsed > 0 ? Long.toString(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isPlaceholder(String value) {
        return PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT)) || value.matches("[+-]?0+(?:[.]0+)?");
    }

    private static String trimUnicodeSpace(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = value.codePointBefore(end);
            if (!isSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
