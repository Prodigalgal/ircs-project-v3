package com.prodigalgal.ircs.task;

import java.util.UUID;

record TaskRuntimeState(
        UUID id,
        UUID dataSourceId,
        String status,
        Boolean enabled,
        Integer startPage,
        Integer currentPage
) {
}
