package com.prodigalgal.ircs.common.config;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class RuntimeConfigRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigValkeyCache cache;

    public RuntimeConfigRepository(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.system-config.valkey-cache.key-prefix:ircs:system-config:v1}") String keyPrefix,
            @Value("${app.system-config.valkey-cache.ttl:PT12H}") Duration remoteTtl,
            @Value("${app.runtime-config.local-cache.ttl:PT2S}") Duration localTtl) {
        this.jdbcTemplate = jdbcTemplate;
        this.cache = new SystemConfigValkeyCache(redisTemplateProvider, keyPrefix, remoteTtl, localTtl);
    }

    public Optional<String> findValue(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        String normalizedKey = key.trim();
        return cache.findValue(normalizedKey, () -> findValueFromDatabase(normalizedKey));
    }

    public void evict(String key) {
        if (StringUtils.hasText(key)) {
            cache.evictLocal(key.trim());
        }
    }

    private Optional<String> findValueFromDatabase(String key) {
        return jdbcTemplate.query(
                "select config_value from system_configs where config_key = ?",
                ps -> ps.setString(1, key),
                rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty());
    }
}
