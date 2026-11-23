package com.prodigalgal.ircs.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.common.cache.CacheRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class PortalStandardNameCacheTest {

    @Test
    void resolvesStandardNamesFromTtlCache() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource("portal_standard_name_cache"));
        createDictionaryTables(jdbcTemplate);
        jdbcTemplate.update("insert into catalog_genres(code, name) values ('drama', '剧情')");
        jdbcTemplate.update("insert into catalog_regions(code, name) values ('cn', '中国大陆')");
        jdbcTemplate.update("insert into catalog_languages(code, name) values ('zh', '国语')");
        PortalStandardNameCache cache = new PortalStandardNameCache(
                new NamedParameterJdbcTemplate(jdbcTemplate),
                new CacheRegistry(),
                Duration.ofMinutes(30));

        assertThat(cache.resolveGenreNames(List.of("drama", "missing"))).containsExactly("missing", "剧情");
        assertThat(cache.resolveAreaNames(List.of("cn"))).containsExactly("中国大陆");
        assertThat(cache.resolveLanguageNames(List.of("zh"))).containsExactly("国语");

        jdbcTemplate.update("update catalog_genres set name = '故事' where code = 'drama'");

        assertThat(cache.resolveGenreNames(List.of("drama"))).containsExactly("剧情");
    }

    private SingleConnectionDataSource dataSource(String name) {
        return new SingleConnectionDataSource(
                "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                true);
    }

    private void createDictionaryTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("create table catalog_genres(code varchar(100) primary key, name varchar(255))");
        jdbcTemplate.execute("create table catalog_regions(code varchar(100) primary key, name varchar(255))");
        jdbcTemplate.execute("create table catalog_languages(code varchar(100) primary key, name varchar(255))");
    }
}
