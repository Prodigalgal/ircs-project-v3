package com.prodigalgal.ircs.messaging;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(SimpleRabbitListenerContainerFactory.class)
public class MessageConsumerThreadConfiguration {

    @Bean(destroyMethod = "close", defaultCandidate = false)
    SimpleAsyncTaskExecutor rabbitConsumerExecutor() {
        var executor = new SimpleAsyncTaskExecutor("ircs-rabbit-consumer-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(30_000);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            @Qualifier("rabbitConsumerExecutor") SimpleAsyncTaskExecutor executor) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // Container concurrency and prefetch remain the admission controls.
        factory.setTaskExecutor(executor);
        return factory;
    }
}
