package com.prodigalgal.ircs.migrator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** An isolated database running the same changelog as the deployable migrator. */
public final class CatalogTestDatabase implements AutoCloseable {
    private final JdbcTemplate admin;
    private final String name;
    private final String url;
    private final DataSource dataSource;

    private CatalogTestDatabase(JdbcTemplate admin, String name, String url, DataSource dataSource) {
        this.admin = admin;
        this.name = name;
        this.url = url;
        this.dataSource = dataSource;
    }

    public static CatalogTestDatabase open() throws LiquibaseException, URISyntaxException {
        String base = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(base, user, password));
        String name = "ircs_catalog_test_" + UUID.randomUUID().toString().replace("-", "");
        URI address = URI.create(base.substring("jdbc:".length()));
        String url = "jdbc:" + new URI(address.getScheme(), address.getAuthority(), "/" + name,
                address.getQuery(), null);
        DataSource dataSource = new DriverManagerDataSource(url, user, password);
        var database = new CatalogTestDatabase(admin, name, url, dataSource);
        admin.execute("create database " + name);
        try {
            var migration = new SpringLiquibase();
            migration.setResourceLoader(new DefaultResourceLoader());
            migration.setDataSource(dataSource);
            migration.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            migration.afterPropertiesSet();
            return database;
        } catch (LiquibaseException | RuntimeException ex) {
            database.close();
            throw ex;
        }
    }

    public String url() {
        return url;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        admin.execute("drop database " + name + " with (force)");
    }
}
