package com.prodigalgal.ircs.ops;

interface MaintenanceAggregationClient {

    MaintenanceRunResult recalculateDirtyUnified(String correlationId);

    MaintenanceRunResult prepareAggregationReset(String correlationId);

    MaintenanceRunResult markAggregationResetRawPending(String correlationId);
}
