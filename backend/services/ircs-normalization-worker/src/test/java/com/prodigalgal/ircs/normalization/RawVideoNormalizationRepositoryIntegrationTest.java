package com.prodigalgal.ircs.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.prodigalgal.ircs.common.aggregation.AggregationWorkPublisher;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Real PostgreSQL coverage for nullable classification and the normalization write transaction. */
@EnabledIfEnvironmentVariable(named = "IRCS_TEST_POSTGRES_URL", matches = ".+")
class RawVideoNormalizationRepositoryIntegrationTest {
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private HikariDataSource pool;
    private RawVideoNormalizationRepository repository;
    private DataSourceTransactionManager manager;
    private String schema;
    private UUID id;

    @BeforeEach
    void setUp() {
        String url = System.getenv("IRCS_TEST_POSTGRES_URL");
        String user = System.getenv("IRCS_TEST_POSTGRES_USER");
        String password = System.getenv("IRCS_TEST_POSTGRES_PASSWORD");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "normalization_write_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("create schema " + schema);
        pool = new HikariDataSource();
        pool.setJdbcUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(1000);
        pool.setConnectionInitSql("set search_path to " + schema);
        jdbc = new JdbcTemplate(pool);
        jdbc.execute("""
                create table standard_category(slug text primary key);
                create table raw_videos(
                    id uuid primary key, title text, alias_title text, season integer, subtitle text,
                    description text, year text, area text, raw_language_str text, remarks text, score numeric,
                    total_episodes text, duration text, douban_id text, tmdb_id text, imdb_id text,
                    rotten_tomatoes_id text, data_source_id uuid, data_hash text, raw_metadata jsonb,
                    locked_fields jsonb default '[]', category_code text, source_category_code text,
                    source_category_name text, normalization_status text default 'PENDING',
                    normalization_retry_count integer default 0, next_normalization_retry_time timestamptz,
                    actor_names jsonb default '[]', director_names jsonb default '[]',
                    area_codes jsonb default '[]', language_codes jsonb default '[]', genre_codes jsonb default '[]',
                    normalization_snapshot jsonb, updated_at timestamptz default now());
                """);
        id = UUID.randomUUID();
        jdbc.update("insert into raw_videos(id,title,description,data_hash,raw_metadata) values (?, ?, ?, ?, cast(? as jsonb))",
                id, "國王的演講", "音楽與繁體國語", "normalization-integration-v1",
                "{\"title\":\"國王的演講\",\"description\":\"音楽與繁體國語\",\"year\":\"2010\"}");
        repository = RawVideoNormalizationRepository.forTest(new NamedParameterJdbcTemplate(pool));
        manager = new DataSourceTransactionManager(pool);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
        if (admin != null && schema != null) {
            // This schema is generated and owned exclusively by the current test.
            admin.execute("drop schema " + schema + " cascade");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"movie", "legacy-category"})
    void writesWithoutCategoryHintsAndPreservesOnlyStableExistingCategory(String existing) {
        jdbc.update("update raw_videos set category_code=? where id=?", existing, id);
        markReady(patch(null, null));
        assertThat(category()).isEqualTo(existing.equals("movie") ? "movie" : null);
        assertReady();
    }

    @Test
    void writesWithoutAnyCategoryInsteadOfFailingOnAnUntypedNullParameter() {
        markReady(patch(null, null));
        assertThat(category()).isNull();
        assertReady();
    }

    @Test
    void infersCategoryFromSourceNameWhenSourceCodeIsMissing() {
        jdbc.update("insert into standard_category(slug) values ('movie')");
        markReady(patch(null, "电影"));
        assertThat(category()).isEqualTo("movie");
        assertThat(jdbc.queryForObject("select source_category_name from raw_videos where id=?", String.class, id))
                .isEqualTo("电影");
        assertReady();
    }

    @Test
    void missingTaxonomyEntryDoesNotRollBackNormalizedFieldsOrEraseKnownCategory() {
        jdbc.update("update raw_videos set category_code='movie' where id=?", id);
        markReady(patch("unmapped-source", "未知来源分类"));
        assertThat(category()).isEqualTo("movie");
        assertReady();
    }

    @Test
    void realServiceTransactionConvertsTextPersistsReadyAndPublishesAllHandoffs() {
        var mapper = JsonMapper.builder().build();
        var search = mock(RawSearchSyncPublisher.class);
        var enrich = mock(MetadataEnrichPublisher.class);
        var aggregation = mock(AggregationWorkPublisher.class);
        var service = new RawVideoNormalizationService(repository,
                new RawVideoTextNormalizer(mapper, new DescriptionMetadataExtractor(), new RawRelationAliasPolicy()),
                search, enrich, aggregation, mock(NormalizeVideoPublisher.class), mapper,
                mock(NormalizationConfigValues.class));
        var proxy = new ProxyFactory(service);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));

        var result = ((RawVideoNormalizationService) proxy.getProxy()).normalize(id, "normalization-integration-v1");

        assertThat(result.status()).isEqualTo("READY");
        assertReady();
        verify(search).publishIndex(id);
        verify(enrich).publish(id, "normalization-integration-v1");
        verify(aggregation).enqueue(id, "normalization-integration-v1", "normalization-ready");
        assertThat(pool.getHikariPoolMXBean().getActiveConnections()).isZero();
    }

    private void markReady(RawVideoPatch patch) {
        new TransactionTemplate(manager).executeWithoutResult(ignored -> repository.markReady(id, patch));
    }

    private void assertReady() {
        assertThat(jdbc.queryForObject("select normalization_status from raw_videos where id=?", String.class, id))
                .isEqualTo("READY");
        assertThat(jdbc.queryForObject("select title from raw_videos where id=?", String.class, id))
                .isEqualTo("国王的演讲");
        assertThat(jdbc.queryForObject("select description from raw_videos where id=?", String.class, id))
                .contains("音乐");
    }

    private String category() {
        return jdbc.queryForObject("select category_code from raw_videos where id=?", String.class, id);
    }

    private RawVideoPatch patch(String sourceCode, String sourceName) {
        return new RawVideoPatch("国王的演讲", null, null, null, "音乐与繁体国语", "2010",
                null, null, null, null, null, null, null, null, null, null,
                null, sourceCode, sourceName, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
