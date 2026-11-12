package com.prodigalgal.ircs.common.readiness;

import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;

public class AutoStartGateHealthIndicator implements HealthIndicator {

    private final AutoStartGateInspector inspector;
    private final List<AutoStartGate> gates;

    public AutoStartGateHealthIndicator(Environment environment, List<AutoStartGate> gates) {
        this.inspector = new AutoStartGateInspector(environment);
        this.gates = List.copyOf(gates);
    }

    @Override
    public Health health() {
        List<AutoStartGateState> states = inspector.inspect(gates);
        return Health.up()
                .withDetail("gateCount", states.size())
                .withDetail("enabledCount", states.stream().filter(AutoStartGateState::enabled).count())
                .withDetail("blockedCount", states.stream().filter(AutoStartGateState::blocked).count())
                .withDetail("gates", states)
                .build();
    }
}
