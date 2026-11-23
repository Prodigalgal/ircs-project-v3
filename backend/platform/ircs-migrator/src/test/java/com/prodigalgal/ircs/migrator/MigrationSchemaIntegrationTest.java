package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Applies the actual complete schema before expensive Native builds, without hand-written table substitutes. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class MigrationSchemaIntegrationTest {
    @Test
    void completeChangelogAndCatalogFunctionsApplyIdempotentlyToAFreshDatabase() throws Exception {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String database = "ircs_migration_" + UUID.randomUUID().toString().replace("-", "");
        URI address = URI.create(url.substring("jdbc:".length()));
        String isolatedUrl = "jdbc:" + new URI(address.getScheme(), address.getAuthority(), "/" + database,
                address.getQuery(), null);
        admin.execute("create database " + database);
        try {
            var dataSource = new DriverManagerDataSource(isolatedUrl, user, password);
            var migration = new SpringLiquibase();
            migration.setResourceLoader(new DefaultResourceLoader());
            migration.setDataSource(dataSource);
            migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            migration.afterPropertiesSet();
            var jdbc = new JdbcTemplate(dataSource);
            Integer applied = jdbc.queryForObject("select count(*) from databasechangelog", Integer.class);
            assertThat(applied).isGreaterThan(10);
            assertThat(jdbc.queryForObject("select to_regclass('catalog_external_ids') is not null", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("select resource_available from catalog_resource_inventory(?, 2)",
                    Boolean.class, UUID.randomUUID())).isFalse();
            assertThat(jdbc.queryForObject("select catalog_episode_number('S02E12')", Integer.class)).isEqualTo(12);
            assertThat(jdbc.queryForObject("select count(*) from databasechangelog where tag='5.0.0'", Integer.class)).isOne();
            migration.afterPropertiesSet();
            assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class)).isEqualTo(applied);
            try (var fixture = Objects.requireNonNull(getClass().getResourceAsStream("/catalog-v5-relationships.sql"))) {
                jdbc.execute(new String(fixture.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            admin.execute("drop database " + database);
        }
    }
}
