package com.prodigalgal.ircs.content.config;

import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ContentConfigValues {

    public static final String SCRAPER_BASE_URL_KEY = "app.content.internal.scraper-base-url";
    static final String SCRAPER_BASE_URL_ALIAS = "app.content.scraper-base-url";
    static final String SCRAPER_BASE_URL_ENV_ALIAS = "APP_CONTENT_SCRAPER_BASE_URL";
    static final String DEFAULT_SCRAPER_BASE_URL = "http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080";

    private final Environment environment;
    private final SystemConfigRepository repository;

    ContentConfigValues(Environment environment, SystemConfigRepository repository) {
        this.environment = environment;
        this.repository = repository;
    }

    public String scraperBaseUrl() {
        return value(
                DEFAULT_SCRAPER_BASE_URL,
                SCRAPER_BASE_URL_KEY,
                SCRAPER_BASE_URL_ALIAS,
                SCRAPER_BASE_URL_ENV_ALIAS);
    }

    private String value(String defaultValue, String key, String... aliases) {
        String[] runtimeKeys = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(key),
                        Arrays.stream(aliases))
                .toArray(String[]::new);
        return RuntimeInjectedConfig.find(environment, runtimeKeys)
                .or(() -> repository.findValue(key))
                .filter(StringUtils::hasText)
                .orElse(defaultValue);
    }
}
