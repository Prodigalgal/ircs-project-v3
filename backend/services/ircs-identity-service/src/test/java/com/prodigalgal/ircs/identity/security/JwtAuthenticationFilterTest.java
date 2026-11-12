package com.prodigalgal.ircs.identity.security;




import com.prodigalgal.ircs.identity.domain.IdentityConfigKey;
import com.prodigalgal.ircs.identity.application.MemberStatusCacheService;
import com.prodigalgal.ircs.identity.application.IdentityConfigService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.security.IrcsAuthClaims;
import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final MemberStatusCacheService memberStatusCacheService = mock(MemberStatusCacheService.class);
    private final IdentityConfigService configService = mock(IdentityConfigService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            jwtTokenService,
            memberStatusCacheService,
            configService);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsAdminTokenAndGrantsRoleAdmin() throws Exception {
        Claims claims = Jwts.claims()
                .subject("admin")
                .add(IrcsAuthClaims.ROLE, IrcsPermissions.ROLE_ADMIN)
                .add(IrcsAuthClaims.PERMISSIONS, Set.of(IrcsPermissions.ALL))
                .add(IrcsAuthClaims.SCOPES, Set.of(IrcsPermissions.SCOPE_ADMIN_ALL, IrcsPermissions.SCOPE_DATA_ALL))
                .add(IrcsAuthClaims.DATA_CATEGORIES, Set.of(IrcsPermissions.ALL))
                .add(IrcsAuthClaims.DATA_GENRES, Set.of(IrcsPermissions.ALL))
                .add(IrcsAuthClaims.DATA_TAGS, Set.of(IrcsPermissions.ALL))
                .add(IrcsAuthClaims.CONTENT_VISIBILITY, Set.of(IrcsPermissions.ALL))
                .issuedAt(new Date())
                .build();
        when(jwtTokenService.parse("token")).thenReturn(claims);
        when(jwtTokenService.iatFloorSeconds()).thenReturn(0L);
        when(configService.value(IdentityConfigKey.ADMIN_USERNAME)).thenReturn("admin");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(
                "ROLE_ADMIN",
                SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(IrcsPermissions.authority(IrcsPermissions.ALL))));
    }

    @Test
    void ignoresUnsignedAdminHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
        request.addHeader(IrcsAuthHeaders.AUTH_SUBJECT, "admin");
        request.addHeader(IrcsAuthHeaders.AUTH_ROLE, IrcsPermissions.ROLE_ADMIN);
        for (String header : java.util.List.of(IrcsAuthHeaders.AUTH_PERMISSIONS, IrcsAuthHeaders.AUTH_SCOPES,
                IrcsAuthHeaders.DATA_CATEGORIES, IrcsAuthHeaders.DATA_GENRES, IrcsAuthHeaders.DATA_TAGS,
                IrcsAuthHeaders.CONTENT_VISIBILITY)) request.addHeader(header, "*");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        org.junit.jupiter.api.Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void retainsVerifiedGatewayPersonalAccessTokenIdentity() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/members");
        IrcsAuthHeaders.setRequestAttribute(request, new com.prodigalgal.ircs.common.security.IrcsRequestPrincipal(
                "api-token:ircs_pat_123", IrcsPermissions.ROLE_ADMIN,
                Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*")));
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertEquals("api-token:ircs_pat_123", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void rejectsVerifiedAdminWithUnboundUsername() throws Exception {
        when(configService.value(IdentityConfigKey.ADMIN_USERNAME)).thenReturn("admin");
        var request = new MockHttpServletRequest("GET", "/api/v1/members");
        IrcsAuthHeaders.setRequestAttribute(request, new com.prodigalgal.ircs.common.security.IrcsRequestPrincipal(
                "someone-else", IrcsPermissions.ROLE_ADMIN,
                Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*")));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void acceptsVerifiedGatewayAdminPrincipalAndGrantsRoleAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members");
        request.addHeader(IrcsAuthHeaders.AUTH_SUBJECT, "admin");
        request.addHeader(IrcsAuthHeaders.AUTH_ROLE, IrcsPermissions.ROLE_ADMIN);
        request.addHeader(IrcsAuthHeaders.AUTH_PERMISSIONS, IrcsPermissions.ALL);
        request.addHeader(IrcsAuthHeaders.AUTH_SCOPES, IrcsPermissions.SCOPE_ADMIN_ALL + "," + IrcsPermissions.SCOPE_DATA_ALL);
        request.addHeader(IrcsAuthHeaders.DATA_CATEGORIES, IrcsPermissions.ALL);
        request.addHeader(IrcsAuthHeaders.DATA_GENRES, IrcsPermissions.ALL);
        request.addHeader(IrcsAuthHeaders.DATA_TAGS, IrcsPermissions.ALL);
        request.addHeader(IrcsAuthHeaders.CONTENT_VISIBILITY, IrcsPermissions.ALL);
        IrcsAuthHeaders.setRequestAttribute(request, new com.prodigalgal.ircs.common.security.IrcsRequestPrincipal(
                "admin", IrcsPermissions.ROLE_ADMIN, Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*"), Set.of("*")));
        when(configService.value(IdentityConfigKey.ADMIN_USERNAME)).thenReturn("admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(IrcsPermissions.ROLE_ADMIN)));
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(IrcsPermissions.authority(IrcsPermissions.ALL))));
        verify(jwtTokenService, never()).parse(org.mockito.ArgumentMatchers.anyString());
    }
}
