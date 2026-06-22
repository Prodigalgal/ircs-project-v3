package com.prodigalgal.ircs.ops.traffic.dto;

public record TrafficSlotResponse(
        String key,
        String label,
        long waitingTasks,
        long nextAvailableInMs,
        double congestionRate,
        boolean isBlocked,
        String limiterType,
        Long remainingPermits,
        Long capacity) {
}
