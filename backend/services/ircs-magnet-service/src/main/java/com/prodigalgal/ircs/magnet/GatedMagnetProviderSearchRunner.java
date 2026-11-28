package com.prodigalgal.ircs.magnet;

import com.prodigalgal.ircs.common.config.RuntimeConfigService;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
class GatedMagnetProviderSearchRunner implements MagnetProviderSearchRunner {

    private static final String YTS_BZ = "YTS_BZ";
    private static final String FIXTURE = "FIXTURE";
    private static final String PROVIDER_DISABLED = "MAGNET_PROVIDER_DISABLED";
    private static final String PROVIDER_UNSUPPORTED = "MAGNET_PROVIDER_UNSUPPORTED";
    private static final String FIXTURE_DISABLED = "MAGNET_FIXTURE_DISABLED";
    private static final Set<String> GENERIC_PROVIDER_TYPES = Set.of(
            "THE_PIRATE_BAY", "APIBAY", "EZTV", "EZTVX", "THE_PIRATE_BAY_FRONTEND", "TPB_FRONTEND",
            "EXT_TO", "NYAA", "BT4G", "TORRENT_GALAXY", "SOLIDTORRENTS", "GENERIC_HTML", "THE_RARBG");
    private static final Profiles FIXTURE_PROFILES = Profiles.of("dev", "test", "native-smoke");

    private final MagnetProviderSearchRunner fixtureRunner;
    private final MagnetProviderSearchRunner ytsBzRunner;
    private final MagnetProviderSearchRunner genericRunner;
    private final RuntimeConfigService runtimeConfig;
    private final boolean realProviderEnabledByDeployment;
    private final String realProviderAllowlistByDeployment;
    private final Environment environment;
    private final boolean fixtureEnabledByDeployment;
    GatedMagnetProviderSearchRunner(
            FixtureMagnetProviderSearchRunner fixtureRunner,
            YtsBzMagnetProviderSearchRunner ytsBzRunner,
            GenericMagnetProviderSearchRunner genericRunner,
            ObjectProvider<RuntimeConfigService> runtimeConfigProvider,
            @Value("${app.magnet.real-provider.enabled:false}") boolean realProviderEnabled,
            @Value("${app.magnet.real-provider.allowlist:YTS_BZ,THE_PIRATE_BAY,EZTV,THE_RARBG}")
                    String realProviderAllowlist,
            Environment environment,
            @Value("${app.magnet.fixture.enabled:false}") boolean fixtureEnabled) {
        this.fixtureRunner = fixtureRunner;
        this.ytsBzRunner = ytsBzRunner;
        this.genericRunner = genericRunner;
        this.runtimeConfig = runtimeConfigProvider == null ? null : runtimeConfigProvider.getIfAvailable();
        this.realProviderEnabledByDeployment = realProviderEnabled;
        this.realProviderAllowlistByDeployment = realProviderAllowlist;
        this.environment = environment;
        this.fixtureEnabledByDeployment = fixtureEnabled;
    }

    @Override
    public MagnetProviderSearchResult search(
            MagnetProviderSummary provider,
            MagnetExternalIdQuery query,
            UUID unifiedVideoId) {
        if (provider == null) {
            throw unsupportedProvider();
        }
        if (!Boolean.TRUE.equals(provider.enabled())) {
            throw new MagnetProviderRunnerException(PROVIDER_DISABLED, null, null);
        }
        if (isFixture(provider)) {
            if (!fixtureEnabled()) {
                throw new MagnetProviderRunnerException(FIXTURE_DISABLED, null, null);
            }
            return fixtureRunner.search(provider, query, unifiedVideoId);
        }
        if (isYtsBz(provider)) {
            requireRealProviderEnabled();
            if (!isAllowlisted(provider)) {
                throw unsupportedProvider();
            }
            return ytsBzRunner.search(provider, query, unifiedVideoId);
        }
        if (isGenericProvider(provider)) {
            requireRealProviderEnabled();
            if (!isAllowlisted(provider)) {
                throw unsupportedProvider();
            }
            return genericRunner.search(provider, query, unifiedVideoId);
        }
        throw unsupportedProvider();
    }

    private boolean isFixture(MagnetProviderSummary provider) {
        return FIXTURE.equals(normalize(provider.providerType())) || FIXTURE.equals(normalize(provider.code()));
    }

    private boolean isYtsBz(MagnetProviderSummary provider) {
        return YTS_BZ.equals(normalize(provider.providerType()))
                || YTS_BZ.equals(normalize(provider.code()));
    }

    private boolean isGenericProvider(MagnetProviderSummary provider) {
        return GENERIC_PROVIDER_TYPES.contains(normalize(provider.providerType()))
                || GENERIC_PROVIDER_TYPES.contains(normalize(provider.code()));
    }

    private boolean isAllowlisted(MagnetProviderSummary provider) {
        Set<String> allowlist = realProviderAllowlist();
        return allowlist.contains(normalize(provider.providerType()))
                || allowlist.contains(normalize(provider.code()));
    }

    private void requireRealProviderEnabled() {
        if (!realProviderEnabled()) {
            throw new MagnetProviderRunnerException(PROVIDER_DISABLED, null, null);
        }
    }

    private boolean fixtureEnabled() {
        return fixtureEnabledByDeployment
                && environment != null
                && environment.acceptsProfiles(FIXTURE_PROFILES);
    }

    private MagnetProviderRunnerException unsupportedProvider() {
        return new MagnetProviderRunnerException(PROVIDER_UNSUPPORTED, null, null);
    }

    private boolean realProviderEnabled() {
        return runtimeConfig == null
                ? realProviderEnabledByDeployment
                : runtimeConfig.booleanValue("app.magnet.real-provider.enabled", realProviderEnabledByDeployment);
    }

    private Set<String> realProviderAllowlist() {
        String value = runtimeConfig == null
                ? realProviderAllowlistByDeployment
                : runtimeConfig.stringValue("app.magnet.real-provider.allowlist", realProviderAllowlistByDeployment);
        return parseAllowlist(value);
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
