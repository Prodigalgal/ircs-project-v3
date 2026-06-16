package com.prodigalgal.ircs.scraper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ScraperTrafficConfigValuesTest {

    private final SystemConfigRepository repository = org.mockito.Mockito.mock(SystemConfigRepository.class);

    @Test
    void readsGlobalTrafficDefaultsFromSystemConfigRepository() {
        when(repository.findValue("global.traffic.min-delay-ms")).thenReturn(Optional.of("4000"));
        when(repository.findValue("global.traffic.max-delay-ms")).thenReturn(Optional.of("4000"));
        when(repository.findValue("global.traffic.safety-floor-ms")).thenReturn(Optional.of("3500"));
        when(repository.findValue("global.traffic.max-wait-ms")).thenReturn(Optional.of("180000"));

        ScraperTrafficConfigValues values = new ScraperTrafficConfigValues(new MockEnvironment(), repository);

        assertThat(values.globalDelay()).isEqualTo(Duration.ofMillis(4000));
        assertThat(values.safetyFloor()).isEqualTo(Duration.ofMillis(3500));
        assertThat(values.maxWait(Duration.ofSeconds(1))).isEqualTo(Duration.ofMillis(180000));
    }

    @Test
    void runtimeEnvironmentOverridesSystemConfigRepository() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GLOBAL_TRAFFIC_MIN_DELAY_MS", "1000")
                .withProperty("GLOBAL_TRAFFIC_MAX_DELAY_MS", "1000")
                .withProperty("GLOBAL_TRAFFIC_SAFETY_FLOOR_MS", "1200")
                .withProperty("GLOBAL_TRAFFIC_MAX_WAIT_MS", "90000");
        when(repository.findValue("global.traffic.min-delay-ms")).thenReturn(Optional.of("4000"));
        when(repository.findValue("global.traffic.max-delay-ms")).thenReturn(Optional.of("4000"));
        when(repository.findValue("global.traffic.safety-floor-ms")).thenReturn(Optional.of("3500"));
        when(repository.findValue("global.traffic.max-wait-ms")).thenReturn(Optional.of("180000"));

        ScraperTrafficConfigValues values = new ScraperTrafficConfigValues(environment, repository);

        assertThat(values.globalDelay()).isEqualTo(Duration.ofMillis(1000));
        assertThat(values.safetyFloor()).isEqualTo(Duration.ofMillis(1200));
        assertThat(values.maxWait(Duration.ofSeconds(1))).isEqualTo(Duration.ofMillis(90000));
    }
}
