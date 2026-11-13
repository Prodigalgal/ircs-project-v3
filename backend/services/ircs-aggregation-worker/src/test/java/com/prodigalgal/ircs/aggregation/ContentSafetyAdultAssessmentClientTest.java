package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.common.adult.AdultAssessmentInput;
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
        String body = """
                {"items":[{"id":"%s","level":"SUSPECT","signals":[],"model":{
                "available":false,"raw":{"provider":"GOOGLE_DECISIONS","retryable":true,
                "reason":"QUOTA_WAIT","retryAfterSeconds":86400}}}]}
                """.formatted(id);
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
        String body = """
                {"items":[
                {"id":"%s","level":"SUSPECT","model":{"available":false,
                "raw":{"provider":"GOOGLE_DECISIONS","retryable":true,"reason":"QUEUE_PENDING","retryAfterSeconds":2}}},
                {"id":"%s","level":"SUSPECT","model":{"available":false,
                "raw":{"provider":"GOOGLE_DECISIONS","retryable":true,"reason":"QUOTA_WAIT","retryAfterSeconds":86400}}}
                ]}
                """.formatted(first, second);
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
        String body = """
                {"items":[{"id":"%s","level":"ADULT","adultRestricted":true,"confidence":100,
                "signals":[],"model":{"available":false,"raw":{"provider":"GOOGLE_DECISIONS",
                "retryable":true,"reason":"QUEUE_PENDING","retryAfterSeconds":2}}}]}
                """.formatted(id);
        withResponse(200, body, client -> {
            var assessment = client.assess(Map.of(id, input())).get(id);
            assertTrue(assessment.adultRestricted());
            assertEquals(com.prodigalgal.ircs.common.adult.AdultAssessmentLevel.ADULT, assessment.level());
        });
    }

    @Test
    void permanentReviewResultCompletesAsSuspectWithoutRetry() throws Exception {
        UUID id = UUID.randomUUID();
        String body = """
                {"items":[{"id":"%s","level":"SUSPECT","confidence":30,"signals":[],"model":{
                "available":false,"raw":{"provider":"GOOGLE_DECISIONS","retryable":false,
                "reason":"LOW_CONFIDENCE"}}}]}
                """.formatted(id);
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
