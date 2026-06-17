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
                new AutoStartGate("aggregation-work-queue-worker", "app.aggregation.work-queue.worker.enabled",
                        "APP_AGGREGATION_WORK_QUEUE_WORKER_ENABLED", false)
        ));
    }
}
