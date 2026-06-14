package com.prodigalgal.ircs.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.prodigalgal.ircs.contracts.notification.MailMessageDTO;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

class NotificationWorkerRabbitConfigurationTest {

    @Test
    void messageConverterAllowsNotificationContractPayloads() throws Exception {
        MailMessageDTO expected = MailMessageDTO.builder()
                .to("codex-smoke@example.invalid")
                .subject("subject")
                .content("content")
                .html(false)
                .build();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_SERIALIZED_OBJECT);
        Message message = new Message(serialize(expected), properties);

        MessageConverter converter = new NotificationWorkerRabbitConfiguration().messageConverter();

        Object actual = converter.fromMessage(message);

        assertThat(actual).isInstanceOf(MailMessageDTO.class);
        MailMessageDTO mail = (MailMessageDTO) actual;
        assertThat(mail.getTo()).isEqualTo(expected.getTo());
        assertThat(mail.getSubject()).isEqualTo(expected.getSubject());
        assertThat(mail.getContent()).isEqualTo(expected.getContent());
        assertThat(mail.isHtml()).isFalse();
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }
}
