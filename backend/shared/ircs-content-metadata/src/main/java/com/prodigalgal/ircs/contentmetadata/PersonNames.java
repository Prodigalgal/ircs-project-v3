package com.prodigalgal.ircs.contentmetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative cleanup and splitting for person-name fields only. */
public final class PersonNames {

    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern HTML_BLOCK = Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern HTML_TAG = Pattern.compile("(?s)<[A-Za-z!/][^>]*>");
    private static final Pattern HTML_ENTITY = Pattern.compile(
            "&(#x[0-9a-fA-F]+|#\\d+|amp|nbsp|quot|apos|lt|gt);", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATORS = Pattern.compile("[,，、;；|/]+");
    private static final Pattern AMPERSAND = Pattern.compile("\\s*[&＆]\\s*");
    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "-", "--", "0", "n/a", "na", "null", "none", "undefined", "unknown",
            "内详", "未知", "不详", "暂无", "佚名", "无", "待定");
    private static final int MAX_NAME_LENGTH = 255;

    private PersonNames() {
    }

    /** Cleans, splits, filters, and de-duplicates names while preserving their input order. */
    public static Set<String> normalize(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = normalizeText(value);
            if (cleaned.isEmpty() || PLACEHOLDERS.contains(cleaned.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String part : SEPARATORS.split(cleaned)) {
                for (String name : splitAsianAmpersand(part)) {
                    addIfUsable(names, name);
                }
            }
        }
        return Collections.unmodifiableSet(names);
    }

    /** Removes markup and normalizes whitespace without changing name spelling or transliterating. */
    public static String normalizeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String decoded = decodeEntities(value);
        decoded = HTML_BLOCK.matcher(decoded).replaceAll(" ");
        decoded = HTML_TAG.matcher(HTML_COMMENT.matcher(decoded).replaceAll(" ")).replaceAll(" ");
        StringBuilder normalized = new StringBuilder(decoded.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < decoded.length();) {
            int codePoint = decoded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint >= 0xFF01 && codePoint <= 0xFF5E) {
                codePoint -= 0xFEE0;
            }
            if (isSpace(codePoint) || Character.isISOControl(codePoint)) {
                pendingSpace = normalized.length() > 0;
            } else {
                if (pendingSpace) {
                    normalized.append(' ');
                    pendingSpace = false;
                }
                normalized.appendCodePoint(codePoint);
            }
        }
        return normalized.toString();
    }

    private static List<String> splitAsianAmpersand(String value) {
        String[] candidates = AMPERSAND.split(value, -1);
        if (candidates.length < 2) {
            return List.of(value);
        }
        for (String candidate : candidates) {
            if (!containsCjk(candidate)) {
                return List.of(value);
            }
        }
        List<String> parts = new ArrayList<>(candidates.length);
        Collections.addAll(parts, candidates);
        return parts;
    }

    private static void addIfUsable(Set<String> names, String value) {
        String name = normalizeText(value);
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH
                || PLACEHOLDERS.contains(name.toLowerCase(Locale.ROOT))
                || name.chars().allMatch(character -> character == '0')) {
            return;
        }
        names.add(name);
    }

    private static String decodeEntities(String value) {
        Matcher matcher = HTML_ENTITY.matcher(value);
        StringBuilder decoded = new StringBuilder(value.length());
        int previousEnd = 0;
        while (matcher.find()) {
            decoded.append(value, previousEnd, matcher.start());
            decoded.append(decodeEntity(matcher.group(1)));
            previousEnd = matcher.end();
        }
        decoded.append(value, previousEnd, value.length());
        return decoded.toString();
    }

    private static String decodeEntity(String entity) {
        String lower = entity.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "amp" -> "&";
            case "nbsp" -> "\u00a0";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "lt" -> "<";
            case "gt" -> ">";
            default -> decodeNumericEntity(entity);
        };
    }

    private static String decodeNumericEntity(String entity) {
        try {
            int codePoint = entity.startsWith("#x") || entity.startsWith("#X")
                    ? Integer.parseInt(entity.substring(2), 16)
                    : Integer.parseInt(entity.substring(1));
            if (!Character.isValidCodePoint(codePoint)
                    || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
                return "";
            }
            return new String(Character.toChars(codePoint));
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL;
        });
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                || codePoint == '\u00a0' || codePoint == '\u3000';
    }
}
