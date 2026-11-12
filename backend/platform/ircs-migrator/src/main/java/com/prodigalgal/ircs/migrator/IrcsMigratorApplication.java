package com.prodigalgal.ircs.migrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ImportRuntimeHints(MigratorRuntimeHints.class)
public class IrcsMigratorApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IrcsMigratorApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        ConfigurableApplicationContext context = application.run(args);
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }
}
