package com.prodigalgal.ircs.task;

import java.util.UUID;

record ScheduledTaskDefinition(
        UUID id,
        String name,
        Boolean enabled,
        String cronExpression,
        String timeZone) {
}
