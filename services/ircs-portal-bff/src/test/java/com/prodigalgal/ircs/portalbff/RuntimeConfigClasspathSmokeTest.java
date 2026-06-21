package com.prodigalgal.ircs.portalbff;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class RuntimeConfigClasspathSmokeTest {

    @Test
    void runtimeConfigFallsBackWithoutJdbcOnPortalBffClasspath() {
        new ApplicationContextRunner()
                .withUserConfiguration(RuntimeConfigScanConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("systemEnvironment", Map.of("APP_DEMO_LIMIT", "31"))))
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(RuntimeConfigService.class);
                    org.assertj.core.api.Assertions.assertThat(context)
                            .doesNotHaveBean("runtimeConfigRepository");
                    org.assertj.core.api.Assertions.assertThat(context.getBean(RuntimeConfigService.class)
                                    .intValue("app.demo.limit", 12))
                            .isEqualTo(31);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("com.prodigalgal.ircs.common.config")
    static class RuntimeConfigScanConfiguration {
    }
}
