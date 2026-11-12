package com.prodigalgal.ircs.ops.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class OpsInternalAccessFilterTest {
    @Test
    void failsClosedEvenWhenHistoricalRequireTokenIsFalse() throws Exception {
        var filter = new OpsInternalAccessFilter(false, "", "ops:read", "ops:run");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/ops/maintenance"), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(chain.getRequest()).isNull();
    }
}
