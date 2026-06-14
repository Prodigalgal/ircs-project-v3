package com.prodigalgal.ircs.normalization;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class NormalizationSearchSyncConfiguration {

    @Bean
    @Primary
    SearchSyncOutboxWriter normalizationSearchSyncOutboxWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.application.name:ircs-normalization-worker}") String defaultSourceService) {
        return new SearchSyncOutboxWriter(jdbcTemplate, defaultSourceService);
    }
}
