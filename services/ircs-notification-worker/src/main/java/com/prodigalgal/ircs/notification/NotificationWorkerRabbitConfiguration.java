package com.prodigalgal.ircs.notification;

import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class NotificationWorkerRabbitConfiguration {

    @Bean
    MessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.addAllowedListPatterns("com.prodigalgal.ircs.contracts.notification.*");
        return converter;
    }
}
