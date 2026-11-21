package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.contentmetadata.AuthoritativeIds;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
final class AggregationSafetyPolicy {

    private final boolean automaticUnifiedMergeEnabled;
    private final Set<String> rejectedExternalIds;

    AggregationSafetyPolicy(
            @Value("${app.aggregation.auto-unified-merge.enabled:false}") boolean automaticUnifiedMergeEnabled,
            @Value("${app.aggregation.identity.rejected-external-ids:douban:123,douban:456}") String rejectedExternalIds) {
        this.automaticUnifiedMergeEnabled = automaticUnifiedMergeEnabled;
        this.rejectedExternalIds = Arrays.stream(rejectedExternalIds == null ? new String[0] : rejectedExternalIds.split(","))
                .map(AggregationSafetyPolicy::normalizeRejectedId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean automaticUnifiedMergeEnabled() {
        return automaticUnifiedMergeEnabled;
    }

    Set<String> rejectedExternalIdValues(String provider) {
        String prefix = (provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT)) + ":";
        return rejectedExternalIds.stream()
                .filter(value -> value.startsWith(prefix)).map(value -> value.substring(prefix.length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Syntactic usability only; this does not verify the upstream identity or its entity granularity. */
    boolean isUsableExternalId(String provider, String value) {
        return normalizeExternalId(provider, value) != null;
    }

    boolean isRejectedExternalId(String provider, String value) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String canonical = canonicalExternalId(normalizedProvider, value);
        return canonical != null && rejectedExternalIds.contains(normalizedProvider + ":" + canonical);
    }

    String normalizeExternalId(String provider, String value) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        String canonical = canonicalExternalId(normalizedProvider, value);
        return canonical != null && !rejectedExternalIds.contains(normalizedProvider + ":" + canonical) ? canonical : null;
    }

    private static String canonicalExternalId(String provider, String value) {
        return switch (provider) {
            case "douban" -> AuthoritativeIds.normalizeDouban(value);
            case "tmdb" -> AuthoritativeIds.normalizeTmdb(value);
            case "imdb" -> AuthoritativeIds.normalizeImdb(value);
            case "rt" -> AuthoritativeIds.normalizeRottenTomatoes(value);
            default -> null;
        };
    }

    private static String normalizeRejectedId(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator < 0) { return normalized; }
        String canonical = canonicalExternalId(normalized.substring(0, separator), normalized.substring(separator + 1));
        return canonical == null ? normalized : normalized.substring(0, separator) + ":" + canonical;
    }

    static boolean categoriesCompatible(String left, String right) {
        String normalizedLeft = canonicalCategory(left);
        String normalizedRight = canonicalCategory(right);
        // Missing classification is not evidence of a different work. Known conflicting buckets are.
        if (normalizedLeft == null || normalizedRight == null || normalizedLeft.equals(normalizedRight)) { return true; }
        AggregationCategoryProfile leftProfile = AggregationCategoryProfile.from(normalizedLeft);
        AggregationCategoryProfile rightProfile = AggregationCategoryProfile.from(normalizedRight);
        return leftProfile != AggregationCategoryProfile.OTHER && leftProfile == rightProfile;
    }

    private static String canonicalCategory(String value) {
        String normalized = category(value);
        return "其他".equals(normalized) ? "other" : normalized;
    }

    private static String category(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Set.of("unknown_category", "unknown", "未知", "未分类").contains(normalized) ? null : normalized;
    }
}
