package com.prodigalgal.ircs.contentmetadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AuthoritativeIdsTest {
    @Test
    void placeholdersNeverBecomeAnIdentity() {
        for (String value : new String[]{null, "", " ", "0", "000", "0.0", "0.00", "-0", "-1", "NULL", "none", "undefined", "unknown", "N/A", "暂无"}) {
            assertNull(AuthoritativeIds.normalizeDouban(value));
            assertNull(AuthoritativeIds.normalizeTmdb(value));
            assertNull(AuthoritativeIds.normalizeImdb(value));
            assertNull(AuthoritativeIds.normalizeRottenTomatoes(value));
        }
        assertNull(AuthoritativeIds.normalizeImdb("tt0000000"));
    }

    @Test
    void numericIdentifiersHaveAConsistentBoundedCanonicalForm() {
        assertEquals("123", AuthoritativeIds.normalizeDouban("\u00a000123\u3000"));
        assertEquals("456", AuthoritativeIds.normalizeTmdb("00456"));
        assertEquals(Long.toString(Long.MAX_VALUE), AuthoritativeIds.normalizeTmdb(Long.toString(Long.MAX_VALUE)));
        for (String invalid : new String[]{"123x", "tmdb-1", "1.0", "+1", "1e5", "9223372036854775808", "https://example.invalid/123"}) {
            assertNull(AuthoritativeIds.normalizeTmdb(invalid));
        }
    }

    @Test
    void providerFormatsAreDistinctAndDoNotProveTheEntityExists() {
        assertEquals("tt0133093", AuthoritativeIds.normalizeImdb(" TT0133093 "));
        assertEquals("tt0000001", AuthoritativeIds.normalizeImdb("tt0000001"));
        assertNull(AuthoritativeIds.normalizeImdb("tt001"));
        assertEquals("the_matrix", AuthoritativeIds.normalizeRottenTomatoes("The_Matrix"));
        assertEquals("s01", AuthoritativeIds.normalizeRottenTomatoes("s01"));
        assertNull(AuthoritativeIds.normalizeRottenTomatoes("https://www.rottentomatoes.com/m/the_matrix"));
        assertNull(AuthoritativeIds.normalizeRottenTomatoes("m/the_matrix"));
    }
}
