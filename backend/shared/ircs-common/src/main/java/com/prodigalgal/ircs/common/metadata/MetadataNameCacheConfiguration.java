package com.prodigalgal.ircs.common.metadata;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
public class MetadataNameCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetadataNameCache metadataNameValkeyCache(
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            @Value("${app.metadata-name-cache.key-prefix:" + MetadataNameCache.DEFAULT_KEY_PREFIX + "}")
                    String keyPrefix,
            @Value("${app.metadata-name-cache.ttl:PT12H}") Duration ttl) {
        return new MetadataNameCache(redisTemplateProvider, keyPrefix, ttl);
    }
}
