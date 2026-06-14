package com.prodigalgal.ircs.portalbff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortalBffRoutesTest {

    private final PortalBffRoutes routes = new PortalBffRoutes(
            "http://identity",
            "http://interaction",
            "http://portal",
            "http://search",
            "http://storage"
    );

    @Test
    void normalizesHuawaiBackendAuthPath() {
        ResolvedRoute route = routes.resolve("/api/backend/api/portal/auth/pow").orElseThrow();

        assertThat(route.targetBaseUrl()).isEqualTo("http://identity");
        assertThat(route.targetPath()).isEqualTo("/api/portal/auth/pow");
    }

    @Test
    void routesPortalSearchBeforeGenericPortalPath() {
        ResolvedRoute route = routes.resolve("/api/portal/search/suggest").orElseThrow();

        assertThat(route.targetBaseUrl()).isEqualTo("http://search");
        assertThat(route.targetPath()).isEqualTo("/api/portal/search/suggest");
    }

    @Test
    void routesPortalHomeToPortalService() {
        ResolvedRoute route = routes.resolve("/api/backend/api/portal/home").orElseThrow();

        assertThat(route.targetBaseUrl()).isEqualTo("http://portal");
        assertThat(route.targetPath()).isEqualTo("/api/portal/home");
    }
}
