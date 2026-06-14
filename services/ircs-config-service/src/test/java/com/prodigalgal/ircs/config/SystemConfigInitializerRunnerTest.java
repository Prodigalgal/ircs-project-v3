package com.prodigalgal.ircs.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class SystemConfigInitializerRunnerTest {

    private final JdbcConfigRepository repository = org.mockito.Mockito.mock(JdbcConfigRepository.class);
    private final SystemConfigDefaults defaults = new SystemConfigDefaults(NoOpPasswordEncoder.getInstance());

    @Test
    void upsertsAllDefaultConfigsOnStartup() {
        when(repository.upsertDefault(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new JdbcConfigRepository.UpsertResult(true, false));
        SystemConfigInitializerRunner runner = SystemConfigInitializerRunner.forTest(repository, defaults);

        runner.run(new DefaultApplicationArguments());

        assertTrue(defaults.all().size() > 20);
        verify(repository, org.mockito.Mockito.times(defaults.all().size()))
                .upsertDefault(org.mockito.ArgumentMatchers.any());
    }
}
