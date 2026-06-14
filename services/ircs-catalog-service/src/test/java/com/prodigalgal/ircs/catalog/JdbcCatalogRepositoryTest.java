package com.prodigalgal.ircs.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCatalogRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final JdbcCatalogRepository repository = new JdbcCatalogRepository(jdbcTemplate, namedParameterJdbcTemplate);

    @Test
    void traceRawLanguageUsesCurrentRawVideosTable() {
        when(namedParameterJdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class))).thenReturn(List.of());

        repository.traceRawLanguage(UUID.randomUUID());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(namedParameterJdbcTemplate).query(
                sql.capture(),
                any(MapSqlParameterSource.class),
                any(RowMapper.class));

        assertThat(sql.getValue()).contains("join raw_videos v on vrl.video_id = v.id");
        assertThat(sql.getValue()).doesNotContain("join videos v");
    }

    @Test
    void seedMethodsAreIdempotentAndFillUnmappedRawAliases() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:catalog-seed-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate realJdbc = new JdbcTemplate(dataSource);
        NamedParameterJdbcTemplate realNamed = new NamedParameterJdbcTemplate(dataSource);
        createSeedSchema(realJdbc);
        JdbcCatalogRepository realRepository = new JdbcCatalogRepository(realJdbc, realNamed);

        JdbcCatalogRepository.SeedResult category = realRepository.seedStandardCategory("电影", "movie");
        JdbcCatalogRepository.SeedResult categoryAgain = realRepository.seedStandardCategory("电影", "edited-movie");
        JdbcCatalogRepository.SeedResult genre = realRepository.seedStandardGenre("剧情");
        JdbcCatalogRepository.SeedResult area = realRepository.seedStandardArea("CN", "中国", "Asia");
        JdbcCatalogRepository.SeedResult language = realRepository.seedStandardLanguage("zh", "普通话", "Mandarin", "普通话");

        assertThat(category.inserted()).isTrue();
        assertThat(categoryAgain.inserted()).isFalse();
        assertThat(realJdbc.queryForObject("select count(*) from standard_category where name = '电影'", Integer.class))
                .isEqualTo(1);
        assertThat(realJdbc.queryForObject("select slug from standard_category where name = '电影'", String.class))
                .isEqualTo("movie");

        realJdbc.update(
                "insert into raw_languages (id, created_at, updated_at, version, source_value, standard_language_id) "
                        + "values (random_uuid(), now(), now(), 0, '国语', null)");
        JdbcCatalogRepository.SeedResult rawLanguage = realRepository.seedRawLanguageAlias("国语", language.id());
        JdbcCatalogRepository.SeedResult rawLanguageAgain = realRepository.seedRawLanguageAlias("国语", UUID.randomUUID());
        JdbcCatalogRepository.SeedResult rawArea = realRepository.seedRawAreaAlias("中国大陆", area.id());
        JdbcCatalogRepository.SeedResult rawGenre = realRepository.seedRawGenreAlias("剧情", genre.id());
        JdbcCatalogRepository.SeedResult dataSourceSeed = realRepository.seedDataSource(
                "光速资源",
                "https://api.guangsuapi.com",
                "/api.php/provide/vod/josn",
                "{\"ac\":\"list\",\"pg\":\"{page}\"}",
                "{\"ac\":\"detail\",\"ids\":\"{ids}\"}",
                "{\"detail_mapping\":{\"title\":{\"path\":\"$.list[0].vod_name\"}}}");
        JdbcCatalogRepository.SeedResult dataSourceAgain = realRepository.seedDataSource(
                "光速资源",
                "https://changed.example",
                "/changed",
                "{\"ac\":\"changed\"}",
                "{\"ac\":\"changed\"}",
                "{\"detail_mapping\":{\"title\":{\"path\":\"changed\"}}}");

        assertThat(rawLanguage.updated()).isTrue();
        assertThat(rawLanguageAgain.updated()).isFalse();
        assertThat(rawArea.inserted()).isTrue();
        assertThat(rawGenre.inserted()).isTrue();
        assertThat(dataSourceSeed.inserted()).isTrue();
        assertThat(dataSourceAgain.inserted()).isFalse();
        assertThat(dataSourceAgain.updated()).isFalse();
        assertThat(realJdbc.queryForObject("select count(*) from raw_languages where source_value = '国语'", Integer.class))
                .isEqualTo(1);
        assertThat(realJdbc.queryForObject("select count(*) from raw_areas where source_value = '中国大陆'", Integer.class))
                .isEqualTo(1);
        assertThat(realJdbc.queryForObject("select count(*) from raw_genres where source_value = '剧情'", Integer.class))
                .isEqualTo(1);
        assertThat(realJdbc.queryForObject("select count(*) from data_sources where name = '光速资源'", Integer.class))
                .isEqualTo(1);
        assertThat(realJdbc.queryForObject("select base_url from data_sources where name = '光速资源'", String.class))
                .isEqualTo("https://api.guangsuapi.com");
        assertThat(realRepository.listDataSourceSeedMappings())
                .singleElement()
                .satisfies(row -> assertThat(row.fieldMapping()).contains("vod_name"));

        JdbcCatalogRepository.SeedResult rawCategory = realRepository.syncRawCategory(
                dataSourceSeed.id(),
                "1",
                "电影",
                "电影");
        JdbcCatalogRepository.SeedResult rawCategoryAgain = realRepository.syncRawCategory(
                dataSourceSeed.id(),
                "1",
                "动作片",
                "电影");
        UUID shortDramaId = realRepository.seedStandardCategory("短剧", "short-drama").id();
        UUID otherId = realRepository.seedStandardCategory("其他", "other").id();
        realJdbc.update(
                "update raw_category set category_id = ? where data_source_id = ? and source_code = ?",
                shortDramaId,
                dataSourceSeed.id(),
                "1");
        JdbcCatalogRepository.SeedResult rawCategoryManualMapping = realRepository.syncRawCategory(
                dataSourceSeed.id(),
                "1",
                "电视剧",
                "电视剧");

        assertThat(rawCategory.inserted()).isTrue();
        assertThat(rawCategoryAgain.updated()).isTrue();
        assertThat(rawCategoryManualMapping.updated()).isTrue();
        assertThat(realJdbc.queryForObject("select source_name from raw_category where source_code = '1'", String.class))
                .isEqualTo("电视剧");
        assertThat(realJdbc.queryForObject("select category_id from raw_category where source_code = '1'", UUID.class))
                .isEqualTo(shortDramaId);

        JdbcCatalogRepository.SeedResult unknownCategory = realRepository.syncRawCategory(
                dataSourceSeed.id(),
                "99",
                "未知分类",
                null);
        assertThat(unknownCategory.inserted()).isTrue();
        assertThat(realJdbc.queryForObject("select category_id from raw_category where source_code = '99'", UUID.class))
                .isEqualTo(otherId);
    }

    private void createSeedSchema(JdbcTemplate jdbc) {
        jdbc.execute(
                """
                create table standard_category (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    name varchar(100) unique not null,
                    slug varchar(100) unique not null
                )
                """);
        jdbc.execute(
                """
                create table standard_genre (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    name varchar(100) unique not null
                )
                """);
        jdbc.execute(
                """
                create table standard_areas (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    name varchar(100) unique not null,
                    code varchar(10) unique not null,
                    region varchar(50)
                )
                """);
        jdbc.execute(
                """
                create table standard_languages (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    name varchar(100) unique not null,
                    code varchar(20),
                    english_name varchar(100),
                    native_name varchar(100)
                )
                """);
        jdbc.execute(
                """
                create table raw_genres (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    source_value varchar(100) unique not null,
                    standard_genre_id uuid
                )
                """);
        jdbc.execute(
                """
                create table raw_areas (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    source_value varchar(100) unique not null,
                    standard_area_id uuid
                )
                """);
        jdbc.execute(
                """
                create table raw_languages (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    source_value varchar(100) unique not null,
                    standard_language_id uuid
                )
                """);
        jdbc.execute(
                """
                create domain if not exists jsonb as varchar
                """);
        jdbc.execute(
                """
                create table data_sources (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    name varchar(255) unique not null,
                    base_url varchar(1024) not null,
                    list_path varchar(1024) not null,
                    list_params jsonb,
                    detail_path varchar(1024) not null,
                    detail_params jsonb,
                    field_mapping jsonb
                )
                """);
        jdbc.execute(
                """
                create table raw_category (
                    id uuid primary key,
                    created_at timestamp not null,
                    updated_at timestamp not null,
                    version bigint,
                    data_source_id uuid not null,
                    source_code varchar(100) not null,
                    source_name varchar(255),
                    category_id uuid,
                    unique (data_source_id, source_code)
                )
                """);
    }
}
