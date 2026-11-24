package com.prodigalgal.ircs.task.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CatalogDiscoveryConfigValuesTest {

    @Test
    void clampsAutomaticFanoutAndAllowsRuntimeDisable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.task.catalog-discovery.enabled", "false")
                .withProperty("app.task.catalog-discovery.batch-size", "1000")
                .withProperty("app.task.catalog-discovery.max-data-sources", "1000")
                .withProperty("app.task.catalog-discovery.lease-duration", "PT1H");
        CatalogDiscoveryConfigValues config = new CatalogDiscoveryConfigValues(new RuntimeConfigService(null, environment));

        assertThat(config.enabled()).isFalse();
        assertThat(config.batchSize()).isEqualTo(5);
        assertThat(config.maxDataSources()).isEqualTo(3);
        assertThat(config.leaseDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void explicitSourceIdsAreValidatedAndDeduplicated() {
        UUID id = UUID.randomUUID();
        String key = "app.task.catalog-discovery.source-ids";
        MockEnvironment environment = new MockEnvironment();
        CatalogDiscoveryConfigValues config = new CatalogDiscoveryConfigValues(new RuntimeConfigService(null, environment));

        assertThat(config.sourceIds()).isEmpty();
        environment.setProperty(key, id + ", " + id);
        assertThat(config.sourceIds()).containsExactly(id);
        environment.setProperty(key, id + ",invalid");
        assertThatThrownBy(config::sourceIds).isInstanceOf(IllegalArgumentException.class);
    }
}
