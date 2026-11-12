package com.prodigalgal.ircs.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityCorsTest {
    @Test
    void permitsOnlyExplicitOriginsWithoutAmbientCredentials() {
        var configuration = new SecurityConfig(null, "https://admin.mnnu.eu.org,https://huawai.mnnu.eu.org").corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login"));
        assertThat(configuration).isNotNull();
        assertThat(configuration.checkOrigin("https://evil.example")).isNull();
        assertThat(configuration.checkOrigin("https://admin.mnnu.eu.org")).isEqualTo("https://admin.mnnu.eu.org");
        assertThat(configuration.getAllowCredentials()).isFalse();
    }
}
