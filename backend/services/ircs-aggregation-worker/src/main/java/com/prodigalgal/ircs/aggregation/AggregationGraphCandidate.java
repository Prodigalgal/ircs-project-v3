package com.prodigalgal.ircs.aggregation;

import java.util.UUID;

public sealed interface AggregationGraphCandidate
        permits AggregationGraphCandidate.RawNode, AggregationGraphCandidate.UnifiedNode {

    UUID id();

    String year();

    String categoryName();

    static RawNode raw(SourceRecordAggregationRecord sourceRecord) {
        return new RawNode(sourceRecord);
    }

    static UnifiedNode unified(CatalogEntryAggregationCandidate catalogEntry) {
        return new UnifiedNode(catalogEntry);
    }

    record RawNode(SourceRecordAggregationRecord sourceRecord) implements AggregationGraphCandidate {
        @Override
        public UUID id() {
            return sourceRecord.id();
        }

        @Override
        public String year() {
            return sourceRecord.year();
        }

        @Override
        public String categoryName() {
            return sourceRecord.categoryName();
        }
    }

    record UnifiedNode(CatalogEntryAggregationCandidate catalogEntry) implements AggregationGraphCandidate {
        @Override
        public UUID id() {
            return catalogEntry.id();
        }

        @Override
        public String year() {
            return catalogEntry.year();
        }

        @Override
        public String categoryName() {
            return catalogEntry.categoryName();
        }
    }
}
