package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class MagnetProviderMigrationIntegrationTest {
    private static final String PROVIDER_MIGRATION =
            "db/changelog/2026/11/28-01-add-magnet-provider-therarbg.sql";
    private static final String TRACKER_MIGRATION =
            "db/changelog/2026/11/28-02-configure-magnet-trackers.sql";
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        admin = new JdbcTemplate(source);
        schema = "migration_magnets_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        var scoped = new DriverManagerDataSource(source.getUrl(), source.getUsername(), source.getPassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        scoped.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(scoped);
        jdbc.execute("""
                create table magnet_providers (
                    id uuid primary key, created_at timestamptz not null default now(),
                    updated_at timestamptz not null default now(),
                    version bigint, code varchar(64) not null unique, name varchar(100) not null,
                    provider_type varchar(50) not null, base_url varchar(500) not null,
                    enabled boolean not null, priority integer not null, risk_level varchar(50) not null,
                    supported_external_ids jsonb not null, min_delay_ms integer not null,
                    max_delay_ms integer not null, timeout_ms integer not null, result_limit integer not null,
                    auto_approve_allowed boolean not null, content_policy varchar(500)
                )
                """);
        jdbc.execute("""
                create table system_configs (
                    id uuid primary key, created_at timestamp not null, updated_at timestamp not null,
                    version bigint, config_key varchar(100) not null unique,
                    config_value varchar(1024), description varchar(255)
                )
                """);
        jdbc.update("""
                insert into magnet_providers (
                    id,created_at,updated_at,version,code,name,provider_type,base_url,enabled,priority,
                    risk_level,supported_external_ids,min_delay_ms,max_delay_ms,timeout_ms,result_limit,
                    auto_approve_allowed,content_policy
                ) values (?,now(),now(),4,'thepiratebay_org','Custom retired frontend','THE_PIRATE_BAY_FRONTEND',
                    'https://custom.example',true,77,'MEDIUM','[]'::jsonb,25,50,5000,9,true,'operator policy')
                """, UUID.randomUUID());
        insertConfig("app.magnet.real-provider.allowlist", "YTS_BZ,EXT_TO,CUSTOM_SOURCE,THE_PIRATE_BAY_FRONTEND");
        insertConfig("app.magnet.traffic.provider-gap-ms",
                "YTS_BZ=700,EXT_TO=999,CUSTOM_SOURCE=123,THE_PIRATE_BAY_FRONTEND=5");
        insertConfig("app.magnet.tracker-list", "udp://operator.example:80/announce");
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void mergesOperatorSettingsSeedsManualReviewProviderAndPreservesRetiredProviderData() throws Exception {
        migrate(PROVIDER_MIGRATION);
        migrate(TRACKER_MIGRATION);

        assertThat(jdbc.queryForObject("select enabled from magnet_providers where code='therarbg'", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select auto_approve_allowed from magnet_providers where code='therarbg'",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.real-provider.allowlist")).isEqualTo("YTS_BZ,CUSTOM_SOURCE,THE_RARBG");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.traffic.provider-gap-ms")).isEqualTo(
                        "YTS_BZ=700,CUSTOM_SOURCE=123,THE_RARBG=4000");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.tracker-list")).isEqualTo("udp://operator.example:80/announce");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.tracker-append-enabled")).isEqualTo("true");
        assertThat(jdbc.queryForObject("select enabled from magnet_providers where code='thepiratebay_org'",
                Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select name from magnet_providers where code='thepiratebay_org'",
                String.class)).isEqualTo("Custom retired frontend");
        assertThat(jdbc.queryForObject("select priority from magnet_providers where code='thepiratebay_org'",
                Integer.class)).isEqualTo(77);
    }

    @Test
    void leavesExistingProviderConfigurationAndProviderSpecificGapUntouched() throws Exception {
        jdbc.update("""
                insert into magnet_providers (
                    id,created_at,updated_at,version,code,name,provider_type,base_url,enabled,priority,
                    risk_level,supported_external_ids,min_delay_ms,max_delay_ms,timeout_ms,result_limit,
                    auto_approve_allowed,content_policy
                ) values (?,now(),now(),8,'therarbg','Operator name','CUSTOM_TYPE','https://operator.example',
                    false,91,'LOW','[\"TITLE\"]'::jsonb,100,200,3000,7,true,'operator policy')
                """, UUID.randomUUID());
        jdbc.update("update system_configs set config_value=? where config_key=?",
                "BASE=100,THE_RARBG=8765,OTHER=50", "app.magnet.traffic.provider-gap-ms");
        jdbc.update("update system_configs set config_value=? where config_key=?",
                "THE_RARBG,CUSTOM_SOURCE,EXT_TO", "app.magnet.real-provider.allowlist");
        insertConfig("app.magnet.tracker-append-enabled", "false");
        migrate(PROVIDER_MIGRATION);
        migrate(TRACKER_MIGRATION);

        assertThat(jdbc.queryForMap("select name,provider_type,base_url,enabled,priority,auto_approve_allowed,content_policy from magnet_providers where code='therarbg'"))
                .containsEntry("name", "Operator name")
                .containsEntry("provider_type", "CUSTOM_TYPE")
                .containsEntry("base_url", "https://operator.example")
                .containsEntry("enabled", false)
                .containsEntry("priority", 91)
                .containsEntry("auto_approve_allowed", true)
                .containsEntry("content_policy", "operator policy");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.traffic.provider-gap-ms")).isEqualTo("BASE=100,THE_RARBG=8765,OTHER=50");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.real-provider.allowlist")).isEqualTo("THE_RARBG,CUSTOM_SOURCE");
        assertThat(jdbc.queryForObject("select config_value from system_configs where config_key=?", String.class,
                "app.magnet.tracker-append-enabled")).isEqualTo("false");
    }

    private void insertConfig(String key, String value) {
        jdbc.update("insert into system_configs (id,created_at,updated_at,version,config_key,config_value,description) values (?,now(),now(),0,?,?,?)",
                UUID.randomUUID(), key, value, "operator setting");
    }

    private void migrate(String resource) throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
                var accessor = new ClassLoaderResourceAccessor()) {
            connection.setSchema(schema);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var liquibase = new Liquibase(resource, accessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }
}
