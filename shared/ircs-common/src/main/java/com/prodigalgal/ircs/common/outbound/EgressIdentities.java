package com.prodigalgal.ircs.common.outbound;

import org.springframework.util.StringUtils;

public final class EgressIdentities {

    private EgressIdentities() {
    }

    public static String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
