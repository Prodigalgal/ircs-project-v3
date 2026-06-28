package com.prodigalgal.ircs.magnet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GenericMagnetProviderSearchRunnerTest {

    private final MagnetProviderSummary provider = provider("THE_PIRATE_BAY", "https://apibay.org");
    private final MagnetExternalIdQuery query = new MagnetExternalIdQuery("TITLE_YEAR", "Codex Movie 2026");

    @Test
    void parsesApibayJsonResultWithHashOnlyFormat() {
        FakeHttpClient httpClient = FakeHttpClient.ok("""
                [
                  {
                    "id": "1",
                    "name": "Codex Movie 2026 1080p WEB-DL",
                    "info_hash": "abcdef1234567890abcdef1234567890abcdef12",
                    "size": "1503238553",
                    "seeders": "25",
                    "leechers": "3"
                  }
                ]
                """);
        GenericMagnetProviderSearchRunner runner = runner(httpClient);

        MagnetProviderSearchResult result = runner.search(provider, query, UUID.randomUUID());

        assertEquals("https://apibay.org/q.php?q=Codex%20Movie%202026&cat=0", result.requestUrl());
        assertEquals(1, result.candidates().size());
        MagnetProviderCandidate candidate = result.candidates().getFirst();
        assertEquals("ABCDEF1234567890ABCDEF1234567890ABCDEF12", candidate.infoHash());
        assertEquals(
                "magnet:?xt=urn:btih:ABCDEF1234567890ABCDEF1234567890ABCDEF12&dn=Codex%20Movie%202026%201080p%20WEB-DL",
                candidate.magnetUri());
        assertEquals("Codex Movie 2026 1080p WEB-DL", candidate.title());
        assertEquals(1503238553L, candidate.sizeBytes());
        assertEquals(25, candidate.seeders());
        assertEquals(3, candidate.leechers());
        assertEquals("1080P", candidate.resolution());
        assertEquals("TITLE_YEAR", candidate.matchedExternalIdType());
        assertEquals("Codex Movie 2026", candidate.matchedExternalIdValue());
        assertEquals(76, candidate.matchScore());
        assertEquals("JSON", candidate.providerEvidence().get("sourceFormat"));
    }

    @Test
    void parsesHtmlMagnetAttributesAndEmbeddedPlainText() {
        FakeHttpClient httpClient = FakeHttpClient.ok("""
                <html>
                  <head><title>Codex Search</title></head>
                  <body>
                    <table>
                      <tr>
                        <td><a href="/detail/1">Codex Movie 2026 4K</a></td>
                        <td>1.4 GB</td>
                        <td>Seeders: 33</td>
                        <td>Leechers: 4</td>
                        <td><a data-clipboard-text="magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12&amp;dn=Codex%20Movie%202026%204K">copy</a></td>
                      </tr>
                    </table>
                    <script>
                    window.extra = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111&dn=Codex%20Movie%20720p";
                    </script>
                  </body>
                </html>
                """);
        GenericMagnetProviderSearchRunner runner = runner(httpClient);

        MagnetProviderSearchResult result = runner.search(
                provider("GENERIC_HTML", "https://example.invalid/search?q={query}"),
                query,
                UUID.randomUUID());

        assertEquals("https://example.invalid/search?q=Codex%20Movie%202026", result.requestUrl());
        assertEquals(2, result.candidates().size());
        MagnetProviderCandidate first = result.candidates().getFirst();
        assertEquals("ABCDEF1234567890ABCDEF1234567890ABCDEF12", first.infoHash());
        assertEquals("Codex Movie 2026 4K", first.title());
        assertEquals(1_400_000_000L, first.sizeBytes());
        assertEquals("1.4 GB", first.sizeLabel());
        assertEquals(33, first.seeders());
        assertEquals(4, first.leechers());
        assertEquals("4K", first.resolution());
        assertEquals("https://example.invalid/detail/1", first.sourceUrl());
        assertEquals("HTML", first.providerEvidence().get("sourceFormat"));

        MagnetProviderCandidate second = result.candidates().get(1);
        assertEquals("1111111111111111111111111111111111111111", second.infoHash());
        assertEquals("Codex Movie 720p", second.title());
    }

    @Test
    void classifiesTimeoutWithoutLeakingBody() {
        GenericMagnetProviderSearchRunner runner = runner(FakeHttpClient.timeout());

        MagnetProviderRunnerException ex = assertThrows(
                MagnetProviderRunnerException.class,
                () -> runner.search(provider, query, UUID.randomUUID()));

        assertEquals("GENERIC_MAGNET_TIMEOUT", ex.failureType());
        assertTrue(ex.getMessage().contains("GENERIC_MAGNET_TIMEOUT"));
    }

    private GenericMagnetProviderSearchRunner runner(FakeHttpClient httpClient) {
        return new GenericMagnetProviderSearchRunner(
                new ObjectMapper(),
                MagnetProviderTrafficLimiter.noop(),
                httpClientProvider(httpClient));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<GenericMagnetProviderSearchRunner.GenericMagnetHttpClient> httpClientProvider(
            GenericMagnetProviderSearchRunner.GenericMagnetHttpClient httpClient) {
        ObjectProvider<GenericMagnetProviderSearchRunner.GenericMagnetHttpClient> provider =
                org.mockito.Mockito.mock(ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfUnique()).thenReturn(httpClient);
        return provider;
    }

    private MagnetProviderSummary provider(String providerType, String baseUrl) {
        return new MagnetProviderSummary(
                UUID.randomUUID(),
                providerType.toLowerCase(),
                providerType,
                providerType,
                baseUrl,
                true,
                10,
                "HIGH",
                List.of("TITLE_YEAR", "TITLE", "IMDB"),
                1000,
                3000,
                10000,
                20,
                true,
                "仅用于测试。",
                null,
                null,
                null,
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z"));
    }

    private static final class FakeHttpClient implements GenericMagnetProviderSearchRunner.GenericMagnetHttpClient {

        private final int statusCode;
        private final String body;
        private final boolean timeout;

        private FakeHttpClient(int statusCode, String body, boolean timeout) {
            this.statusCode = statusCode;
            this.body = body;
            this.timeout = timeout;
        }

        static FakeHttpClient ok(String body) {
            return new FakeHttpClient(200, body, false);
        }

        static FakeHttpClient timeout() {
            return new FakeHttpClient(200, "", true);
        }

        @Override
        public GenericMagnetProviderSearchRunner.GenericMagnetHttpResponse get(URI uri, Duration timeout)
                throws IOException {
            if (this.timeout) {
                throw new HttpTimeoutException("secret timeout detail");
            }
            return new GenericMagnetProviderSearchRunner.GenericMagnetHttpResponse(statusCode, body);
        }
    }
}
