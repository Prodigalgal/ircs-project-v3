package com.prodigalgal.ircs.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.identity.dto.IdentityDtos.PoWChallenge;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class PoWServiceTest {

    private static final Pattern CAPTCHA_PATTERN = Pattern.compile("^(\\d+) ([+*/-]) (\\d+) = \\?$");

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final IdentityConfigService configService = mock(IdentityConfigService.class);

    @Test
    void springContextCreatesPoWServiceWithProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(StringRedisTemplate.class, () -> redisTemplate)
                .withBean(IdentityConfigService.class, () -> configService)
                .withBean(SecureRandom.class, SecureRandom::new)
                .withUserConfiguration(PoWServiceConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(PoWService.class));
    }

    @Test
    void generatesAddSubtractMultiplyAndDivideCaptchas() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(configService.intProperty("app.identity.pow.difficulty", 4)).thenReturn(0);
        when(configService.durationProperty("app.identity.pow.ttl", Duration.ofMinutes(5)))
                .thenReturn(Duration.ofMinutes(5));
        PoWService service = new PoWService(
                redisTemplate,
                configService,
                new QueuedSecureRandom(
                        0, 0, 1,
                        1, 0, 0,
                        2, 0, 1,
                        3, 0, 1));

        List<PoWChallenge> challenges = List.of(
                service.generateChallenge("admin.login"),
                service.generateChallenge("admin.login"),
                service.generateChallenge("admin.login"),
                service.generateChallenge("admin.login"));

        assertThat(challenges)
                .extracting(PoWChallenge::captchaQuestion)
                .containsExactly("2 + 3 = ?", "3 - 1 = ?", "2 * 3 = ?", "6 / 2 = ?");
        assertThat(challenges).allSatisfy(challenge -> assertThat(challenge.captchaRequired()).isTrue());

        ArgumentCaptor<String> storedChallenge = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(4)).set(anyString(), storedChallenge.capture(), any(Duration.class));

        assertThat(storedChallenge.getAllValues())
                .extracting(value -> value.split("\\|", -1)[3])
                .containsExactly("5", "2", "6", "3");
        for (int index = 0; index < challenges.size(); index++) {
            assertThat(answer(challenges.get(index).captchaQuestion()))
                    .isEqualTo(storedChallenge.getAllValues().get(index).split("\\|", -1)[3]);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void scopedHeaderProofIsConsumedOnceAndCannotBeReplayed() {
        var stored = new java.util.concurrent.atomic.AtomicReference<>(
                "portal.register|0|server-nonce||" + java.time.Instant.now().plusSeconds(60).toEpochMilli());
        org.mockito.Mockito.doAnswer(call -> stored.getAndSet(null)).when(redisTemplate)
                .execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.<Object[]>any());
        PoWService service = new PoWService(redisTemplate, configService, new SecureRandom());
        var request = proof("id", "portal.register", "server-nonce", "1");
        service.verifyHeaders(request, "portal.register", false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.verifyHeaders(request, "portal.register", false))
                .isInstanceOf(com.prodigalgal.ircs.identity.api.ApiException.class).hasMessageContaining("expired or invalid");
        org.mockito.Mockito.verify(redisTemplate, org.mockito.Mockito.never()).opsForValue();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rejectsCrossScopeProofAndLegacyBodyEvenAtZeroDifficulty() {
        org.mockito.Mockito.doReturn("portal.register|0|server-nonce||" + java.time.Instant.now().plusSeconds(60).toEpochMilli())
                .when(redisTemplate).execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                        org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.<Object[]>any());
        PoWService service = new PoWService(redisTemplate, configService, new SecureRandom());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.verifyHeaders(
                proof("id", "portal.register", "server-nonce", "1"), "admin.login", true))
                .isInstanceOf(com.prodigalgal.ircs.identity.api.ApiException.class).hasMessageContaining("scope mismatch");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.verify(
                new com.prodigalgal.ircs.identity.dto.IdentityDtos.PoWVerification("id", "legacy-solution")))
                .isInstanceOf(com.prodigalgal.ircs.identity.api.ApiException.class).hasMessageContaining("scoped PoW verification headers");
    }

    private org.springframework.mock.web.MockHttpServletRequest proof(String id, String scope, String nonce, String counter) {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/portal/auth/register");
        request.addHeader("X-IRCS-POW-Challenge", id);
        request.addHeader("X-IRCS-POW-Counter", counter);
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((id + ":" + scope + ":" + nonce + ":" + counter).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            request.addHeader("X-IRCS-POW-Digest", java.util.HexFormat.of().formatHex(digest));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
        return request;
    }

    private String answer(String question) {
        Matcher matcher = CAPTCHA_PATTERN.matcher(question);
        assertThat(matcher.matches()).isTrue();
        int left = Integer.parseInt(matcher.group(1));
        String operator = matcher.group(2);
        int right = Integer.parseInt(matcher.group(3));
        return String.valueOf(switch (operator) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> left / right;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        });
    }

    private static final class QueuedSecureRandom extends SecureRandom {

        private final Queue<Integer> values = new ArrayDeque<>();

        QueuedSecureRandom(int... values) {
            for (int value : values) {
                this.values.add(value);
            }
        }

        @Override
        public int nextInt(int bound) {
            if (values.isEmpty()) {
                return 0;
            }
            return Math.floorMod(values.remove(), bound);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(PoWService.class)
    static class PoWServiceConfiguration {
    }
}
