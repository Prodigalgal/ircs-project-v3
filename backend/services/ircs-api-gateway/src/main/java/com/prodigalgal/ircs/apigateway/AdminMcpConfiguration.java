package com.prodigalgal.ircs.apigateway;

import com.prodigalgal.ircs.common.security.IrcsAuthException;
import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.mcp.server.enabled", havingValue = "true", matchIfMissing = true)
class AdminMcpConfiguration {

    static final String ENDPOINT = "/api/v1/mcp";
    static final String REQUEST_CONTEXT = "ircs.admin.request";
    static final int REQUEST_LIMIT = 256 * 1024;

    @Bean
    WebMvcStatelessServerTransport adminMcpTransport(@Qualifier("mcpServerJsonMapper") JsonMapper mapper) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(mapper))
                .messageEndpoint(ENDPOINT)
                .contextExtractor(request -> McpTransportContext.create(Map.of(REQUEST_CONTEXT, request.servletRequest())))
                .build();
    }

    @Bean("webMvcStatelessServerRouterFunction")
    RouterFunction<ServerResponse> adminMcpRoutes(
            WebMvcStatelessServerTransport transport,
            ApiGatewayAuthenticator authenticator,
            @Value("${app.gateway.mcp.allowed-origin}") String allowedOrigin) {
        return transport.getRouterFunction()
                .andRoute(RequestPredicates.path(ENDPOINT),
                        request -> ServerResponse.status(HttpStatus.METHOD_NOT_ALLOWED).build())
                .filter((request, next) -> {
                    var origins = request.headers().header(HttpHeaders.ORIGIN);
                    if (!origins.isEmpty() && (origins.size() != 1 || allowedOrigin.isBlank()
                            || !allowedOrigin.equals(origins.getFirst()))) {
                        return ServerResponse.status(HttpStatus.FORBIDDEN).build();
                    }
                    if (request.servletRequest().getQueryString() != null) {
                        return ServerResponse.badRequest().build();
                    }
                    if (request.headers().firstHeader(HttpHeaders.AUTHORIZATION) == null
                            && request.headers().firstHeader(AdminApiTokenService.HEADER_API_TOKEN) == null) {
                        return unauthorized();
                    }
                    try {
                        var principal = authenticator.requireRoleAndPermission(request.servletRequest(),
                                IrcsPermissions.ROLE_ADMIN, IrcsPermissions.ADMIN_ACCESS);
                        IrcsAuthHeaders.setRequestAttribute(request.servletRequest(), principal);
                    } catch (IrcsAuthException ex) {
                        return ex.reason() == IrcsAuthException.Reason.FORBIDDEN
                                ? ServerResponse.status(HttpStatus.FORBIDDEN).build() : unauthorized();
                    }
                    if (!HttpMethod.POST.equals(request.method())) {
                        return next.handle(request);
                    }
                    if (request.headers().contentType().filter(MediaType.APPLICATION_JSON::isCompatibleWith).isEmpty()) {
                        return ServerResponse.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
                    }
                    if (request.headers().contentLength().orElse(0L) > REQUEST_LIMIT) {
                        return ServerResponse.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                    }
                    byte[] body = request.servletRequest().getInputStream().readNBytes(REQUEST_LIMIT + 1);
                    if (body.length > REQUEST_LIMIT) {
                        return ServerResponse.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
                    }
                    return next.handle(ServerRequest.from(request).body(body).build());
                });
    }

    @Bean
    List<SyncToolSpecification> adminMcpTools(AdminMcpToolExecutor executor) {
        return AdminMcpOperations.ALL.stream()
                .map(operation -> new SyncToolSpecification(operation.tool(),
                        (context, call) -> executor.execute(operation, context, call.arguments())))
                .toList();
    }

    private static ServerResponse unauthorized() {
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"ircs-admin\"")
                .build();
    }
}
