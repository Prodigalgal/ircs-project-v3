package com.prodigalgal.ircs.metadata.catalog;

public interface CatalogMetadataSource {
    CatalogMetadataEvidence fetch(CatalogMetadataTarget target);
}
