package com.prodigalgal.ircs.identity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthProviderSettingsService {

    private static final String ENABLED_KEY = "member.oauth.enabled";
    private static final String ALLOWED_PROVIDERS_KEY = "member.oauth.allowed-providers";
    private static final String DEFAULT_ALLOWED_PROVIDERS = "google,x,github,gitee,wechat,qq";

    private static final List<ProviderDefinition> PROVIDERS = List.of(
            new ProviderDefinition("google", "Google"),
            new ProviderDefinition("x", "X"),
            new ProviderDefinition("github", "GitHub"),
            new ProviderDefinition("gitee", "Gitee"),
            new ProviderDefinition("wechat", "微信"),
            new ProviderDefinition("qq", "QQ"));

    private final IdentityConfigService configService;

    OAuthProviderSettingsService(IdentityConfigService configService) {
        this.configService = configService;
    }

    public OAuthProviderSettings publicSettings() {
        boolean enabled = configService.bool(ENABLED_KEY, false);
        if (!enabled) {
            return new OAuthProviderSettings(false, List.of());
        }

        Set<String> allowedProviders = parseAllowedProviders(
                configService.value(ALLOWED_PROVIDERS_KEY, DEFAULT_ALLOWED_PROVIDERS));

        List<OAuthProviderSettings.Provider> providers = PROVIDERS.stream()
                .filter(provider -> allowedProviders.contains(provider.code()))
                .filter(provider -> configService.bool("member.oauth." + provider.code() + ".enabled", false))
                .map(provider -> new OAuthProviderSettings.Provider(
                        provider.code(),
                        provider.label(),
                        "/api/backend/api/portal/auth/oauth/" + provider.code() + "/start"))
                .toList();

        return new OAuthProviderSettings(true, providers);
    }

    private static Set<String> parseAllowedProviders(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
    }

    private record ProviderDefinition(String code, String label) {
    }
}
