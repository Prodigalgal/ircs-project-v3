package com.prodigalgal.ircs.adminbff;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AdminBffDataSourceWarmup {

    private static final Logger log = LoggerFactory.getLogger(AdminBffDataSourceWarmup.class);

    private final DataSource dataSource;

    @Value("${app.bff.datasource-warmup.enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    void warmup() {
        if (!enabled) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                log.warn("admin.bff.datasource.warmup.invalid");
            }
        } catch (SQLException ex) {
            log.warn("admin.bff.datasource.warmup.failed", ex);
        }
    }
}
