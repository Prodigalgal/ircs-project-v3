package com.prodigalgal.ircs.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GatewayIdentityAutoConfiguration {
    @Bean
    GatewayIdentityFilter gatewayIdentityFilter(Environment environment) {
        return new GatewayIdentityFilter(environment.getProperty("spring.application.name", ""),
                environment.getProperty("ircs.gateway.identity.verification-key",
                        environment.getProperty("IRCS_GATEWAY_IDENTITY_VERIFICATION_KEY", "")));
    }
}
