package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestAggregationContextSearchClientTest {

    @Test
    void disabledClientDoesNotAttemptSearchRecall() {
        RestAggregationContextSearchClient client =
                new RestAggregationContextSearchClient(RestClient.builder(), false, "http://search-service", null);

        AggregationContextSearchClient.ContextSearchResult result =
                client.findCandidateUnifiedVideoIds("Codex Signal", "2026");

        assertFalse(result.attempted());
        assertEquals(List.of(), result.unifiedVideoIds());
    }

    @Test
    void callsSearchServiceAndDeduplicatesReturnedIds() {
        UUID id = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestAggregationContextSearchClient client =
                new RestAggregationContextSearchClient(builder, true, "http://search-service/", null);
        server.expect(requestTo("http://search-service/internal/v1/search/unified-context-candidates?title=Codex%20Signal&year=2026"))
                .andRespond(withSuccess(
                        "[\"" + id + "\",\"" + id + "\"]",
                        MediaType.APPLICATION_JSON));

        AggregationContextSearchClient.ContextSearchResult result =
                client.findCandidateUnifiedVideoIds("Codex Signal", "2026");

        assertTrue(result.attempted());
        assertEquals(List.of(id), result.unifiedVideoIds());
        server.verify();
    }

    @Test
    void rejectsHttpRecallInsideDatabaseTransaction() {
        RestAggregationContextSearchClient client = new RestAggregationContextSearchClient(
                RestClient.builder(), true, "http://search-service", null);
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                    () -> client.findCandidateUnifiedVideoIds("Ordinary movie", "2026"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void searchServiceFailureIsAttemptedEmptyResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestAggregationContextSearchClient client =
                new RestAggregationContextSearchClient(builder, true, "http://search-service", null);
        server.expect(requestTo("http://search-service/internal/v1/search/unified-context-candidates?title=Codex%20Signal&year=2026"))
                .andRespond(withServerError());

        AggregationContextSearchClient.ContextSearchResult result =
                client.findCandidateUnifiedVideoIds("Codex Signal", "2026");

        assertTrue(result.attempted());
        assertEquals(List.of(), result.unifiedVideoIds());
        server.verify();
    }
}
