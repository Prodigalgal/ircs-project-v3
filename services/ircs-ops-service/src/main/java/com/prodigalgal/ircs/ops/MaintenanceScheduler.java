package com.prodigalgal.ircs.ops;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class MaintenanceScheduler {

    private final MaintenanceSchedulerService schedulerService;

    @Scheduled(
            initialDelayString = "${app.ops.maintenance.scheduler.initial-delay-ms:60000}",
            fixedDelayString = "${app.ops.maintenance.scheduler.fixed-delay-ms:300000}")
    void tick() {
        try {
            schedulerService.scheduledTick();
        } catch (RuntimeException ex) {
            log.warn("Maintenance scheduler tick failed: {}", ex.getMessage());
        }
    }
}
