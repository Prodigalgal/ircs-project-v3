package com.prodigalgal.ircs.apigateway;

import com.prodigalgal.ircs.common.security.IrcsJwtRequestAuthenticator;
import com.prodigalgal.ircs.common.security.IrcsJwtRuntimeConfigResolver;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
class ApiGatewayAuthConfiguration {

    @Bean
    IrcsJwtRequestAuthenticator apiGatewayJwtRequestAuthenticator(
            Environment environment,
            DataSource dataSource,
            GatewaySystemConfigRepository configRepository) {
        return new IrcsJwtRequestAuthenticator(new IrcsJwtRuntimeConfigResolver(environment, dataSource, configRepository));
    }
}
