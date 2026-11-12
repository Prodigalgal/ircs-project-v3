package com.prodigalgal.ircs.messaging;

/** Publishes only when the transport has confirmed accepting the message. */
public interface MessagePublisher {

    void publish(String destination, String routingKey, Object payload);
}
