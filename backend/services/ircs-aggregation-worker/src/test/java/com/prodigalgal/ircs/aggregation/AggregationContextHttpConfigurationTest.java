package com.prodigalgal.ircs.aggregation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AggregationContextHttpConfigurationTest {
    @Test
    void timeoutMisconfigurationCannotDisableDeadlineOrCreateAnUnboundedWait() {
        Duration fallback = Duration.ofSeconds(3);
        assertEquals(fallback, AggregationContextHttpConfiguration.boundedTimeout(null, fallback));
        assertEquals(fallback, AggregationContextHttpConfiguration.boundedTimeout(Duration.ZERO, fallback));
        assertEquals(fallback, AggregationContextHttpConfiguration.boundedTimeout(Duration.ofSeconds(-1), fallback));
        assertEquals(Duration.ofMillis(100), AggregationContextHttpConfiguration.boundedTimeout(Duration.ofNanos(1), fallback));
        assertEquals(Duration.ofSeconds(30), AggregationContextHttpConfiguration.boundedTimeout(Duration.ofDays(1), fallback));
    }
}
