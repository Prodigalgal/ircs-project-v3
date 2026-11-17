package com.prodigalgal.ircs.metadata.catalog;

/** Fixed, non-secret reason suitable for durable retry state and metrics/logs. */
final class CatalogMetadataUnavailableException extends RuntimeException {
    CatalogMetadataUnavailableException(String code) {
        super(code);
    }
}
