package com.prodigalgal.ircs.apigateway;

import com.prodigalgal.ircs.common.config.JdbcSystemConfigRepositorySupport;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GatewaySystemConfigRepository extends JdbcSystemConfigRepositorySupport {

    public GatewaySystemConfigRepository(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.system-config.valkey-cache.key-prefix:ircs:system-config:v1}") String keyPrefix,
            @Value("${app.system-config.valkey-cache.ttl:PT12H}") Duration ttl,
            @Value("${app.system-config.local-cache.ttl:PT5M}") Duration localTtl) {
        super(jdbcTemplate, redisTemplateProvider, keyPrefix, ttl, localTtl);
    }
}
