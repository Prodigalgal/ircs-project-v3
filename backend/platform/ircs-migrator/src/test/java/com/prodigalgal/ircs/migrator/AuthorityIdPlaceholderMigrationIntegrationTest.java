package com.prodigalgal.ircs.migrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
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
class AuthorityIdPlaceholderMigrationIntegrationTest {
    private static final String RESOURCE = "db/changelog/2026/11/16-01-clear-authoritative-id-placeholders.sql";
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(System.getenv("IRCS_TEST_POSTGRES_URL"),
                System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
        admin = new JdbcTemplate(source);
        schema = "migration_ids_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        var scoped = new DriverManagerDataSource(source.getUrl(), source.getUsername(), source.getPassword());
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        scoped.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(scoped);
        for (String table : List.of("raw_videos", "unified_videos")) {
            jdbc.execute("create table " + table + """
                    (id uuid primary key,title text,actor_names jsonb,locked_fields jsonb,
                     douban_id text,tmdb_id text,imdb_id text,rotten_tomatoes_id text,
                     source_vid text,source_category_code text,raw_metadata jsonb,pipeline_status text)
                    """);
        }
        jdbc.execute("""
                create table raw_video_unified_video(raw_video_id uuid references raw_videos(id),
                    unified_video_id uuid references unified_videos(id),primary key(raw_video_id,unified_video_id));
                create table member_video_favorites(video_id uuid references unified_videos(id));
                create table member_watch_history(video_id uuid references unified_videos(id));
                """);
    }

    @AfterEach
    void tearDown() {
        if (admin != null && schema != null) {
            // This randomly named schema is created and owned exclusively by this test.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @Test
    void clearsOnlyConfirmedPlaceholdersAndPreservesLocksEvidenceReferencesAndLiquibaseIdempotence() throws Exception {
        for (String table : List.of("raw_videos", "unified_videos")) {
            for (String placeholder : List.of("", " ", "　", "0", "0000", "-1", " NuLl ", "NONE", "undefined",
                    "unknown", "n/a", "na", "无", "暂无", "未知", "未填写")) {
                insert(table, placeholder, placeholder, placeholder, placeholder, "[]");
            }
            insert(table, null, null, " TT0000000 ", null, "[]");
            insert(table, "123", "456", "tt1234567", "Real_Title", "[]");
            insert(table, "not-a-valid-id", "123/456", "tt123", "unrecognized-but-not-placeholder", "[]");
            insert(table, "0", "0", "tt0000000", "0",
                    "[\"doubanId\",\"tmdbId\",\"imdbId\",\"rottenTomatoesId\"]");
            insert(table, "0", "0", "tt0000000", "0",
                    "{\"doubanId\":true,\"tmdbId\":true,\"imdbId\":true,\"rottenTomatoesId\":true}");
        }
        UUID raw = insert("raw_videos", "0", "0", "tt0000000", "0", "[]");
        UUID unified = insert("unified_videos", "0", "0", "tt0000000", "0", "[]");
        jdbc.update("insert into raw_video_unified_video values (?,?)", raw, unified);
        jdbc.update("insert into member_video_favorites values (?)", unified);
        jdbc.update("insert into member_watch_history values (?)", unified);
        var rawEvidence = jdbc.queryForList("select id,title,actor_names,source_vid,source_category_code,raw_metadata,pipeline_status from raw_videos order by id");
        var unifiedEvidence = jdbc.queryForList("select id,title,actor_names,source_vid,source_category_code,raw_metadata,pipeline_status from unified_videos order by id");

        migrateTwice();

        for (String table : List.of("raw_videos", "unified_videos")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where douban_id='123' and tmdb_id='456' and imdb_id='tt1234567' and rotten_tomatoes_id='Real_Title'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where douban_id='not-a-valid-id' and imdb_id='tt123'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where douban_id='0' and tmdb_id='0' and imdb_id='tt0000000' and rotten_tomatoes_id='0'", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where locked_fields='[]'::jsonb and (douban_id is not null or tmdb_id is not null or imdb_id is not null or rotten_tomatoes_id is not null)", Integer.class)).isEqualTo(2);
        }
        assertThat(jdbc.queryForList("select id,title,actor_names,source_vid,source_category_code,raw_metadata,pipeline_status from raw_videos order by id")).isEqualTo(rawEvidence);
        assertThat(jdbc.queryForList("select id,title,actor_names,source_vid,source_category_code,raw_metadata,pipeline_status from unified_videos order by id")).isEqualTo(unifiedEvidence);
        assertThat(jdbc.queryForObject("select count(*) from raw_video_unified_video where raw_video_id=? and unified_video_id=?", Integer.class, raw, unified)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from member_video_favorites where video_id=?", Integer.class, unified)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from member_watch_history where video_id=?", Integer.class, unified)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog where id='1785900000005-clear-authoritative-id-placeholders'", Integer.class)).isEqualTo(1);
    }

    private UUID insert(String table, String douban, String tmdb, String imdb, String rt, String locks) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + table + " values (?, '原题', '[\"罗予彤\",\"孙乾钊\"]'::jsonb,cast(? as jsonb),?,?,?,?, '0','0','{\"doubanId\":0,\"source_vid\":0,\"rawTypeId\":0}'::jsonb,'READY')",
                id, locks, douban, tmdb, imdb, rt);
        return id;
    }

    private void migrateTwice() throws Exception {
        try (Connection connection = DriverManager.getConnection(System.getenv("IRCS_TEST_POSTGRES_URL"),
                    System.getenv("IRCS_TEST_POSTGRES_USER"), System.getenv("IRCS_TEST_POSTGRES_PASSWORD"));
                var accessor = new ClassLoaderResourceAccessor()) {
            connection.setSchema(schema);
            try (var statement = connection.createStatement()) {
                statement.execute("set lock_timeout='7s'");
                statement.execute("set statement_timeout='9s'");
            }
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            try (var liquibase = new Liquibase(RESOURCE, accessor, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                liquibase.update(new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement(); var result = statement.executeQuery("select current_setting('lock_timeout'),current_setting('statement_timeout')")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("7s");
                    assertThat(result.getString(2)).isEqualTo("9s");
                }
            }
        }
    }
}
