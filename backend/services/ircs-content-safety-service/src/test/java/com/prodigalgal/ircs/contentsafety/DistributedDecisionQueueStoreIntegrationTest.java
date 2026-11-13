package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class DistributedDecisionQueueStoreIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private DecisionSettings settings;
    private DistributedDecisionQueueStore first;
    private DistributedDecisionQueueStore second;
    private DecisionMicrobatchClient client;
    private String prefix;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        factory.afterPropertiesSet(); factory.start(); redis = new StringRedisTemplate(factory);
        prefix = "ircs-test:{decision-queue-"+UUID.randomUUID()+"}:";
        settings = new DecisionSettings(); var mapper = new ObjectMapper();
        first = new DistributedDecisionQueueStore(redis, mapper, settings, prefix);
        second = new DistributedDecisionQueueStore(redis, mapper, settings, prefix);
        client = new DecisionMicrobatchClient(first, settings, new ContentSafetyProperties());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) { var keys = redis.keys(prefix+"*"); if (keys != null && !keys.isEmpty()) { redis.delete(keys); } }
        if (factory != null) { factory.destroy(); }
    }

    @Test
    void crossRequestMicrobatchFlushesAtCapacityAndHasOneOwnerAcrossReplicas() {
        for (int i=0; i<20; i++) { client.classify(List.of(GoogleDecisionNsfwClientTest.item("document "+i))); }
        var ready = first.ready(); assertThat(ready).hasSize(20);
        var owner = first.claim(ready); var competitor = second.claim(ready);
        assertThat(owner.jobs()).hasSize(20); assertThat(competitor.jobs()).isEmpty();
        assertThat(first.markSent(owner)).isTrue();
        assertThat(second.markSent(owner)).isFalse(); // a sent claim cannot dispatch again
        assertThat(first.snapshot().queued()).isZero();
        assertThat(first.snapshot().inflight()).isEqualTo(20);
    }

    @Test
    void incompleteBatchWaitsBrieflyThenFlushesWithoutNeedingAnotherHttpRequest() {
        var item = GoogleDecisionNsfwClientTest.item("documentary"); client.classify(List.of(item));
        redis.opsForHash().put(prefix+"job:"+client.key(item), "created", Long.toString(Instant.now().plusSeconds(10).toEpochMilli()));
        assertThat(first.ready()).isEmpty();
        redis.opsForHash().put(prefix+"job:"+client.key(item), "created", Long.toString(Instant.now().minusSeconds(1).toEpochMilli()));
        assertThat(second.ready()).hasSize(1);
    }

    @Test
    void duplicateEvidenceReusesOneJobAndCachedResultForDifferentCallerIds() {
        var original = GoogleDecisionNsfwClientTest.item("documentary");
        var otherId = GoogleDecisionNsfwClientTest.item("documentary");
        client.classify(List.of(original)); client.classify(List.of(otherId));
        assertThat(first.snapshot().queued()).isEqualTo(1);
        redis.opsForHash().put(prefix+"job:"+client.key(original), "created", "0");
        var claim = first.claim(first.ready());
        var safe = new com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult(
                DecisionSettings.MODEL, "v1beta", true, 0.01, "SAFE", Map.of());
        first.complete(claim, claim.jobs().getFirst(), safe);
        var cached = client.classify(List.of(otherId));
        assertThat(cached.get(otherId.id()).available()).isTrue();
        assertThat(cached).doesNotContainKey(original.id());
        assertThat(first.snapshot().queued()).isZero();
        assertThat(redis.getExpire(prefix+"result:"+client.key(original))).isBetween(86390L, 86400L);
    }

    @Test
    void capacityDeferralDoesNotTakeOwnershipAndNewArrivalsObserveGlobalEarliestTime() {
        var firstItem = GoogleDecisionNsfwClientTest.item("first"); client.classify(List.of(firstItem));
        redis.opsForHash().put(prefix+"job:"+client.key(firstItem), "created", "0");
        first.deferPending(first.ready(), 120, "COOLDOWN");
        assertThat(first.snapshot().queued()).isEqualTo(1);
        assertThat(first.snapshot().inflight()).isZero();
        var arriving = GoogleDecisionNsfwClientTest.item("arriving");
        var waiting = client.classify(List.of(arriving)).get(arriving.id());
        assertThat(((Number) waiting.raw().get("retryAfterSeconds")).longValue()).isBetween(118L, 121L);
        assertThat(second.ready()).isEmpty();
    }

    @Test
    void unknownSentOutcomeIsNotBlindlyReplayedAndOldOwnerCannotPublish() {
        var item = GoogleDecisionNsfwClientTest.item("ordinary documentary"); client.classify(List.of(item));
        redis.opsForHash().put(prefix+"job:"+client.key(item), "created", "0");
        var claim = first.claim(first.ready()); assertThat(first.markSent(claim)).isTrue();
        redis.opsForZSet().add(prefix+"inflight", client.key(item), Instant.now().minusSeconds(1).toEpochMilli());
        assertThat(second.ready()).isEmpty();
        first.complete(claim, claim.jobs().getFirst(), new com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult(
                DecisionSettings.MODEL, "v1beta", true, 0.01, "SAFE", Map.of()));
        var result = client.classify(List.of(item)).get(item.id());
        assertThat(result.available()).isFalse();
        assertThat(result.raw()).containsEntry("reason", "UNKNOWN_OUTCOME");
        assertThat(first.snapshot().inflight()).isZero();
    }

    @Test
    void expiredOwnerCannotCompleteOrDeferEvenBeforeRecoveryReapsItsClaim() {
        var item = GoogleDecisionNsfwClientTest.item("ordinary documentary"); client.classify(List.of(item));
        redis.opsForHash().put(prefix+"job:"+client.key(item), "created", "0");
        var claim = first.claim(first.ready()); assertThat(first.markSent(claim)).isTrue();
        redis.opsForZSet().add(prefix+"inflight", client.key(item), Instant.now().minusSeconds(1).toEpochMilli());
        first.complete(claim, claim.jobs().getFirst(), new com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult(
                DecisionSettings.MODEL, "v1beta", true, 0.01, "SAFE", Map.of()));
        first.defer(claim, claim.jobs().getFirst(), 999, "COOLDOWN");
        assertThat(redis.opsForValue().get(prefix+"result:"+client.key(item))).isNull();
        assertThat(redis.opsForValue().get(prefix+"dispatch-after")).isNull();
        assertThat(redis.opsForHash().get(prefix+"job:"+client.key(item), "state")).isEqualTo("sent");
        assertThat(redis.opsForZSet().score(prefix+"pending", client.key(item))).isNull();
        assertThat(second.ready()).isEmpty();
        assertThat(client.classify(List.of(item)).get(item.id()).raw()).containsEntry("reason", "UNKNOWN_OUTCOME");
    }

    @Test
    void queueBackpressureIsBoundedAndUnsentExpiredClaimsRecover() {
        settings.setQueueCapacity(1);
        var item = GoogleDecisionNsfwClientTest.item("one"); client.classify(List.of(item));
        var extra = GoogleDecisionNsfwClientTest.item("two");
        assertThat(client.classify(List.of(extra)).get(extra.id()).raw()).containsEntry("reason", "QUEUE_FULL");
        redis.opsForHash().put(prefix+"job:"+client.key(item), "created", "0");
        var original = first.claim(first.ready());
        redis.opsForZSet().add(prefix+"inflight", client.key(item), Instant.now().minusSeconds(1).toEpochMilli());
        var recovered = second.claim(second.ready());
        assertThat(recovered.jobs()).hasSize(1);
        assertThat(first.markSent(original)).isFalse();
        assertThat(second.markSent(recovered)).isTrue();
    }

    @Test
    void independentHttpSubmissionsProduceOneGoogleRequestAndOneQuotaReservation() throws Exception {
        settings.setBatchSize(2);
        var ordinary = GoogleDecisionNsfwClientTest.item("ordinary documentary");
        var adult = GoogleDecisionNsfwClientTest.item("explicit pornographic sexual material");
        client.classify(List.of(ordinary)); client.classify(List.of(adult));
        var credentials = mock(GoogleDecisionCredentialResolver.class);
        when(credentials.resolve()).thenReturn(new GoogleDecisionCredentialResolver.Credential("fixture-secret"));
        var transport = mock(DecisionHttpTransport.class);
        when(transport.exchange(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var body = new ObjectMapper().readTree((String) call.getArgument(3));
            assertThat(body.path("questions").size()).isEqualTo(2);
            String input = body.path("input").asText();
            // ZSET ties may sort by hash. The answer follows item labels, never hash/UUID ordering.
            var parsed = new ObjectMapper().readTree(input);
            boolean firstAdult = parsed.get(0).path("text").asText().startsWith("explicit");
            return new DecisionHttpTransport.Response(200, """
                    {"answers":{"item_1":{"type":"noul","noul":%s,"confidence":0.99},
                    "item_0":{"type":"noul","noul":%s,"confidence":0.99}},
                    "usage":{"total_input_tokens":200,"total_output_tokens":0,"total_tokens":200}}
                    """.formatted(firstAdult ? "0.01" : "0.99", firstAdult ? "0.99" : "0.01"), Map.of());
        });
        var props = new ContentSafetyProperties(); props.adult().model().setEnabled(true);
        props.adult().model().setProvider(DecisionSettings.PROVIDER);
        var quota = new DistributedDecisionQuotaStore(redis, java.time.Clock.systemUTC(), prefix+"quota:");
        var google = new GoogleDecisionNsfwClient(new ObjectMapper(), props, settings, credentials, quota, transport);
        var worker = new DecisionMicrobatchWorker(first, quota, google, settings, props, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try { worker.tick(); }
        finally { worker.close(); }
        verify(transport, times(1)).exchange(any(), any(), any(), any(), any());
        verify(credentials, times(1)).resolve();
        assertThat(client.classify(List.of(ordinary)).get(ordinary.id()).label()).isEqualTo("SAFE");
        assertThat(client.classify(List.of(adult)).get(adult.id()).label()).isEqualTo("ADULT");
        assertThat(first.snapshot().queued()).isZero();
        assertThat(first.snapshot().inflight()).isZero();
        assertThat(redis.opsForValue().get(prefix+"quota:day:"+java.time.LocalDate.now(DistributedDecisionQuotaStore.PACIFIC))).isEqualTo("1");
    }
}
