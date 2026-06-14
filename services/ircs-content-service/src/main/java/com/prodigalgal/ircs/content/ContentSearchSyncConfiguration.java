package com.prodigalgal.ircs.content;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class ContentSearchSyncConfiguration {

    @Bean
    @Primary
    SearchSyncOutboxWriter contentSearchSyncOutboxWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.application.name:ircs-content-service}") String defaultSourceService) {
        return new SearchSyncOutboxWriter(jdbcTemplate, defaultSourceService);
    }
}
