package com.prodigalgal.ircs.apigateway;

import com.prodigalgal.ircs.common.security.IrcsAuthHeaders;
import com.prodigalgal.ircs.common.security.IrcsPermissions;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
class AdminMcpToolExecutor {

    private static final int RESPONSE_LIMIT = 512 * 1024;
    private static final int MAX_QUERY_PARAMETERS = 24;
    private static final int MAX_QUERY_VALUE_LENGTH = 1000;
    private static final Pattern QUERY_PARAMETER = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern CONFIG_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}");
    private static final Set<String> SECRET_QUERY_PARAMETERS = Set.of("token", "password", "secret", "apikey");
    private static final List<String> SECRET_RESPONSE_FIELDS = List.of(
            "password", "passwordHash", "proxyPassword", "secretPayload", "accessToken", "refreshToken");

    private final ApiGatewayRoutes routes;
    private final GatewayProxyClient proxyClient;
    private final ObjectMapper mapper;

    AdminMcpToolExecutor(ApiGatewayRoutes routes, GatewayProxyClient proxyClient, ObjectMapper mapper) {
        this.routes = routes;
        this.proxyClient = proxyClient;
        this.mapper = mapper;
    }

    CallToolResult execute(
            AdminMcpOperations.Operation operation, McpTransportContext context, Map<String, Object> arguments) {
        if (!(context.get(AdminMcpConfiguration.REQUEST_CONTEXT) instanceof HttpServletRequest request)) {
            return failure(401, "缺少管理员凭证");
        }
        var principal = IrcsAuthHeaders.fromRequestAttribute(request).orElse(null);
        if (principal == null || !IrcsPermissions.ROLE_ADMIN.equals(principal.role())
                || !principal.hasPermission(IrcsPermissions.ADMIN_ACCESS)) {
            return failure(403, "管理员权限不足");
        }

        var input = arguments == null ? Map.<String, Object>of() : arguments;
        try {
            String path = resolvePath(operation, input);
            String method = operation.method().name();
            if (!principal.hasPermission(ApiGatewayProxyController.adminPermissionFor(path, method))) {
                return failure(403, "此后台操作权限不足");
            }
            var forwarded = new AdminMcpOperationRequest(request, method, path, encodeQuery(input.get("query")));
            byte[] body = operation.body() ? mapper.writeValueAsBytes(input.get("body")) : new byte[0];
            var upstream = proxyClient.invokeAdminOperation(forwarded, routes, body, RESPONSE_LIMIT);
            if (upstream.status() < 200 || upstream.status() >= 300) {
                return failure(upstream.status(), "后台接口返回 HTTP " + upstream.status());
            }
            var output = new LinkedHashMap<String, Object>();
            output.put("status", upstream.status());
            if (upstream.body().length > 0) {
                JsonNode data = mapper.readTree(upstream.body());
                removeSecrets(data, path.startsWith("/api/v1/credentials"));
                output.put("data", data);
            }
            return callResult(output, false);
        } catch (IllegalArgumentException ex) {
            return failure(400, "请按工具参数结构提供记录 ID、查询条件和请求字段");
        } catch (ResponseStatusException ex) {
            return failure(ex.getStatusCode().value(), "后台请求未能返回可用结果");
        } catch (IOException | JacksonException ex) {
            return failure(502, "无法读取后台接口响应");
        }
    }

    private static String resolvePath(AdminMcpOperations.Operation operation, Map<String, Object> arguments) {
        var supported = new HashSet<>(operation.pathParameters());
        if (operation.body()) {
            supported.add("body");
            if (!(arguments.get("body") instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Missing JSON object body");
            }
        }
        if (operation.query()) {
            supported.add("query");
        }
        if (!supported.containsAll(arguments.keySet())) {
            throw new IllegalArgumentException("Unsupported tool arguments");
        }
        String path = "/api/v1" + operation.path();
        for (String parameter : operation.pathParameters()) {
            if (!(arguments.get(parameter) instanceof String value)) {
                throw new IllegalArgumentException("Missing path parameter");
            }
            if ("key".equals(parameter)) {
                if (!CONFIG_KEY.matcher(value).matches()) {
                    throw new IllegalArgumentException("Invalid config key");
                }
            } else if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Invalid UUID");
            }
            path = path.replace("{" + parameter + "}", value);
        }
        return path;
    }

    private static String encodeQuery(Object query) {
        if (query == null) {
            return null;
        }
        if (!(query instanceof Map<?, ?> parameters) || parameters.size() > MAX_QUERY_PARAMETERS) {
            throw new IllegalArgumentException("Invalid query parameter object");
        }
        var encoded = new ArrayList<String>(parameters.size());
        for (var entry : parameters.entrySet()) {
            if (!(entry.getKey() instanceof String name) || !QUERY_PARAMETER.matcher(name).matches()
                    || SECRET_QUERY_PARAMETERS.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Invalid query parameter name");
            }
            Object value = entry.getValue();
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)
                    || value.toString().length() > MAX_QUERY_VALUE_LENGTH) {
                throw new IllegalArgumentException("Invalid query parameter value");
            }
            encoded.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        }
        return String.join("&", encoded);
    }

    private static void removeSecrets(JsonNode data, boolean credentialResponse) {
        if (data == null) {
            return;
        }
        if (data instanceof ObjectNode object) {
            object.remove(SECRET_RESPONSE_FIELDS);
            if (credentialResponse) {
                object.remove("payload");
            }
        }
        data.forEach(child -> removeSecrets(child, credentialResponse));
    }

    private CallToolResult failure(int status, String message) {
        return callResult(Map.of("status", status, "message", message), true);
    }

    private CallToolResult callResult(Map<String, Object> result, boolean error) {
        return CallToolResult.builder().structuredContent(result)
                .addTextContent(mapper.writeValueAsString(result))
                .isError(error).build();
    }
}
