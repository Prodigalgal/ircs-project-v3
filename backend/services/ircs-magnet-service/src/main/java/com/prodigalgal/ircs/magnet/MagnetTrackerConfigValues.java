package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves the configured public announce list for the current delivery. */
@Component
class MagnetTrackerConfigValues {

    static final String APPEND_ENABLED_KEY = "app.magnet.tracker-append-enabled";
    static final String TRACKERS_KEY = "app.magnet.tracker-list";

    private static final Pattern ANNOUNCE_SCHEME = Pattern.compile("(?i)^(?:https?|udp|wss?)://");
    private static final int MAX_TRACKERS = 40;

    private final RuntimeConfigService runtimeConfig;

    MagnetTrackerConfigValues(ObjectProvider<RuntimeConfigService> runtimeConfigProvider) {
        this.runtimeConfig = runtimeConfigProvider == null ? null : runtimeConfigProvider.getIfAvailable();
    }

    /** The announce list to append, never null. Empty means the operator turned appending off. */
    List<String> trackers() {
        if (runtimeConfig == null) {
            return MagnetUriUtils.DEFAULT_TRACKERS;
        }
        if (!runtimeConfig.booleanValue(APPEND_ENABLED_KEY, true)) {
            return List.of();
        }
        String raw = runtimeConfig.stringValue(TRACKERS_KEY, "");
        if (raw == null || raw.isBlank()) {
            return MagnetUriUtils.DEFAULT_TRACKERS;
        }
        return parse(raw);
    }

    /**
     * Splits a configured value into usable announce URLs.
     *
     * <p>An explicitly configured, non-blank value with no usable URLs appends nothing.
     */
    static List<String> parse(String raw) {
        List<String> trackers = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String entry : raw.split("[,\\n\\r]")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()
                    || !ANNOUNCE_SCHEME.matcher(candidate).find()
                    || !StringUtils.hasText(host(candidate))) {
                continue;
            }
            if (!trackers.contains(candidate)) {
                trackers.add(candidate);
            }
            if (trackers.size() >= MAX_TRACKERS) {
                break;
            }
        }
        return List.copyOf(trackers);
    }

    private static String host(String announceUrl) {
        try {
            return java.net.URI.create(announceUrl).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
