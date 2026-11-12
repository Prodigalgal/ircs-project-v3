package com.prodigalgal.ircs.migrator;

import java.sql.Connection;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import liquibase.Liquibase;
import liquibase.change.core.TagDatabaseChange;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseReleaseMarker implements ApplicationRunner {

    private static final Pattern RELEASE_VERSION = Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final System.Logger LOGGER = System.getLogger(DatabaseReleaseMarker.class.getName());

    private final ObjectProvider<BuildProperties> buildProperties;
    private final Environment environment;
    private final DataSource dataSource;

    public DatabaseReleaseMarker(ObjectProvider<BuildProperties> buildProperties, Environment environment, DataSource dataSource) {
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String version = releaseVersion();
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                    Liquibase liquibase = createMarkerLiquibase(version, resourceAccessor, database)) {
                if (liquibase.tagExists(version)) {
                    LOGGER.log(System.Logger.Level.INFO, "Database already has release tag {0}; leaving history unchanged", version);
                    return;
                }
                liquibase.update();
                if (!liquibase.tagExists(version)) {
                    throw new IllegalStateException("Liquibase update completed without recording release tag " + version);
                }
                LOGGER.log(System.Logger.Level.INFO, "Recorded database release tag {0}", version);
            }
        }
    }

    private String releaseVersion() {
        BuildProperties buildInfo = buildProperties.getIfAvailable();
        String buildVersion = buildInfo == null ? null : buildInfo.getVersion();
        String environmentVersion = environment.getProperty("APP_VERSION");
        if (buildVersion != null && !buildVersion.isBlank()
                && environmentVersion != null && !environmentVersion.isBlank()
                && !buildVersion.equals(environmentVersion)) {
            throw new IllegalStateException("APP_VERSION does not match Spring Boot build info version");
        }
        String version = buildVersion;
        if (version == null || version.isBlank()) {
            version = environmentVersion;
        }
        if (version == null || !RELEASE_VERSION.matcher(version).matches()) {
            throw new IllegalStateException("Database release marker requires a formal x.y.z version from build info or APP_VERSION");
        }
        return version;
    }

    private Liquibase createMarkerLiquibase(String version, ResourceAccessor resourceAccessor, Database database) {
        String changelogPath = "db/release-tags/" + version + ".yaml";
        DatabaseChangeLog changelog = new DatabaseChangeLog(changelogPath);
        ChangeSet changeSet = new ChangeSet(
                "database-release-" + version,
                "ircs",
                false,
                false,
                changelogPath,
                null,
                null,
                database.getObjectQuotingStrategy(),
                changelog);
        TagDatabaseChange tag = new TagDatabaseChange();
        tag.setTag(version);
        changeSet.addChange(tag);
        changelog.addChangeSet(changeSet);
        return new Liquibase(changelog, resourceAccessor, database);
    }

}
