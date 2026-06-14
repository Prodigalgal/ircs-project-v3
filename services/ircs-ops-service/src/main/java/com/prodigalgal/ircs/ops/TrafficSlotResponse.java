package com.prodigalgal.ircs.ops;

public record TrafficSlotResponse(
        String key,
        String label,
        long waitingTasks,
        long nextAvailableInMs,
        double congestionRate,
        boolean isBlocked) {
}

