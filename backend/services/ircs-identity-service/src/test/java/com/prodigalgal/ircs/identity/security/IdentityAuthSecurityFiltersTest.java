package com.prodigalgal.ircs.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.prodigalgal.ircs.common.security.GatewayIdentitySignature;
import com.prodigalgal.ircs.identity.application.PoWService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

class IdentityAuthSecurityFiltersTest {
    @Test
    @SuppressWarnings("unchecked")
    void aliasesUseOneRateBucketAndForgedForwardedIpCannotResetIt() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(31L);
        var filter = new IdentityAuthRateLimitFilter(redis, null, true, 30, Duration.ofMinutes(5));
        for (String path : new String[]{"/api/v1/auth/login", "/api/v1/auth/login/", "/api/v1/auth/login;x=1", "/api/v1/auth/%6cogin"}) {
            var request = new MockHttpServletRequest("POST", path);
            request.setRemoteAddr("10.244.1.1");
            request.addHeader("X-Forwarded-For", java.util.UUID.randomUUID().toString());
            request.addHeader("X-Real-IP", java.util.UUID.randomUUID().toString());
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(429);
        }
        verify(values, times(4)).increment(com.prodigalgal.ircs.identity.IdentityCacheKeys.authRateLimit("/api/v1/auth/login:10.244.1.1"));
        var signed = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        signed.setAttribute(GatewayIdentitySignature.CLIENT_IP_ATTRIBUTE, "203.0.113.7");
        filter.doFilter(signed, new MockHttpServletResponse(), new MockFilterChain());
        verify(values).increment(com.prodigalgal.ircs.identity.IdentityCacheKeys.authRateLimit("/api/v1/auth/login:203.0.113.7"));
    }

    @Test
    void powAppliesToMvcEquivalentLoginPathsWithSameScope() throws Exception {
        var service = mock(PoWService.class);
        var filter = new PoWEnforcementFilter(service, null, true);
        for (String path : new String[]{"/api/v1/auth/login", "/api/v1/auth/login/", "/api/v1/auth/login;x=1", "/api/v1/auth/%6cogin"}) {
            var request = new MockHttpServletRequest("POST", path);
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
            verify(service).verifyHeaders(request, "admin.login", true);
        }
    }
}
