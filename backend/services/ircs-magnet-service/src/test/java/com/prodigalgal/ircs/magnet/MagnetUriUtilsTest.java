package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MagnetUriUtilsTest {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000";
    private static final String BASE32_ZERO_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ONES_HASH = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private static final String BASE32_ONES_HASH = "77777777777777777777777777777777";

    @Test
    void decodesBase32InfoHashToCanonicalHex() {
        assertEquals(ZERO_HASH, MagnetUriUtils.normalizeInfoHash(BASE32_ZERO_HASH).orElseThrow());
        assertEquals(ONES_HASH, MagnetUriUtils.normalizeInfoHash(BASE32_ONES_HASH.toLowerCase()).orElseThrow());
        assertEquals(ZERO_HASH, MagnetUriUtils.normalizeInfoHash(ZERO_HASH.toLowerCase()).orElseThrow());
    }

    @Test
    void extractsPercentEncodedXtAndAllowsHybridV1AndV2Topics() {
        assertEquals(
                ZERO_HASH,
                MagnetUriUtils.normalizeInfoHash("magnet:?%78%74=urn%3Abtih%3A" + BASE32_ZERO_HASH
                        + "&xt=urn:btmh:1220" + "0".repeat(64)).orElseThrow());
    }

    @Test
    void rejectsInvalidCharactersLengthsAndArbitraryText() {
        assertFalse(MagnetUriUtils.normalizeInfoHash("A".repeat(31)).isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash("A".repeat(33)).isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash("A".repeat(39) + "G").isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash("0".repeat(40) + "0").isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash("prefix " + ZERO_HASH).isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash("magnet:?xt=urn:btih:" + ZERO_HASH + "G").isPresent());
    }

    @Test
    void rejectsMagnetUriWithConflictingBtihTopics() {
        assertFalse(MagnetUriUtils.normalizeInfoHash(
                "magnet:?xt=urn:btih:" + ZERO_HASH + "&xt=urn:btih:" + ONES_HASH).isPresent());
        assertFalse(MagnetUriUtils.normalizeInfoHash(
                "magnet:?xt=urn:btih:" + ZERO_HASH + "&xt=urn:btih:" + BASE32_ONES_HASH).isPresent());
        assertEquals(ZERO_HASH, MagnetUriUtils.normalizeInfoHash(
                "magnet:?xt=urn:btih:" + ZERO_HASH + "&xt=urn:btih:" + BASE32_ZERO_HASH).orElseThrow());
    }

    @Test
    void preservesMagnetUriBuilderEncodingContractWithCanonicalHash() {
        assertEquals(
                "magnet:?xt=urn:btih:" + ZERO_HASH + "&dn=Codex%20Movie%202026",
                MagnetUriUtils.buildMagnetUri(BASE32_ZERO_HASH, "Codex Movie 2026"));
        assertThrows(IllegalArgumentException.class, () -> MagnetUriUtils.buildMagnetUri("not-a-hash", "title"));
    }
}
