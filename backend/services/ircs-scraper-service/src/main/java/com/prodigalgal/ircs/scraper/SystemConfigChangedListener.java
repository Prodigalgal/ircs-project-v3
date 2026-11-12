package com.prodigalgal.ircs.scraper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.ircs.contracts.config.SystemConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
class SystemConfigChangedListener {

    static final String QUEUE_NAME = "q.scraper.config_changed";

    private final SystemConfigRepository repository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = QUEUE_NAME, autoStartup = "${app.scraper.config-listener.enabled:true}")
    void handle(String payload) {
        try {
            SystemConfigChangedEvent event = objectMapper.readValue(payload, SystemConfigChangedEvent.class);
            if (StringUtils.hasText(event.key())) {
                repository.evict(event.key());
            }
        } catch (JacksonException ex) {
            log.warn("Failed to parse scraper system config change event", ex);
        }
    }
}
