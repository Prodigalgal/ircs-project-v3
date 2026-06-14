package com.prodigalgal.ircs.task;

import java.util.UUID;

record TaskConfigurationChangedEvent(UUID taskId, boolean deleted) {
}
