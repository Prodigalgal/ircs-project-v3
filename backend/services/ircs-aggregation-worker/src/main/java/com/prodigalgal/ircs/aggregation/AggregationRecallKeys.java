package com.prodigalgal.ircs.aggregation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.util.StringUtils;

/** Bounded, operation-local recall keys. A bucket is candidate evidence, never an identity. */
final class AggregationRecallKeys {

    private static final int MAX_TITLE_KEYS = 6;
    private static final int MAX_PEOPLE_KEYS = 8;
    private static final Set<String> UNKNOWN_NAMES = Set.of(
            "unknown", "null", "none", "n/a", "0", "-", "未知", "不详", "暂无", "无", "待定");

    private AggregationRecallKeys() {
    }

    static String completeTitle(String title, String subtitle) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String main = title.trim();
        if (!AggregationTitleNormalizer.isDistinctiveSecondaryTitle(subtitle)) {
            return main;
        }
        String mainKey = AggregationTitleNormalizer.normalizeTitle(main);
        String subtitleKey = AggregationTitleNormalizer.normalizeTitle(subtitle);
        return mainKey.contains(subtitleKey) ? main : main + " " + subtitle.trim();
    }

    static List<String> titles(RawVideoAggregationRecord raw) {
        LinkedHashMap<String, String> titles = new LinkedHashMap<>();
        addTitle(titles, completeTitle(raw.title(), raw.subtitle()));
        addTitle(titles, raw.title());
        for (String alias : AggregationTitleNormalizer.splitAliases(raw.aliasTitle())) {
            if (AggregationTitleNormalizer.isDistinctiveSecondaryTitle(alias)) {
                addTitle(titles, alias);
            }
            if (titles.size() >= MAX_TITLE_KEYS) {
                break;
            }
        }
        return List.copyOf(titles.values());
    }

    static List<String> people(Set<String> values) {
        TreeSet<String> normalized = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value) && !UNKNOWN_NAMES.contains(value.trim().toLowerCase(java.util.Locale.ROOT))) {
                    normalized.add(value.trim());
                }
            }
        }
        return normalized.stream().limit(MAX_PEOPLE_KEYS).toList();
    }

    private static void addTitle(LinkedHashMap<String, String> titles, String value) {
        if (StringUtils.hasText(value) && titles.size() < MAX_TITLE_KEYS) {
            String key = AggregationTitleNormalizer.normalizeTitle(value);
            if (StringUtils.hasText(key)) {
                titles.putIfAbsent(key, value.trim());
            }
        }
    }
}
