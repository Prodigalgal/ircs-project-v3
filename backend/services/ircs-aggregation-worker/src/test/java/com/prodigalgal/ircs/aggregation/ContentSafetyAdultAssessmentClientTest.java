package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentBatchResponse;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ContentSafetyAdultAssessmentClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assessSendsCompactPayloadWithoutRawMetadata() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = captureServer(requestBody);
        server.start();
        try {
            UUID id = UUID.randomUUID();
            ContentSafetyAdultAssessmentClient client = new ContentSafetyAdultAssessmentClient(
                    objectMapper,
                    true,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "aggregation-test",
                    "token",
                    "content-safety:assess",
                    Duration.ofSeconds(3));

            client.assess(Map.of(id, new AdultAssessmentInput(
                    "title",
                    "alias",
                    "d".repeat(800),
                    "remarks",
                    "subtitle",
                    "movie",
                    "Movie",
                    List.of("actor"),
                    List.of("director"),
                    List.of("genre"),
                    List.of(new AdultAssessmentInput.SourceEvidence(
                            "source",
                            true,
                            "adult",
                            "Adult",
                            "example.test",
                            "raw-metadata-should-not-cross-service-boundary")))));

            JsonNode item = objectMapper.readTree(requestBody.get()).path("items").get(0);
            assertEquals(512, item.path("description").asText().length());
            assertTrue(item.path("sources").get(0).path("rawMetadata").isNull());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void googleQuotaUnavailabilityDefersInsteadOfFallingBackToSafe() throws Exception {
        UUID id = UUID.randomUUID();
        String body = response(result(id, "SUSPECT", false, 0,
                unavailableGoogleModel(true, "QUOTA_WAIT", 86400)));
        withResponse(200, body, client -> {
            var exception = assertThrows(AdultAssessmentDeferredException.class,
                    () -> client.assess(Map.of(id, input())));
            assertEquals(Duration.ofDays(1), exception.retryAfter());
            assertEquals("QUOTA_WAIT", exception.getMessage());
        });
    }

    @Test
    void mixedBatchUsesLongestRealQuotaWaitRatherThanPollingDayQuotaEveryTwoSeconds() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        String body = response(
                result(first, "SUSPECT", false, 0, unavailableGoogleModel(true, "QUEUE_PENDING", 2)),
                result(second, "SUSPECT", false, 0, unavailableGoogleModel(true, "QUOTA_WAIT", 86400)));
        withResponse(200, body, client -> {
            var exception = assertThrows(AdultAssessmentDeferredException.class,
                    () -> client.assess(Map.of(first, input(), second, input())));
            assertEquals("QUOTA_WAIT", exception.getMessage());
            assertEquals(Duration.ofDays(1), exception.retryAfter());
        });
    }

    @Test
    void definiteAdultResultRemainsRestrictedDespiteAuxiliaryGooglePending() throws Exception {
        UUID id = UUID.randomUUID();
        String body = response(result(id, "ADULT", true, 100,
                unavailableGoogleModel(true, "QUEUE_PENDING", 2)));
        withResponse(200, body, client -> {
            var assessment = client.assess(Map.of(id, input())).get(id);
            assertTrue(assessment.adultRestricted());
            assertEquals(com.prodigalgal.ircs.common.adult.AdultAssessmentLevel.ADULT, assessment.level());
        });
    }

    @Test
    void permanentReviewResultCompletesAsSuspectWithoutRetry() throws Exception {
        UUID id = UUID.randomUUID();
        String body = response(result(id, "SUSPECT", false, 30,
                unavailableGoogleModel(false, "LOW_CONFIDENCE", 60)));
        withResponse(200, body, client -> assertEquals(
                com.prodigalgal.ircs.common.adult.AdultAssessmentLevel.SUSPECT,
                client.assess(Map.of(id, input())).get(id).level()));
    }

    @Test
    void httpFailureMalformedJsonAndMissingItemsNeverFallBackToRules() throws Exception {
        for (int status : List.of(429, 500, 503)) {
            withResponse(status, "upstream failure", client -> assertThrows(AdultAssessmentDeferredException.class,
                    () -> client.assess(Map.of(UUID.randomUUID(), input()))));
        }
        for (String body : List.of("invalid-json", "{\"items\":[]}", "null")) {
            withResponse(200, body, client -> assertThrows(AdultAssessmentDeferredException.class,
                    () -> client.assess(Map.of(UUID.randomUUID(), input()))));
        }
    }

    @Test
    void forbidsNetworkCallsInsideTransaction() {
        var client = new ContentSafetyAdultAssessmentClient(objectMapper, true, "http://127.0.0.1:1",
                "test", "token", "content-safety:assess", Duration.ofSeconds(1));
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> client.assess(Map.of(UUID.randomUUID(), input())));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    private AdultAssessmentInput input() {
        return new AdultAssessmentInput("title", null, null, null, null, "movie", "Movie",
                List.of(), List.of(), List.of(), List.of());
    }

    private String response(AdultAssessmentResult... items) throws Exception {
        return objectMapper.writeValueAsString(new AdultAssessmentBatchResponse("test", List.of(items)));
    }

    private AdultAssessmentResult result(UUID id, String level, boolean adultRestricted, int confidence,
            AdultAssessmentModelResult model) {
        return new AdultAssessmentResult(id, "test", level, adultRestricted, confidence, List.of(), model);
    }

    private AdultAssessmentModelResult unavailableGoogleModel(boolean retryable, String reason, long retryAfterSeconds) {
        return new AdultAssessmentModelResult("gemini-decision-1", "v1beta", false, 0.0d, "UNAVAILABLE",
                Map.of("provider", "GOOGLE_DECISIONS", "retryable", retryable, "reason", reason,
                        "retryAfterSeconds", retryAfterSeconds));
    }

    private void withResponse(int status, String body, java.util.function.Consumer<ContentSafetyAdultAssessmentClient> test)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/content-safety/adult-assessments:batch", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            test.accept(new ContentSafetyAdultAssessmentClient(objectMapper, true,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test", "token",
                    "content-safety:assess", Duration.ofSeconds(3)));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer captureServer(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/content-safety/adult-assessments:batch", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String id = objectMapper.readTree(requestBody.get()).path("items").get(0).path("id").asText();
            byte[] response = ("{\"engineVersion\":\"test\",\"items\":[{\"id\":\"" + id
                    + "\",\"ruleVersion\":\"test\",\"level\":\"SAFE\",\"adultRestricted\":false,\"confidence\":90,\"signals\":[]}]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }
}
