package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.config.SystemConfigValkeyCache;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SystemConfigRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigValkeyCache valkeyCache;

    SystemConfigRepository(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.system-config.valkey-cache.key-prefix:ircs:system-config:v1}") String keyPrefix,
            @Value("${app.system-config.valkey-cache.ttl:PT12H}") Duration ttl,
            @Value("${app.system-config.local-cache.ttl:PT5M}") Duration localTtl) {
        this.jdbcTemplate = jdbcTemplate;
        this.valkeyCache = new SystemConfigValkeyCache(redisTemplateProvider, keyPrefix, ttl, localTtl);
    }

    static SystemConfigRepository forTest(JdbcTemplate jdbcTemplate) {
        return new SystemConfigRepository(
                jdbcTemplate,
                null,
                SystemConfigValkeyCache.DEFAULT_KEY_PREFIX,
                SystemConfigValkeyCache.DEFAULT_TTL,
                SystemConfigValkeyCache.DEFAULT_LOCAL_TTL);
    }

    Optional<String> findValue(String key) {
        return valkeyCache.findValue(key, () -> findValueFromDatabase(key));
    }

    void evict(String key) {
        valkeyCache.evictLocal(key);
    }

    private Optional<String> findValueFromDatabase(String key) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "select config_value from system_configs where config_key = ?",
                    String.class,
                    key));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }
}
