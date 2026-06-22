package com.prodigalgal.ircs.notification.channel;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import com.prodigalgal.ircs.contracts.notification.NotificationChannel;
import com.prodigalgal.ircs.contracts.notification.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebhookNotificationChannelExecutor implements NotificationChannelExecutor {

    static final String ENABLED_KEY = "app.notification.webhook.enabled";

    private final RuntimeConfigService runtimeConfig;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    public NotificationChannelExecution execute(NotificationCommand command, String recipient) {
        if (!runtimeConfig.booleanValue(ENABLED_KEY, false)) {
            return NotificationChannelExecution.skipped(channel().name(), recipient, "webhook channel disabled");
        }
        throw new UnsupportedOperationException("Webhook channel transport is not enabled in this rollout");
    }
}
