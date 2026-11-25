package com.prodigalgal.ircs.aggregation;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
class RestAggregationContextSearchClient implements AggregationContextSearchClient {

    private final boolean enabled;
    private final RestClient restClient;

    RestAggregationContextSearchClient(
            RestClient.Builder builder,
            @Value("${app.aggregation.context-search.enabled:true}") boolean enabled,
            @Value("${app.aggregation.context-search.base-url:http://ircs-search-service:8080}") String baseUrl,
            @Qualifier("aggregationContextRequestFactory") ObjectProvider<ClientHttpRequestFactory> factoryProvider) {
        this.enabled = enabled;
        RestClient.Builder configured = builder.clone().baseUrl(stripTrailingSlash(baseUrl));
        ClientHttpRequestFactory factory = factoryProvider == null ? null : factoryProvider.getIfAvailable();
        if (factory != null) { configured.requestFactory(factory); }
        this.restClient = configured.build();
    }

    @Override
    public ContextSearchResult findCandidateUnifiedVideoIds(String title, String year) {
        if (!enabled || !StringUtils.hasText(title)) {
            return ContextSearchResult.notAttempted();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Aggregation search recall must run outside a database transaction");
        }
        try {
            UUID[] ids = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/v1/search/unified-context-candidates")
                            .queryParam("title", title)
                            .queryParamIfPresent("year", StringUtils.hasText(year)
                                    ? java.util.Optional.of(year)
                                    : java.util.Optional.empty())
                            .build())
                    .retrieve()
                    .body(UUID[].class);
            List<UUID> result = ids == null ? List.of() : Arrays.stream(ids)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .limit(20)
                    .toList();
            return ContextSearchResult.attempted(result);
        } catch (RestClientException ex) {
            log.warn("search-service context recall failed for title='{}', year='{}': {}", title, year, ex.getMessage());
            return ContextSearchResult.attempted(List.of());
        }
    }

    private static String stripTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
