package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AggregationConfigValuesTest {

    private final SystemConfigRepository repository = org.mockito.Mockito.mock(SystemConfigRepository.class);

    @Test
    void usesV1DefaultWhenNoRuntimeOrDbConfigExists() {
        AggregationConfigValues values = values(new MockEnvironment());

        assertEquals(200, values.batchSize());
    }

    @Test
    void dbCanonicalKeyOverridesV1Default() {
        when(repository.findValue("aggregation.batch-size")).thenReturn(Optional.of("11"));
        AggregationConfigValues values = values(new MockEnvironment());

        assertEquals(11, values.batchSize());
    }

    @Test
    void runtimeAliasOverridesDbConfig() {
        when(repository.findValue("aggregation.batch-size")).thenReturn(Optional.of("11"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("APP_AGGREGATION_BATCH_SIZE", "1");
        AggregationConfigValues values = values(environment);

        assertEquals(1, values.batchSize());
    }

    @Test
    void invalidDbValueFallsBackWithoutThrowing() {
        when(repository.findValue("aggregation.batch-size")).thenReturn(Optional.of("bad"));
        AggregationConfigValues values = values(new MockEnvironment());

        assertEquals(200, values.batchSize());
    }

    private AggregationConfigValues values(MockEnvironment environment) {
        return new AggregationConfigValues(environment, repository);
    }
}
