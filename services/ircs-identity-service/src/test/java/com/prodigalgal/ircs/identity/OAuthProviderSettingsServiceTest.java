package com.prodigalgal.ircs.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class OAuthProviderSettingsServiceTest {

    private final IdentityConfigService configService = mock(IdentityConfigService.class);
    private final OAuthProviderSettingsService service = new OAuthProviderSettingsService(configService);

    @Test
    void globalDisabledHidesAllProviders() {
        when(configService.bool("member.oauth.enabled", false)).thenReturn(false);

        OAuthProviderSettings settings = service.publicSettings();

        assertFalse(settings.enabled());
        assertTrue(settings.providers().isEmpty());
        verify(configService).bool("member.oauth.enabled", false);
        verifyNoMoreInteractions(configService);
    }

    @Test
    void providerEnabledFlagControlsPublicEntries() {
        when(configService.bool("member.oauth.enabled", false)).thenReturn(true);
        when(configService.value("member.oauth.allowed-providers", "google,x,github,gitee,wechat,qq"))
                .thenReturn("google,x");
        when(configService.bool("member.oauth.google.enabled", false)).thenReturn(true);
        when(configService.bool("member.oauth.x.enabled", false)).thenReturn(false);

        OAuthProviderSettings settings = service.publicSettings();

        assertTrue(settings.enabled());
        assertEquals(1, settings.providers().size());
        OAuthProviderSettings.Provider provider = settings.providers().get(0);
        assertEquals("google", provider.code());
        assertEquals("Google", provider.label());
        assertEquals("/api/backend/api/portal/auth/oauth/google/start", provider.loginUrl());
    }

    @Test
    void allowedProvidersListFiltersEnabledProviders() {
        when(configService.bool("member.oauth.enabled", false)).thenReturn(true);
        when(configService.value("member.oauth.allowed-providers", "google,x,github,gitee,wechat,qq"))
                .thenReturn(" x , gitee ");
        when(configService.bool("member.oauth.x.enabled", false)).thenReturn(true);
        when(configService.bool("member.oauth.gitee.enabled", false)).thenReturn(true);

        OAuthProviderSettings settings = service.publicSettings();

        assertEquals(2, settings.providers().size());
        assertEquals("x", settings.providers().get(0).code());
        assertEquals("gitee", settings.providers().get(1).code());
    }
}
