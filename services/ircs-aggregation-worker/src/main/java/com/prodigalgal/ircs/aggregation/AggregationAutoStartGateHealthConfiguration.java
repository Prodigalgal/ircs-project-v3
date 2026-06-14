package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.readiness.AutoStartGate;
import com.prodigalgal.ircs.common.readiness.AutoStartGateHealthIndicator;
import java.util.List;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class AggregationAutoStartGateHealthConfiguration {

    @Bean
    HealthIndicator autoStartGateHealthIndicator(Environment environment) {
        return new AutoStartGateHealthIndicator(environment, List.of(
                new AutoStartGate("aggregation-scheduler", "app.aggregation.scheduler.enabled",
                        "APP_AGGREGATION_SCHEDULER_ENABLED", false),
                new AutoStartGate("aggregation-config-listener", "app.aggregation.config-listener.enabled",
                        "APP_AGGREGATION_CONFIG_LISTENER_ENABLED", false)
        ));
    }
}
