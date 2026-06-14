package com.prodigalgal.ircs.ops;

import com.prodigalgal.ircs.contracts.search.SearchEntityType;
import java.util.List;
import java.util.UUID;

interface MaintenanceSearchSyncClient {

    int enqueueIndex(List<UUID> entityIds, SearchEntityType entityType, String correlationId);

    int hardResetIndex(SearchEntityType entityType, String correlationId);
}
