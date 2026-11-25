package com.prodigalgal.ircs.metadata.provider.imdb;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.metadata.imdb")
@Data
public class ImdbProviderProperties {
    private String suggestionBaseUrl = "https://v3.sg.media-imdb.com/suggestion/x";
    private String detailBaseUrl = "https://www.imdb.com/title";
    private Duration requestTimeout = Duration.ofSeconds(10);
    private String userAgent = "IRCS-Metadata-IMDb/1.0";
}
