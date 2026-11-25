package com.prodigalgal.ircs.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import com.prodigalgal.ircs.common.security.IrcsRequestPrincipal;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

class AdminMcpToolExecutorTest {

    private final GatewayProxyClient proxy = mock(GatewayProxyClient.class);
    private final ApiGatewayRoutes routes = mock(ApiGatewayRoutes.class);
    private final AdminMcpToolExecutor executor = new AdminMcpToolExecutor(routes, proxy, new JsonMapper());

    @Test
    void usesTheExistingOperationPermission() {
        var result = executor.execute(operation("collection_task_start"),
                admin(IrcsPermissions.ADMIN_ACCESS, IrcsPermissions.TASK_READ),
                Map.of("id", UUID.randomUUID().toString()));

        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent()).isEqualTo(Map.of("status", 403, "message", "此后台操作权限不足"));
        verifyNoInteractions(proxy);
    }

    @Test
    void acceptsOnlyTheRegisteredPathShape() {
        var result = executor.execute(operation("raw_video_get"), admin(IrcsPermissions.ALL),
                Map.of("id", "not-a-uuid", "url", "https://example.invalid"));

        assertThat(result.isError()).isTrue();
        verifyNoInteractions(proxy);
    }

    @Test
    void delegatesWithTheAuthenticatedActorAndEncodesQueryValues() throws Exception {
        var sourceId = UUID.randomUUID();
        var request = admin(IrcsPermissions.ALL);
        when(proxy.invokeAdminOperation(any(), eq(routes), any(byte[].class), anyInt()))
                .thenReturn(new GatewayProxyClient.AdminOperationResponse(200, "[]".getBytes(StandardCharsets.UTF_8)));

        var result = executor.execute(operation("raw_video_get"), request,
                Map.of("id", sourceId.toString(), "query", Map.of("title", "喜剧 & 冒险")));

        assertThat(result.isError()).isFalse();
        var actual = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(proxy).invokeAdminOperation(actual.capture(), eq(routes), any(byte[].class), anyInt());
        assertThat(actual.getValue().getRequestURI()).isEqualTo("/api/v1/raw-videos/" + sourceId);
        assertThat(actual.getValue().getQueryString()).contains("%26");
        assertThat(actual.getValue().getMethod()).isEqualTo("GET");
        assertThat(actual.getValue().getHeader("Accept")).isEqualTo("application/json");
        assertThat(IrcsAuthHeaders.fromRequestAttribute(actual.getValue())).isPresent();
    }

    @Test
    void excludesCredentialPayloadsFromToolResults() throws Exception {
        var payload = """
                {"id":"test-credential","payload":{"password":"fixture"},"enabled":true}
                """;
        when(proxy.invokeAdminOperation(any(), eq(routes), any(byte[].class), anyInt()))
                .thenReturn(new GatewayProxyClient.AdminOperationResponse(200, payload.getBytes(StandardCharsets.UTF_8)));

        var result = executor.execute(operation("credential_get"), admin(IrcsPermissions.ALL),
                Map.of("id", UUID.randomUUID().toString()));

        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent().toString()).contains("test-credential").doesNotContain("payload", "fixture");
    }

    private static AdminMcpOperations.Operation operation(String name) {
        return AdminMcpOperations.ALL.stream().filter(candidate -> candidate.name().equals(name))
                .findFirst().orElseThrow();
    }

    private static McpTransportContext admin(String... permissions) {
        var request = new MockHttpServletRequest();
        var principal = new IrcsRequestPrincipal("mcp-fixture", IrcsPermissions.ROLE_ADMIN,
                Set.of(permissions), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
        IrcsAuthHeaders.setRequestAttribute(request, principal);
        return McpTransportContext.create(Map.of(AdminMcpConfiguration.REQUEST_CONTEXT, request));
    }
}
