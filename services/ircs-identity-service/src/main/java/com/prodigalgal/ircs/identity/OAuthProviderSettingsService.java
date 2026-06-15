package com.prodigalgal.ircs.identity;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OAuthProviderSettingsService {

    private final OAuthProviderConfigResolver configResolver;

    OAuthProviderSettingsService(OAuthProviderConfigResolver configResolver) {
        this.configResolver = configResolver;
    }

    public OAuthProviderSettings publicSettings() {
        boolean enabled = configResolver.globalEnabled();
        if (!enabled) {
            return new OAuthProviderSettings(false, List.of());
        }

        List<OAuthProviderSettings.Provider> providers = configResolver.usableProviders().stream()
                .map(provider -> new OAuthProviderSettings.Provider(
                        provider.code(),
                        provider.label(),
                        "/api/backend/api/portal/auth/oauth/" + provider.code() + "/start"))
                .toList();

        return new OAuthProviderSettings(true, providers);
    }
}
