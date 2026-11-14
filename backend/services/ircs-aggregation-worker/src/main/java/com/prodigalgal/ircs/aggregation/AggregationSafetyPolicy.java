package com.prodigalgal.ircs.aggregation;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class AggregationSafetyPolicy {

    private static final Set<String> PLACEHOLDER_IDS = Set.of("0", "0.0", "null", "none", "unknown", "n/a", "-");
    private static final Set<String> PROVIDERS = Set.of("douban", "tmdb", "imdb", "rt");
    private final boolean automaticUnifiedMergeEnabled;
    private final Set<String> rejectedExternalIds;

    AggregationSafetyPolicy(
            @Value("${app.aggregation.auto-unified-merge.enabled:false}") boolean automaticUnifiedMergeEnabled,
            @Value("${app.aggregation.identity.rejected-external-ids:douban:123}") String rejectedExternalIds) {
        this.automaticUnifiedMergeEnabled = automaticUnifiedMergeEnabled;
        this.rejectedExternalIds = Arrays.stream(rejectedExternalIds == null ? new String[0] : rejectedExternalIds.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean automaticUnifiedMergeEnabled() {
        return automaticUnifiedMergeEnabled;
    }

    Set<String> rejectedExternalIdValues(String provider) {
        String prefix = (provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT)) + ":";
        return java.util.stream.Stream.concat(PLACEHOLDER_IDS.stream(), rejectedExternalIds.stream()
                        .filter(value -> value.startsWith(prefix)).map(value -> value.substring(prefix.length())))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Syntactic usability only; this does not verify the upstream identity or its entity granularity. */
    boolean isUsableExternalId(String provider, String value) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(value)) {
            return false;
        }
        String normalizedProvider = provider.trim().toLowerCase(Locale.ROOT);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.contains(normalizedProvider)
                && !PLACEHOLDER_IDS.contains(normalized)
                && !normalized.matches("0+(?:\\.0+)?")
                && !rejectedExternalIds.contains(normalizedProvider + ":" + normalized);
    }

    static boolean categoriesCompatible(String left, String right) {
        String normalizedLeft = category(left);
        String normalizedRight = category(right);
        // Missing classification is not evidence of a different work. Known conflicting buckets are.
        return normalizedLeft == null || normalizedRight == null || normalizedLeft.equals(normalizedRight);
    }

    private static String category(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Set.of("unknown_category", "unknown", "未知", "未分类").contains(normalized) ? null : normalized;
    }
}
