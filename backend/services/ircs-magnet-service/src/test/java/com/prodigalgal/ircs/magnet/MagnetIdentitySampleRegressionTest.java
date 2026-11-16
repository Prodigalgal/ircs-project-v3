package com.prodigalgal.ircs.magnet;

import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.AUTO_APPROVE;
import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.CANDIDATE;
import static com.prodigalgal.ircs.magnet.MagnetCandidateIdentityPolicy.Disposition.REJECT;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MagnetIdentitySampleRegressionTest {

    @TestFactory
    List<DynamicTest> replaysSanitizedStoredSamplesWithoutInventingIndependentSources() throws IOException {
        JsonNode samples;
        try (InputStream resource = getClass().getResourceAsStream("/magnet-identity-samples.json")) {
            assertNotNull(resource, "sanitized identity samples must be on the test classpath");
            samples = new ObjectMapper().readTree(resource).path("samples");
        }
        assertEquals(18, samples.size(), "the fixed sample must not silently shrink");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode sample : samples) {
            tests.add(DynamicTest.dynamicTest(text(sample, "key"), () -> {
                JsonNode candidate = sample.path("candidate");
                assertEquals("STORED_CANDIDATE", text(candidate, "titleSource"));
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("titleSource", "STORED_CANDIDATE");
                String returnedImdbCode = text(candidate, "imdbCode");
                if (returnedImdbCode != null) {
                    // This is YTS's returned imdbCode, never queryValue or a legacy matched-ID echo.
                    evidence.put("returnedImdbId", returnedImdbCode);
                }
                var decision = MagnetCandidateIdentityPolicy.evaluate(
                        target(sample.path("target")), candidate(text(candidate, "title"), evidence));
                assertAll(
                        () -> assertEquals(text(sample, "expectedDisposition"), decision.disposition().name()),
                        () -> assertEquals(text(sample, "expectedReason"), decision.reason()));
            }));
        }
        return tests;
    }

    @Test
    void independentSourceTitleCanConfirmAliasAndYearButStoredTitleCannot() {
        var target = new MagnetCandidateIdentityPolicy.Target("银杏之城", null, "Ginkgo City", 2024,
                null, "movie", "tt1234567", null, null);
        String title = "Ginkgo City (2024) 1080p WEB-DL";

        // Synthetic source-contract controls; these are not observations about the stored snapshot.
        assertDecision(AUTO_APPROVE, "TITLE_AND_YEAR_OR_SEASON_CONFIRMED", target, title,
                Map.of("titleSource", "CANDIDATE"));
        assertDecision(CANDIDATE, "MISSING_INDEPENDENT_CANDIDATE_TITLE", target, title,
                Map.of("titleSource", "STORED_CANDIDATE"));
        assertDecision(CANDIDATE, "MISSING_INDEPENDENT_CANDIDATE_TITLE", target, title, Map.of());
    }

    @Test
    void correctCrossLanguageTitleNeedsIndependentIdOrKnownAlias() {
        var target = new MagnetCandidateIdentityPolicy.Target("银杏之城", null, null, 2024,
                null, "movie", "tt1234567", null, null);
        String title = "Ginkgo City (2024) 1080p WEB-DL";

        assertDecision(CANDIDATE, "TITLE_NOT_CONFIRMED", target, title,
                Map.of("titleSource", "CANDIDATE"));
        assertDecision(AUTO_APPROVE, "RETURNED_EXTERNAL_ID_CONFIRMED", target, title,
                Map.of("titleSource", "CANDIDATE", "returnedImdbId", "tt1234567"));
        assertDecision(REJECT, "RETURNED_EXTERNAL_ID_CONFLICT", target, title,
                Map.of("titleSource", "CANDIDATE", "returnedImdbId", "tt7654321"));
    }

    @Test
    void queryValueAndLegacyMatchedFieldsCannotStandInForReturnedIds() {
        var target = new MagnetCandidateIdentityPolicy.Target("银杏之城", null, null, 2024,
                null, "movie", "tt1234567", null, null);
        var decision = MagnetCandidateIdentityPolicy.evaluate(target,
                candidate("Ginkgo City (2024) 1080p WEB-DL", Map.of(
                        "titleSource", "CANDIDATE", "queryType", "IMDB", "queryValue", "tt1234567")));

        assertAll(
                () -> assertEquals(CANDIDATE, decision.disposition()),
                () -> assertEquals("TITLE_NOT_CONFIRMED", decision.reason()));
    }

    @Test
    void explicitSeasonConflictIsRejectedWithIndependentOrStoredTitleEvidence() {
        var target = new MagnetCandidateIdentityPolicy.Target("星河守望", null, "Star Watch", 2024,
                2, "series", "tt1234567", null, null);
        for (String source : List.of("CANDIDATE", "STORED_CANDIDATE")) {
            assertDecision(REJECT, "SEASON_CONFLICT", target, "Star Watch S03E02 1080p WEB-DL",
                    Map.of("titleSource", source, "returnedImdbId", "tt1234567"));
        }
    }

    private static MagnetCandidateIdentityPolicy.Target target(JsonNode target) {
        return new MagnetCandidateIdentityPolicy.Target(text(target, "title"), text(target, "subtitle"),
                text(target, "aliasTitle"), integer(target, "year"), integer(target, "season"),
                text(target, "categoryCode"), text(target, "imdbId"), text(target, "tmdbId"),
                text(target, "doubanId"));
    }

    private static void assertDecision(MagnetCandidateIdentityPolicy.Disposition expected, String expectedReason,
                                       MagnetCandidateIdentityPolicy.Target target, String title,
                                       Map<String, Object> evidence) {
        var decision = MagnetCandidateIdentityPolicy.evaluate(target, candidate(title, evidence));
        assertAll(
                () -> assertEquals(expected, decision.disposition()),
                () -> assertEquals(expectedReason, decision.reason()));
    }

    private static MagnetProviderCandidate candidate(String title, Map<String, Object> evidence) {
        // Transport fields are absent because this test exercises only the pure identity decision.
        // Deliberately high legacy match fields ensure they cannot grant identity by themselves.
        return new MagnetProviderCandidate(null, null, title, null, null, null, null, null, null, null,
                "IMDB", "tt1234567", 100, null, List.of(), evidence);
    }

    private static String text(JsonNode object, String field) {
        return object.path(field).asText(null);
    }

    private static Integer integer(JsonNode object, String field) {
        JsonNode value = object.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }
}
