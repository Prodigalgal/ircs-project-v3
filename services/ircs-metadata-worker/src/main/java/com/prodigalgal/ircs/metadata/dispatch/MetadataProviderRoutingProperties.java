package com.prodigalgal.ircs.metadata.dispatch;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "app.metadata")
public class MetadataProviderRoutingProperties {

    private Dispatcher dispatcher = new Dispatcher();
    private Providers providers = new Providers();

    @Data
    public static class Dispatcher {
        private boolean enabled;
        private Duration pendingTtl = Duration.ofMinutes(30);
    }

    @Data
    public static class Providers {
        private boolean doubanEnabled;
        private boolean tmdbEnabled;
        private boolean rottenTomatoesEnabled;
    }
}
