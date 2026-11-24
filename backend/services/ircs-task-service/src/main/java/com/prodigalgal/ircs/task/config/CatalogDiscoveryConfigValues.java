package com.prodigalgal.ircs.task.config;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogDiscoveryConfigValues {

    private static final String PREFIX = "app.task.catalog-discovery.";
    private final RuntimeConfigService config;

    public boolean enabled() {
        return config.booleanValue(PREFIX + "enabled", true);
    }

    public int batchSize() {
        return config.boundedIntValue(PREFIX + "batch-size", 5, 1, 5);
    }

    public int maxDataSources() {
        return config.boundedIntValue(PREFIX + "max-data-sources", 3, 1, 3);
    }

    public List<UUID> sourceIds() {
        String configured = config.stringValue(PREFIX + "source-ids", "");
        if (configured.isEmpty()) return List.of();
        return Arrays.stream(configured.split(",", -1))
                .map(String::strip)
                .map(UUID::fromString)
                .distinct()
                .toList();
    }

    public int maxActiveTasks() {
        return config.boundedIntValue(PREFIX + "max-active-tasks", 24, 1, 100);
    }

    public Duration leaseDuration() {
        Duration configured = config.positiveDurationValue(PREFIX + "lease-duration", Duration.ofMinutes(10));
        if (configured.compareTo(Duration.ofMinutes(2)) < 0) return Duration.ofMinutes(2);
        return configured.compareTo(Duration.ofMinutes(30)) > 0 ? Duration.ofMinutes(30) : configured;
    }
}
