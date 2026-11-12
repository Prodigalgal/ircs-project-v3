package com.prodigalgal.ircs.notification.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_KEY_VALUE_PORT", matches = "\\d+")
class MailSendRateLimiterIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private UUID credentialId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("127.0.0.1",
                Integer.parseInt(System.getenv("IRCS_TEST_KEY_VALUE_PORT")));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        credentialId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redis.delete("traffic:limit:mail:credential:" + credentialId);
        connectionFactory.destroy();
    }

    @Test
    void twoWorkersShareTheSameSlidingWindowAndPreserveBurstCapacity() throws InterruptedException {
        NotificationMailConfigValues config = org.mockito.Mockito.mock(NotificationMailConfigValues.class);
        when(config.rateLimitMaxWaitMs()).thenReturn(10);
        MailCredentialUsageRepository usage = org.mockito.Mockito.mock(MailCredentialUsageRepository.class);
        MailSendRateLimiter first = new MailSendRateLimiter(config, usage, null, null, redis);
        MailSendRateLimiter second = new MailSendRateLimiter(config, usage, null, null, redis);
        MailCredential credential = new MailCredential(credentialId, "mail@example.invalid", "secret",
                null, null, null, null, null, null, null, 2, "SECOND", 0L, 0L);

        assertThat(first.selectCredential(List.of(credential))).isEqualTo(credential);
        assertThat(second.selectCredential(List.of(credential))).isEqualTo(credential);
        assertThatThrownBy(() -> first.selectCredential(List.of(credential)))
                .isInstanceOf(MailCredentialLeaseException.class).hasMessageContaining("max wait");

        Thread.sleep(1100);
        assertThat(second.selectCredential(List.of(credential))).isEqualTo(credential);
    }
}
