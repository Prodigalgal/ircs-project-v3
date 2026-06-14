package com.prodigalgal.ircs.aggregation;

import com.prodigalgal.ircs.common.search.SearchSyncOutboxWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class AggregationSearchSyncConfiguration {

    @Bean
    @Primary
    SearchSyncOutboxWriter aggregationSearchSyncOutboxWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${spring.application.name:ircs-aggregation-worker}") String defaultSourceService) {
        return new SearchSyncOutboxWriter(jdbcTemplate, defaultSourceService);
    }
}
