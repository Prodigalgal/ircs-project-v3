package com.prodigalgal.ircs.messaging;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConfirmedMessagePublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final Duration timeout;

    public ConfirmedMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${ircs.messaging.publish-confirm-timeout:PT10S}") Duration timeout) {
        if (timeout == null || !timeout.isPositive()) {
            throw new IllegalArgumentException("Rabbit publisher confirm timeout must be positive");
        }
        this.rabbitTemplate = rabbitTemplate;
        this.timeout = timeout;
    }

    @Override
    public void publish(String exchange, String routingKey, Object payload) {
        var correlation = new CorrelationData();
        if (payload instanceof Message message) {
            rabbitTemplate.send(exchange, routingKey, message, correlation);
        } else {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlation);
        }
        try {
            var confirm = correlation.getFuture().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new AmqpException("Rabbit publish was rejected or unroutable: " + exchange + "/" + routingKey);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Rabbit publisher confirm interrupted", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new AmqpException("Rabbit publisher confirm failed within " + timeout, ex);
        }
    }
}
