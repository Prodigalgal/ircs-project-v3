package com.prodigalgal.ircs.opsalert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.prodigalgal.ircs")
@ConfigurationPropertiesScan(basePackages = "com.prodigalgal.ircs.opsalert")
public class IrcsOpsAlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrcsOpsAlertServiceApplication.class, args);
    }
}
