package com.prodigalgal.ircs.metadata.inventory;

enum TmdbInventoryMediaType {
    MOVIE("movie", "movie_ids", "original_title", 500_000),
    TV("tv", "tv_series_ids", "original_name", 100_000);

    final String catalogType;
    final String filePrefix;
    final String titleField;
    final int minimumRowCount;

    TmdbInventoryMediaType(String catalogType, String filePrefix, String titleField, int minimumRowCount) {
        this.catalogType = catalogType;
        this.filePrefix = filePrefix;
        this.titleField = titleField;
        this.minimumRowCount = minimumRowCount;
    }
}
