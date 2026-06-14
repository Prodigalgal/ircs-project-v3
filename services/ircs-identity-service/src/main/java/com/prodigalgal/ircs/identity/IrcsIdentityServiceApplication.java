package com.prodigalgal.ircs.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.prodigalgal.ircs")
public class IrcsIdentityServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IrcsIdentityServiceApplication.class, args);
    }
}

