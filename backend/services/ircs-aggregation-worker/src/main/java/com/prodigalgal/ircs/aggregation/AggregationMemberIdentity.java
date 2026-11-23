package com.prodigalgal.ircs.aggregation;

import java.util.UUID;

/** Identity fields read from an existing raw-to-unified association. */
record AggregationMemberIdentity(
        UUID sourceRecordId,
        String title,
        String aliasTitle,
        String subtitle,
        String remarks,
        String year,
        Integer season,
        String category,
        String doubanId,
        String tmdbId,
        String imdbId,
        String rottenTomatoesId) {

    static final int REVIEW_MEMBER_LIMIT = 64;
    static final String REVIEW_LIMIT_REASON = "CLUSTER_MEMBER_LIMIT_EXCEEDED";
}
