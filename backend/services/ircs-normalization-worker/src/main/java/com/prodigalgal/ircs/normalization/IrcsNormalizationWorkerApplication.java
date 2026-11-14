package com.prodigalgal.ircs.normalization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.ImportRuntimeHints;

@EnableScheduling
@ImportRuntimeHints(NormalizationRuntimeHints.class)
@SpringBootApplication(scanBasePackages = "com.prodigalgal.ircs")
public class IrcsNormalizationWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrcsNormalizationWorkerApplication.class, args);
    }
}
