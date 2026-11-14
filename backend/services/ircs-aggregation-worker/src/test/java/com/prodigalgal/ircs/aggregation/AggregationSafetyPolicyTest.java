package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AggregationSafetyPolicyTest {

    private final AggregationSafetyPolicy policy = new AggregationSafetyPolicy(false, "douban:123,imdb:tt0000000");

    @Test
    void rejectedAndPlaceholderIdsNeverProvideIdentityEvidence() {
        for (String value : new String[] {"0", "0.0", "000", "null", "unknown", "N/A", "-", "123"}) {
            assertFalse(policy.isUsableExternalId("douban", value));
        }
        assertFalse(policy.isUsableExternalId("imdb", "tt0000000"));
        assertTrue(policy.isUsableExternalId("tmdb", "123"));
        assertTrue(policy.rejectedExternalIdValues("douban").contains("123"));
        assertFalse(policy.rejectedExternalIdValues("tmdb").contains("123"));
        assertTrue(policy.rejectedExternalIdValues("imdb").contains("tt0000000"));
        assertTrue(policy.rejectedExternalIdValues("tmdb").contains("unknown"));
    }

    @Test
    void providerNamesAreExplicitAndIdentifiersAreNotNumericallyRewritten() {
        assertTrue(policy.isUsableExternalId(" IMDB ", " tt0133093 "));
        assertTrue(policy.isUsableExternalId("douban", "00123"));
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
}
