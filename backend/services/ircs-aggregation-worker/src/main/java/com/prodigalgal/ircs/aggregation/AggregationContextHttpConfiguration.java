package com.prodigalgal.ircs.aggregation;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
class AggregationContextHttpConfiguration {
    @Bean(name = "aggregationContextHttpClient", destroyMethod = "close")
    HttpClient aggregationContextHttpClient(
            @Value("${app.aggregation.context-search.connect-timeout:PT2S}") Duration connectTimeout) {
        return HttpClient.newBuilder().connectTimeout(boundedTimeout(connectTimeout, Duration.ofSeconds(2))).build();
    }

    @Bean("aggregationContextRequestFactory")
    ClientHttpRequestFactory aggregationContextRequestFactory(
            @Qualifier("aggregationContextHttpClient") HttpClient client,
            @Value("${app.aggregation.context-search.request-timeout:PT3S}") Duration requestTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(boundedTimeout(requestTimeout, Duration.ofSeconds(3)));
        return factory;
    }

    static Duration boundedTimeout(Duration configured, Duration fallback) {
        if (configured == null || !configured.isPositive()) { return fallback; }
        if (configured.compareTo(Duration.ofMillis(100)) < 0) { return Duration.ofMillis(100); }
        return configured.compareTo(Duration.ofSeconds(30)) > 0 ? Duration.ofSeconds(30) : configured;
    }
}
