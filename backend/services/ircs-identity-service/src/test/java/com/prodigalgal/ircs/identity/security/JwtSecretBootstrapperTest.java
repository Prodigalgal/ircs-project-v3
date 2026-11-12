package com.prodigalgal.ircs.identity.security;



import com.prodigalgal.ircs.identity.domain.IdentityConfigKey;
import com.prodigalgal.ircs.identity.application.IdentityConfigService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JwtSecretBootstrapperTest {

    private final IdentityConfigService configService = org.mockito.Mockito.mock(IdentityConfigService.class);
    private final JwtSecretBootstrapper bootstrapper = new JwtSecretBootstrapper(configService);

    @Test
    void startupGeneratesRuntimeSecretAndPersistsItWhenNoSecretExists() {
        when(configService.storedValue(IdentityConfigKey.JWT_SECRET)).thenReturn("");

        bootstrapper.run(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<IdentityConfigKey, String>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> persistedCaptor = ArgumentCaptor.forClass(String.class);
        verify(configService).hasInjectedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService).storedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService).installRuntimeValues(valuesCaptor.capture());
        verify(configService).updateValue(org.mockito.Mockito.eq(IdentityConfigKey.JWT_SECRET), persistedCaptor.capture());

        String runtimeSecret = valuesCaptor.getValue().get(IdentityConfigKey.JWT_SECRET);
        assertEquals(runtimeSecret, persistedCaptor.getValue());
        assertTrue(runtimeSecret.getBytes(StandardCharsets.UTF_8).length >= 32);
    }

    @Test
    void startupKeepsRuntimeInjectedSecret() {
        when(configService.hasInjectedValue(IdentityConfigKey.JWT_SECRET)).thenReturn(true);

        bootstrapper.run(null);

        verify(configService).hasInjectedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService, never()).storedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService, never()).installRuntimeValues(any());
        verify(configService, never()).updateValue(any(), anyString());
        verifyNoMoreInteractions(configService);
    }

    @Test
    void startupKeepsStoredSecret() {
        when(configService.storedValue(IdentityConfigKey.JWT_SECRET)).thenReturn("db-secret");

        bootstrapper.run(null);

        verify(configService).hasInjectedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService).storedValue(IdentityConfigKey.JWT_SECRET);
        verify(configService, never()).installRuntimeValues(any());
        verify(configService, never()).updateValue(any(), anyString());
        verifyNoMoreInteractions(configService);
    }
}
