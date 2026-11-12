package com.prodigalgal.ircs.contentsafety;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestConstructor;

@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ContentSafetyApplicationContextTest {

    private final ApplicationContext context;

    ContentSafetyApplicationContextTest(ApplicationContext context) {
        this.context = context;
    }

    @Test
    void usesHttpTelemetryConsistentlyWithTheNativeBuild() {
        assertThat(context.getBeansOfType(OtlpHttpSpanExporter.class)).hasSize(1);
        assertThat(context.getBeansOfType(OtlpGrpcSpanExporter.class)).isEmpty();
    }
}
