package com.prodigalgal.ircs.contentsafety;

import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentItem;
import com.prodigalgal.ircs.contracts.contentsafety.AdultAssessmentModelResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** HTTP assessments only enqueue or read a result. Provider I/O belongs to the microbatch worker. */
@Component
@RequiredArgsConstructor
class DecisionMicrobatchClient {
    static final String POLICY = "text-nsfw-v1";
    private final DecisionQueueStore queue;
    private final DecisionSettings settings;
    private final ContentSafetyProperties properties;

    Map<UUID, AdultAssessmentModelResult> classify(List<AdultAssessmentItem> items) {
        Map<UUID, AdultAssessmentModelResult> results = new LinkedHashMap<>();
        for (AdultAssessmentItem item : items) {
            try {
                settings.validate();
                String full = AdultModelClassifierClient.adultText(item, Integer.MAX_VALUE);
                if (full.isBlank()) {
                    results.put(item.id(), GoogleDecisionNsfwClient.unavailable("EMPTY_INPUT", 60));
                    continue;
                }
                DecisionQueueStore.Lookup found = queue.submit(key(item), item);
                AdultAssessmentModelResult result = found.result();
                if (result == null) {
                    result = pending(found.full() ? "QUEUE_FULL" : "QUEUE_PENDING", found.retryAfterSeconds(), found.ahead(), "");
                } else if (!result.available() && Boolean.TRUE.equals(result.raw().get("retryable"))) {
                    Map<String, Object> raw = new LinkedHashMap<>(result.raw());
                    raw.put("retryAfterSeconds", found.retryAfterSeconds());
                    result = new AdultAssessmentModelResult(result.name(), result.version(), false, result.adultScore(), result.label(), raw);
                }
                results.put(item.id(), result);
            } catch (DecisionUnavailableException ex) {
                results.put(item.id(), "CONFIGURATION_ERROR".equals(ex.reason())
                        ? GoogleDecisionNsfwClient.unavailable(ex.reason(), 60)
                        : pending("QUEUE_PENDING", 2, 0, ex.reason()));
            } catch (RuntimeException ex) {
                results.put(item.id(), pending("QUEUE_PENDING", 2, 0, "QUEUE_UNAVAILABLE"));
            }
        }
        return results;
    }

    String key(AdultAssessmentItem item) {
        // Full normalized evidence is hashed, including any suffix beyond the evaluated prefix.
        String evidence = AdultModelClassifierClient.adultText(item, Integer.MAX_VALUE);
        String policy = DecisionSettings.MODEL+'\n'+POLICY+'\n'+GoogleDecisionNsfwClient.INSTRUCTIONS+'\n'
                +settings.getMaxTextLength()+'\n'+settings.getMinConfidence()+'\n'
                +properties.adult().model().adultThreshold()+'\n'+properties.adult().model().suspectThreshold()+'\n'+evidence;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(policy.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is required", ex); }
    }

    static AdultAssessmentModelResult pending(String reason, long seconds, long ahead, String upstreamReason) {
        return new AdultAssessmentModelResult(DecisionSettings.MODEL, "v1beta", false, 0, "UNAVAILABLE", Map.of(
                "reason", reason, "retryable", true, "retryAfterSeconds", Math.max(2, seconds),
                "provider", DecisionSettings.PROVIDER, "queueAhead", ahead, "upstreamReason", upstreamReason,
                "inputModality", "text"));
    }
}
