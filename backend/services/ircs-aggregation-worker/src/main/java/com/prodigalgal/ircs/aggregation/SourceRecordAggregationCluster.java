package com.prodigalgal.ircs.aggregation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record SourceRecordAggregationCluster(
        SourceRecordAggregationRecord leader,
        List<SourceRecordAggregationRecord> members,
        List<UUID> contextCatalogEntryIds) {

    public SourceRecordAggregationCluster {
        if (leader == null) {
            throw new IllegalArgumentException("leader is required");
        }
        LinkedHashSet<SourceRecordAggregationRecord> orderedMembers = new LinkedHashSet<>();
        orderedMembers.add(leader);
        if (members != null) {
            orderedMembers.addAll(members);
        }
        members = List.copyOf(orderedMembers);
        contextCatalogEntryIds = contextCatalogEntryIds == null
                ? List.of()
                : contextCatalogEntryIds.stream()
                        .filter(id -> id != null)
                        .distinct()
                        .toList();
    }

    public SourceRecordAggregationCluster(SourceRecordAggregationRecord leader, List<SourceRecordAggregationRecord> members) {
        this(leader, members, List.of());
    }

    public List<UUID> sourceRecordIds() {
        return members.stream()
                .map(SourceRecordAggregationRecord::id)
                .toList();
    }

    public boolean isMultiRawCluster() {
        return members.size() > 1;
    }
}
