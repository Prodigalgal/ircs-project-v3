package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GenericMagnetCandidateParserTest {
    private static final String HASH = "ABCDEF1234567890ABCDEF1234567890ABCDEF12";
    private final GenericMagnetCandidateParser parser = new GenericMagnetCandidateParser(new ObjectMapper());
    private final MagnetExternalIdQuery query = new MagnetExternalIdQuery("IMDB", "tt1234567");
    private final MagnetProviderSummary provider = new MagnetProviderSummary(UUID.randomUUID(), "test", "test", "EZTV",
            "https://example.invalid", true, 1, "HIGH", List.of("IMDB"), 0, 0, 1000, 20, true,
            null, null, null, null, null, null);

    @Test
    void returnsIndependentIdsInsteadOfCopyingTheImdbQueryIntoEvidence() throws Exception {
        var candidates = parser.parse(provider, query, "https://example.invalid", """
                [{"hash":"%s", "title":"Example S02E03", "imdb_id":"7654321", "year":2026}]
                """.formatted(HASH));
        var evidence = candidates.getFirst().providerEvidence();
        assertEquals("tt1234567", evidence.get("queryValue"));
        assertEquals("tt7654321", evidence.get("returnedImdbId"));
        assertEquals("2026", evidence.get("returnedYear"));
        assertEquals("CANDIDATE", evidence.get("titleSource"));
        assertEquals(0, candidates.getFirst().matchScore());
    }

    @Test
    void fallbackQueryTitleIsExplicitlyMarkedAndHasNoReturnedId() throws Exception {
        var candidate = parser.parse(provider, query, "https://example.invalid",
                "[{\"hash\":\"" + HASH + "\"}]").getFirst();
        assertEquals("QUERY", candidate.providerEvidence().get("titleSource"));
        assertFalse(candidate.providerEvidence().containsKey("returnedImdbId"));
    }

    @Test
    void embeddedOversizedHashCannotBeTruncatedIntoAValidMagnet() throws Exception {
        assertTrue(parser.parse(provider, query, "https://example.invalid",
                "<html><body>magnet:?xt=urn:btih:" + HASH + "0</body></html>").isEmpty());
    }

    @Test
    void base32AndHexAliasesAreDeduplicatedByTheActualHash() throws Exception {
        var candidates = parser.parse(provider, query, "https://example.invalid", """
                [{"hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "name":"Example 2026"},
                 {"hash":"0000000000000000000000000000000000000000", "name":"Example 2026"}]
                """);
        assertEquals(1, candidates.size());
        assertEquals("0".repeat(40), candidates.getFirst().infoHash());
    }
}
