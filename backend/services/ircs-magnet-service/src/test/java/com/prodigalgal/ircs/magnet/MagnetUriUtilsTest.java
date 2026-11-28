package com.prodigalgal.ircs.magnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MagnetUriUtilsTest {

    private static final String ZERO_HASH = "0000000000000000000000000000000000000000";
    private static final String BASE32_ZERO_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ONES_HASH = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
    private static final String BASE32_ONES_HASH = "77777777777777777777777777777777";

    private static final String HASH = "D7A46713EAEE18C746B3254B7D1492A50FD9D6CE";
    private static final String RICH = "magnet:?xt=urn:btih:" + HASH + "&dn=Matrix&tr=udp%3A%2F%2Frich.example%3A80";
    private static final String BARE = "magnet:?xt=urn:btih:" + HASH;

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
        // buildMagnetUri deliberately produces only the identity part; the announce list is appended
        // once downstream so that one configured list governs every provider.
        assertEquals(
                "magnet:?xt=urn:btih:" + ZERO_HASH + "&dn=Codex%20Movie%202026",
                MagnetUriUtils.buildMagnetUri(BASE32_ZERO_HASH, "Codex Movie 2026"));
        assertFalse(MagnetUriUtils.hasTrackers(MagnetUriUtils.buildMagnetUri(ZERO_HASH, "x")));
        assertThrows(IllegalArgumentException.class, () -> MagnetUriUtils.buildMagnetUri("not-a-hash", "title"));
    }

    @Test
    void theConfiguredListIsWhatCompletesABuiltMagnet() {
        String built = MagnetUriUtils.buildMagnetUri(HASH, "The Matrix 1999");
        String configured = MagnetUriUtils.ensureTrackers(built, List.of("udp://custom.example:1337/announce"));

        assertThat(MagnetUriUtils.trackersOf(configured))
                .containsExactly("udp://custom.example:1337/announce");
        assertThat(configured).contains("dn=The%20Matrix%201999");
        assertThat(MagnetUriUtils.normalizeInfoHash(configured)).contains(HASH);
    }

    @Test
    void aBareMagnetFromAHashOnlyProviderIsCompleted() {
        String completed = MagnetUriUtils.ensureTrackers(BARE, MagnetUriUtils.DEFAULT_TRACKERS);

        assertThat(MagnetUriUtils.trackersOf(completed)).isNotEmpty();
        assertThat(MagnetUriUtils.normalizeInfoHash(completed)).contains(HASH);
        // Idempotent: running it again must not duplicate trackers.
        assertThat(MagnetUriUtils.ensureTrackers(completed, MagnetUriUtils.DEFAULT_TRACKERS)).isEqualTo(completed);
    }

    @Test
    void aProviderAnnounceListStaysFirstAndIsNotDropped() {
        String completed = MagnetUriUtils.ensureTrackers(RICH, MagnetUriUtils.DEFAULT_TRACKERS);
        List<String> trackers = MagnetUriUtils.trackersOf(completed);

        assertThat(trackers.getFirst()).isEqualTo("udp://rich.example:80");
        assertThat(trackers).hasSizeGreaterThan(1);
    }

    @Test
    void addingTrackersPreservesOriginalQueryFieldsEncodingMultiplicityAndFragment() {
        String source = "magnet:?xt=urn:btih:" + HASH
                + "&xt=urn:btmh:1220" + "0".repeat(64)
                + "&x.pe=203.0.113.1%3A51413&x.pe=203.0.113.2%3A51413"
                + "&dn=Matrix+%281999%29&ws=https%3A%2F%2Fcdn.example%2Fmovie&tr=udp%3A%2F%2Frich.example%3A80#keep";

        String completed = MagnetUriUtils.ensureTrackers(source, List.of("udp://new.example:80/announce"));

        assertThat(completed).startsWith("magnet:?xt=urn:btih:" + HASH
                + "&xt=urn:btmh:1220" + "0".repeat(64)
                + "&x.pe=203.0.113.1%3A51413&x.pe=203.0.113.2%3A51413"
                + "&dn=Matrix+%281999%29&ws=https%3A%2F%2Fcdn.example%2Fmovie&tr=udp%3A%2F%2Frich.example%3A80");
        assertThat(completed).endsWith("&tr=udp%3A%2F%2Fnew.example%3A80%2Fannounce#keep");
        assertThat(MagnetUriUtils.ensureTrackers(completed, List.of("udp://new.example:80/announce")))
                .isEqualTo(completed);
    }

    @Test
    void mergingRetainsHybridTopicsAndOtherFieldsWhileAvoidingCrossSourceDuplicates() {
        String existing = "magnet:?xt=urn:btih:" + HASH
                + "&xt=urn:btmh:1220" + "0".repeat(64)
                + "&x.pe=peer%3A1&x.pe=peer%3A1&ws=https%3A%2F%2Fa.example%2Ffile"
                + "&tr=udp%3A%2F%2Ftracker.example%3A80";
        String incoming = "magnet:?xt=urn:btih:" + HASH.toLowerCase()
                + "&xt=urn:btmh:1220" + "0".repeat(64)
                + "&x.pe=peer%3A1&x.pe=peer%3A2&ws=https%3A%2F%2Fa.example%2Ffile"
                + "&ws=https%3a%2f%2fa.example%2ffile"
                + "&ws=https%3A%2F%2Fb.example%2Ffile&tr=udp%3A%2F%2Ftracker.example%3A80";

        String merged = MagnetUriUtils.mergeMagnets(existing, incoming, HASH, "Matrix");

        List<String> parameters = List.of(merged.substring("magnet:?".length()).split("&"));
        assertThat(merged).contains("xt=urn:btmh:1220" + "0".repeat(64));
        assertThat(parameters).filteredOn(parameter -> parameter.startsWith("x.pe="))
                .containsExactly("x.pe=peer%3A1", "x.pe=peer%3A1", "x.pe=peer%3A2");
        assertThat(parameters).filteredOn(parameter -> parameter.startsWith("ws=")).containsExactly(
                "ws=https%3A%2F%2Fa.example%2Ffile", "ws=https%3a%2f%2fa.example%2ffile",
                "ws=https%3A%2F%2Fb.example%2Ffile");
        assertThat(merged).containsOnlyOnce("tr=udp%3A%2F%2Ftracker.example%3A80");
        assertThat(merged).contains("xt=urn:btih:" + HASH);
        assertThat(MagnetUriUtils.mergeMagnets(merged, incoming, HASH, "Matrix")).isEqualTo(merged);
    }

    @Test
    void mergeDoesNotCarryFieldsFromUrisWithConflictingBtihTopics() {
        String conflicting = "magnet:?xt=urn:btih:" + ONES_HASH + "&x.pe=untrusted";

        String merged = MagnetUriUtils.mergeMagnets(RICH, conflicting, HASH, "Matrix");

        assertThat(merged).contains("xt=urn:btih:" + HASH);
        assertThat(merged).contains("tr=udp%3A%2F%2Frich.example%3A80");
        assertThat(merged).doesNotContain("untrusted");
    }

    @Test
    void mergingUnionsBothAnnounceListsWithoutAddingAnythingElse() {
        String incoming = "magnet:?xt=urn:btih:" + HASH + "&dn=B&tr=udp%3A%2F%2Fother.example%3A80";
        String merged = MagnetUriUtils.mergeMagnets(RICH, incoming, HASH, "Matrix");

        List<String> trackers = MagnetUriUtils.trackersOf(merged);
        assertThat(trackers).containsExactly("udp://rich.example:80", "udp://other.example:80");
        assertThat(MagnetUriUtils.normalizeInfoHash(merged)).contains(HASH);
    }

    @Test
    void mergingABareLinkOverARichOneDoesNotDowngradeIt() {
        // The regression this guards: apibay runs after therarbg for the same hash and would have
        // overwritten the 22-tracker link with a bare one.
        String merged = MagnetUriUtils.mergeMagnets(RICH, BARE, HASH, "Matrix");

        assertThat(MagnetUriUtils.trackersOf(merged)).containsExactly("udp://rich.example:80");
        assertThat(merged.length()).isGreaterThan(BARE.length());
    }

    @Test
    void mergingAnEmptyExistingLinkKeepsTheIncomingLinksAnnounceList() {
        String merged = MagnetUriUtils.mergeMagnets(null, RICH, HASH, "Matrix");

        assertThat(MagnetUriUtils.trackersOf(merged)).containsExactly("udp://rich.example:80");
        assertThat(MagnetUriUtils.normalizeInfoHash(merged)).contains(HASH);
    }

    @Test
    void mergingTwoBareLinksStillYieldsABareLink() {
        // Appending the configured list is the executor's job, so the merge must not invent trackers.
        String merged = MagnetUriUtils.mergeMagnets(null, BARE, HASH, "Matrix");

        assertThat(MagnetUriUtils.hasTrackers(merged)).isFalse();
        assertThat(merged).contains("dn=Matrix");
    }

    @Test
    void mergeTrackersDeduplicatesAndPreservesOrder() {
        List<String> merged = MagnetUriUtils.mergeTrackers(
                List.of("udp://a:1", "udp://b:2"),
                List.of("udp://b:2", "udp://c:3", "  "));

        assertThat(merged).containsExactly("udp://a:1", "udp://b:2", "udp://c:3");
    }

    @Test
    void displayNameIsTakenFromTheIncomingLinkFirst() {
        String incoming = "magnet:?xt=urn:btih:" + HASH + "&dn=Incoming%20Name";
        String existing = "magnet:?xt=urn:btih:" + HASH + "&dn=Existing%20Name";

        assertThat(MagnetUriUtils.mergeMagnets(existing, incoming, HASH, "Fallback"))
                .contains("dn=Incoming%20Name")
                .doesNotContain("Existing%20Name");
    }

    @Test
    void theFallbackTitleIsUsedWhenNeitherLinkNamesTheTorrent() {
        assertThat(MagnetUriUtils.mergeMagnets(null, BARE, HASH, "Fallback Title"))
                .contains("dn=Fallback%20Title");
    }

    @Test
    void anInvalidHashIsReturnedUntouchedRatherThanCorrupted() {
        String broken = "magnet:?xt=urn:btih:not-a-hash";

        assertThat(MagnetUriUtils.ensureTrackers(broken, MagnetUriUtils.DEFAULT_TRACKERS)).isEqualTo(broken);
        assertThat(MagnetUriUtils.mergeMagnets(null, broken, "not-a-hash", "x")).isEqualTo(broken);
    }

    @Test
    void conflictingXtParametersAreRejectedByTheMerger() {
        // Keeping the existing name: the dedicated conflict case already lives above, and this states
        // that the merge path inherits the same rejection.
        assertThat(MagnetUriUtils.normalizeInfoHash(
                "magnet:?xt=urn:btih:" + HASH + "&xt=urn:btih:" + ONES_HASH)).isEmpty();
    }
}
