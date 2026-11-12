package com.prodigalgal.ircs.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ConfirmedMessagePublisherTest {

    private final RabbitTemplate template = mock(RabbitTemplate.class);

    @Test
    void returnsOnlyAfterBrokerAcknowledges() {
        doAnswer(invocation -> {
            invocation.getArgument(3, CorrelationData.class).getFuture()
                    .complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        new ConfirmedMessagePublisher(template, Duration.ofSeconds(1)).publish("exchange", "route", "payload");
    }

    @Test
    void rejectsBrokerNack() {
        doAnswer(invocation -> {
            invocation.getArgument(3, CorrelationData.class).getFuture()
                    .complete(new CorrelationData.Confirm(false, "broker rejected"));
            return null;
        }).when(template).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new ConfirmedMessagePublisher(template, Duration.ofSeconds(1))
                .publish("exchange", "route", "payload")).isInstanceOf(AmqpException.class);
    }

    @Test
    void rejectsUnroutableMessageEvenWhenExchangeAcknowledges() {
        doAnswer(invocation -> {
            var correlation = invocation.getArgument(3, CorrelationData.class);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                    312, "NO_ROUTE", "exchange", "route"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(template).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new ConfirmedMessagePublisher(template, Duration.ofSeconds(1))
                .publish("exchange", "route", new Message(new byte[0], new MessageProperties())))
                .isInstanceOf(AmqpException.class);
    }

    @Test
    void boundsConfirmationWait() {
        assertThatThrownBy(() -> new ConfirmedMessagePublisher(template, Duration.ofMillis(25))
                .publish("exchange", "route", "payload")).isInstanceOf(AmqpException.class)
                .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }
}
