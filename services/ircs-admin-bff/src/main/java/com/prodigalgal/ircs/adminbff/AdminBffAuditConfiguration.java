package com.prodigalgal.ircs.adminbff;

import com.prodigalgal.ircs.common.audit.BffRequestAuditWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminBffAuditConfiguration {

    @Bean
    BffRequestAuditWriter bffRequestAuditWriter(
            @Value("${app.bff.audit.enabled:true}") boolean enabled,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.application.name:ircs-admin-bff}") String requestSource) {
        return new BffRequestAuditWriter(enabled, datasourceUrl, username, password, requestSource);
    }
}
