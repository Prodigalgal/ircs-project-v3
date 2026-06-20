package com.prodigalgal.ircs.ops;

public record ServiceRestartResult(
        String service,
        boolean accepted,
        String message) {

    static ServiceRestartResult accepted(String service) {
        return new ServiceRestartResult(service, true, "Deployment restart requested");
    }

    static ServiceRestartResult rejected(String service, String message) {
        return new ServiceRestartResult(service, false, message);
    }
}
