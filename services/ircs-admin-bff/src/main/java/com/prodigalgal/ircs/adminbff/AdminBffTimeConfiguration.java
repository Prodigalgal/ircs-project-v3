package com.prodigalgal.ircs.adminbff;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AdminBffTimeConfiguration {

    @Bean
    Clock adminBffClock() {
        return Clock.systemUTC();
    }
}
