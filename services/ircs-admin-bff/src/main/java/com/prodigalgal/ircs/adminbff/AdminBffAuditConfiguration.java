package com.prodigalgal.ircs.adminbff;

import com.prodigalgal.ircs.common.audit.BffRequestAuditWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class AdminBffAuditConfiguration {

    @Bean
    BffRequestAuditWriter bffRequestAuditWriter(
            JdbcTemplate jdbcTemplate,
            @Value("${app.bff.audit.enabled:true}") boolean enabled,
            @Value("${spring.application.name:ircs-admin-bff}") String requestSource) {
        return new BffRequestAuditWriter(enabled, jdbcTemplate, requestSource);
    }
}
