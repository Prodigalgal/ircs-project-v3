package com.prodigalgal.ircs.contracts.search;

public enum SearchEntityType {
    CATALOG_ENTRY,
    SOURCE_RECORD;

    public static final String CATALOG_ENTRY_INDEX = "ircs_catalog_entries_v5";
    public static final String SOURCE_RECORD_INDEX = "ircs_source_records_v5";
}
