package com.prodigalgal.ircs.ops;

interface MaintenanceLlmCleaningClient {

    MaintenanceRunResult run(MaintenanceLlmCleaningTask task, String correlationId);

    MaintenanceRunResult resetAllNormalization(String correlationId);
}
