package com.prodigalgal.ircs.config;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ConfigValueRedactor {

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(password|passwd|pwd|secret|token|api[-_.]?key|access[-_.]?key|private[-_.]?key|credential|jwt|auth)",
            Pattern.CASE_INSENSITIVE);

    public SystemConfigSummary sanitize(SystemConfigRecord record) {
        return sanitize(record, record.value(), "DB");
    }

    public SystemConfigSummary sanitize(SystemConfigRecord record, String effectiveValue, String effectiveSource) {
        boolean sensitive = isSensitive(record.key());
        return new SystemConfigSummary(
                record.id(),
                record.key(),
                sensitive ? null : record.value(),
                sensitive ? null : effectiveValue,
                effectiveSource,
                record.description(),
                record.revision(),
                record.updatedAt(),
                sensitive);
    }

    boolean isSensitive(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEY.matcher(normalized).find();
    }
}
