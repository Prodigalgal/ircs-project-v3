package com.prodigalgal.ircs.catalog;

import java.util.UUID;

public record DataSourceSummary(
        UUID id,
        String name,
        String baseUrl,
        String listPath,
        String detailPath,
        String transportMode,
        String httpProtocol,
        String ipVersionPolicy,
        String dnsResolverType) {

    public DataSourceSummary(
            UUID id,
            String name,
            String baseUrl,
            String listPath,
            String detailPath) {
        this(id, name, baseUrl, listPath, detailPath, "AUTO", "AUTO", "AUTO", "SYSTEM");
    }
}
