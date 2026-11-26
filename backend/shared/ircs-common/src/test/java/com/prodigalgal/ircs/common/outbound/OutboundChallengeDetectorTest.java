package com.prodigalgal.ircs.common.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboundChallengeDetectorTest {

    @Test
    void recognisesTheAwsWafChallengeImdbReturnsInsteadOfThePage() {
        var response = new OutboundHttpResponse(202,
                Map.of("x-amzn-waf-action", List.of("challenge")), new byte[] {'<', 'h', 't', 'm', 'l', '>'});
        assertThat(OutboundChallengeDetector.detect(response))
                .contains(OutboundChallengeDetector.WAF_CHALLENGE);
    }

    @Test
    void recognisesTheDoubanSecurityRedirect() {
        var response = new OutboundHttpResponse(302,
                Map.of("Location", List.of("https://sec.douban.com/c?r=https%3A%2F%2Fmovie.douban.com%2Fsubject%2F1%2F&a=1")),
                new byte[0]);
        assertThat(OutboundChallengeDetector.detect(response))
                .contains(OutboundChallengeDetector.SECURITY_REDIRECT);
    }

    @Test
    void recognisesAnAkamaiDeny() {
        var response = new OutboundHttpResponse(403,
                Map.of("Akamai-GRN", List.of("0.826e2317.1795676207.2b9e0e2f")), "denied".getBytes(StandardCharsets.UTF_8));
        assertThat(OutboundChallengeDetector.detect(response))
                .contains(OutboundChallengeDetector.BOT_MANAGER_DENY);
    }

    @Test
    void recognisesTheDoubanLoginPromptWithoutHeaders() {
        var response = new OutboundHttpResponse(403, Map.of(),
                "error code: 01004 Please login: https://accounts.douban.com/passport/login"
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(OutboundChallengeDetector.detect(response))
                .contains(OutboundChallengeDetector.SECURITY_REDIRECT);
    }

    @Test
    void rateLimitIsTransientWhileChallengesAreNot() {
        assertThat(OutboundChallengeDetector.detect(new OutboundHttpResponse(429, Map.of(), new byte[0])))
                .contains(OutboundChallengeDetector.RATE_LIMITED);
        assertThat(OutboundChallengeDetector.isTransient(OutboundChallengeDetector.RATE_LIMITED)).isTrue();
        assertThat(OutboundChallengeDetector.isChallenge(OutboundChallengeDetector.RATE_LIMITED)).isFalse();
        assertThat(OutboundChallengeDetector.isChallenge(OutboundChallengeDetector.WAF_CHALLENGE)).isTrue();
    }

    @Test
    void ordinaryAnswersAreNotClassifiedAsChallenges() {
        assertThat(OutboundChallengeDetector.detect(new OutboundHttpResponse(200, Map.of(), "<html>ok</html>".getBytes(StandardCharsets.UTF_8))))
                .isEmpty();
        assertThat(OutboundChallengeDetector.detect(new OutboundHttpResponse(404, Map.of(), new byte[0])))
                .isEmpty();
        assertThat(OutboundChallengeDetector.detect(new OutboundHttpResponse(403, Map.of(), new byte[0])))
                .isEmpty();
        assertThat(OutboundChallengeDetector.detect(null)).isEmpty();
    }
}
