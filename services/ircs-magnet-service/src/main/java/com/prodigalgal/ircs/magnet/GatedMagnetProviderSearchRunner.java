package com.prodigalgal.ircs.magnet;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class GatedMagnetProviderSearchRunner implements MagnetProviderSearchRunner {

    private static final String YTS_BZ = "YTS_BZ";

    private final MagnetProviderSearchRunner fixtureRunner;
    private final MagnetProviderSearchRunner ytsBzRunner;
    private final boolean realProviderEnabled;
    private final Set<String> realProviderAllowlist;
    GatedMagnetProviderSearchRunner(
            FixtureMagnetProviderSearchRunner fixtureRunner,
            YtsBzMagnetProviderSearchRunner ytsBzRunner,
            @Value("${app.magnet.real-provider.enabled:false}") boolean realProviderEnabled,
            @Value("${app.magnet.real-provider.allowlist:YTS_BZ}") String realProviderAllowlist) {
        this.fixtureRunner = fixtureRunner;
        this.ytsBzRunner = ytsBzRunner;
        this.realProviderEnabled = realProviderEnabled;
        this.realProviderAllowlist = parseAllowlist(realProviderAllowlist);
    }

    @Override
    public MagnetProviderSearchResult search(
            MagnetProviderSummary provider,
            MagnetExternalIdQuery query,
            UUID unifiedVideoId) {
        if (shouldUseYtsBz(provider)) {
            return ytsBzRunner.search(provider, query, unifiedVideoId);
        }
        return fixtureRunner.search(provider, query, unifiedVideoId);
    }

    private boolean shouldUseYtsBz(MagnetProviderSummary provider) {
        if (!realProviderEnabled || provider == null || !isYtsBz(provider)) {
            return false;
        }
        return realProviderAllowlist.contains(normalize(provider.providerType()))
                || realProviderAllowlist.contains(normalize(provider.code()));
    }

    private boolean isYtsBz(MagnetProviderSummary provider) {
        return YTS_BZ.equals(normalize(provider.providerType()))
                || YTS_BZ.equals(normalize(provider.code()));
    }

    private Set<String> parseAllowlist(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }
}
