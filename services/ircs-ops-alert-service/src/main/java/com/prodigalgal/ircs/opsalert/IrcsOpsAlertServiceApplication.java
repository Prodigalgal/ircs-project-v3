package com.prodigalgal.ircs.opsalert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prodigalgal.ircs")
public class IrcsOpsAlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IrcsOpsAlertServiceApplication.class, args);
    }
}
