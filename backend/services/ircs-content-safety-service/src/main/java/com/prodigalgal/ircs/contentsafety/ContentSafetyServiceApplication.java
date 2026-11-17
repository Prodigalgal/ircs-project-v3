package com.prodigalgal.ircs.contentsafety;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;

@SpringBootApplication
@EnableConfigurationProperties({ContentSafetyProperties.class, DecisionSettings.class})
@Import({RuntimeConfigService.class, PublicOutboundProxyPool.class})
public class ContentSafetyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentSafetyServiceApplication.class, args);
    }
}
