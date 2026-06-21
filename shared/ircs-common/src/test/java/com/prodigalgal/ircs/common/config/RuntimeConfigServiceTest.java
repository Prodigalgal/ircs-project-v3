package com.prodigalgal.ircs.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

class RuntimeConfigServiceTest {

    private final RuntimeConfigValueSource valueSource = mock(RuntimeConfigValueSource.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<RuntimeConfigValueSource> valueSourceProvider = mock(ObjectProvider.class);
    private final MockEnvironment environment = new MockEnvironment();
    private final RuntimeConfigService service = new RuntimeConfigService(valueSourceProvider, environment);

    @Test
    void databaseValueWinsOverInjectedValue() {
        when(valueSourceProvider.getIfAvailable()).thenReturn(valueSource);
        environment.setProperty("app.demo.enabled", "true");
        when(valueSource.findValue("app.demo.enabled")).thenReturn(Optional.of("false"));

        assertThat(service.booleanValue("app.demo.enabled", true)).isFalse();
    }

    @Test
    void injectedValueFallsBackWhenDatabaseValueIsBlank() {
        when(valueSourceProvider.getIfAvailable()).thenReturn(valueSource);
        environment.setProperty("APP_DEMO_INTERVAL", "PT9S");
        when(valueSource.findValue("app.demo.interval")).thenReturn(Optional.of(""));

        assertThat(service.durationValue("app.demo.interval", Duration.ofSeconds(1)))
                .isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void invalidTypedValueFallsBack() {
        when(valueSourceProvider.getIfAvailable()).thenReturn(valueSource);
        when(valueSource.findValue("app.demo.limit")).thenReturn(Optional.of("not-a-number"));

        assertThat(service.intValue("app.demo.limit", 12)).isEqualTo(12);
    }

    @Test
    void injectedValueStillWorksWhenDatabaseSourceIsUnavailable() {
        when(valueSourceProvider.getIfAvailable()).thenReturn(null);
        environment.setProperty("APP_DEMO_LIMIT", "25");

        assertThat(service.intValue("app.demo.limit", 12)).isEqualTo(25);
    }
}
