package com.prodigalgal.ircs.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeConfigServiceTest {

    private final RuntimeConfigRepository repository = org.mockito.Mockito.mock(RuntimeConfigRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final RuntimeConfigService service = new RuntimeConfigService(repository, environment);

    @Test
    void databaseValueWinsOverInjectedValue() {
        environment.setProperty("app.demo.enabled", "true");
        when(repository.findValue("app.demo.enabled")).thenReturn(Optional.of("false"));

        assertThat(service.booleanValue("app.demo.enabled", true)).isFalse();
    }

    @Test
    void injectedValueFallsBackWhenDatabaseValueIsBlank() {
        environment.setProperty("APP_DEMO_INTERVAL", "PT9S");
        when(repository.findValue("app.demo.interval")).thenReturn(Optional.of(""));

        assertThat(service.durationValue("app.demo.interval", Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void invalidTypedValueFallsBack() {
        when(repository.findValue("app.demo.limit")).thenReturn(Optional.of("not-a-number"));

        assertThat(service.intValue("app.demo.limit", 12)).isEqualTo(12);
    }
}
