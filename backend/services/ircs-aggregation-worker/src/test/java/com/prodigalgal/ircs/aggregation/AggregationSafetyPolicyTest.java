package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AggregationSafetyPolicyTest {

    private final AggregationSafetyPolicy policy = new AggregationSafetyPolicy(false, "douban:123,douban:456,imdb:tt0000000");

    @Test
    void rejectedAndPlaceholderIdsNeverProvideIdentityEvidence() {
        for (String value : new String[] {"0", "0.0", "000", "null", "unknown", "N/A", "-", "123", "456"}) {
            assertFalse(policy.isUsableExternalId("douban", value));
        }
        assertFalse(policy.isUsableExternalId("imdb", "tt0000000"));
        assertTrue(policy.isUsableExternalId("tmdb", "123"));
        assertTrue(policy.rejectedExternalIdValues("douban").contains("123"));
        assertTrue(policy.rejectedExternalIdValues("douban").contains("456"));
        assertFalse(policy.rejectedExternalIdValues("tmdb").contains("123"));
        assertTrue(policy.rejectedExternalIdValues("imdb").contains("tt0000000"));
        assertFalse(policy.isUsableExternalId("tmdb", "unknown"));
    }

    @Test
    void providerNamesAreExplicitAndCanonicalIdentifiersRespectTheDenylist() {
        assertTrue(policy.isUsableExternalId(" IMDB ", " tt0133093 "));
        assertFalse(policy.isUsableExternalId("douban", "00123"));
        assertTrue(policy.isUsableExternalId("tmdb", "00123"));
        assertTrue("123".equals(policy.normalizeExternalId("tmdb", "00123")));
        assertFalse(policy.isUsableExternalId("unrecognized", "1234567"));
        assertFalse(policy.isUsableExternalId(null, "1234567"));
        assertFalse(policy.automaticUnifiedMergeEnabled());
    }

    @Test
    void missingClassificationDoesNotBridgeConflictingKnownCategories() {
        assertTrue(AggregationSafetyPolicy.categoriesCompatible("UNKNOWN_CATEGORY", "电影"));
        assertTrue(AggregationSafetyPolicy.categoriesCompatible(null, "电影"));
        assertFalse(AggregationSafetyPolicy.categoriesCompatible("电影", "电视剧"));
    }

    @Test
    void standardOtherCategoryAliasesAreCompatibleInEitherDirection() {
        assertTrue(AggregationSafetyPolicy.categoriesCompatible("other", "其他"));
        assertTrue(AggregationSafetyPolicy.categoriesCompatible("其他", "other"));
    }

    @Test
    void otherCategoryDoesNotBridgeKnownOrUnmappedCategories() {
        assertFalse(AggregationSafetyPolicy.categoriesCompatible("other", "体育"));
        assertFalse(AggregationSafetyPolicy.categoriesCompatible("体育", "其他"));
        assertFalse(AggregationSafetyPolicy.categoriesCompatible("other", "unmapped_genre"));
        assertFalse(AggregationSafetyPolicy.categoriesCompatible("unmapped_genre", "另一个未映射类别"));
    }
}
