package com.prodigalgal.ircs.config.application;

import com.prodigalgal.ircs.common.outbound.OutboundProxy;
import com.prodigalgal.ircs.common.outbound.OutboundProxyConnectivityProbe;
import com.prodigalgal.ircs.common.outbound.PublicOutboundProxyPool;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConfigConnectivityService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("R2", "PROXY", "MAIL", "LLM");
    private static final List<String> PROXY_PREFIXES = List.of(
            "app.metadata.tmdb.proxy.", "app.metadata.douban.proxy.",
            "app.metadata.rotten-tomatoes.proxy.", "app.proxy.", "proxy.", "");
    private final PublicOutboundProxyPool proxyPool;
    private final OutboundProxyConnectivityProbe proxyProbe;

    public ConfigConnectivityService(PublicOutboundProxyPool proxyPool, OutboundProxyConnectivityProbe proxyProbe) {
        this.proxyPool = proxyPool;
        this.proxyProbe = proxyProbe;
    }

    public Map<String, Object> testConnection(String type, Map<String, Object> params) {
        String normalizedType = normalizeType(type);
        if (!SUPPORTED_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Unknown test type: " + type);
        }
        Map<String, Object> fields = params == null ? Map.of() : params;
        if (!"PROXY".equals(normalizedType)) {
            return Map.of("message", "未执行连接测试，仅完成参数接收与契约校验", "type", normalizedType,
                    "mode", "dry-run", "status", "NOT_TESTED", "success", false,
                    "detail", "未访问外部资源，不能据此确认连接可用", "fieldCount", String.valueOf(fields.size()));
        }
        try {
            OutboundProxy proxy = proxy(fields);
            boolean configuredInternal = proxyPool.isConfiguredEndpoint(proxy.host(), proxy.port());
            int status = proxyProbe.probe(proxy, configuredInternal);
            if (status < 200 || status >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理 HTTPS 探测返回非成功状态: " + status);
            }
            return Map.of("message", "代理 HTTPS 连接测试成功", "type", "PROXY", "mode", "live",
                    "status", "CONNECTED", "success", true, "httpStatus", String.valueOf(status),
                    "target", OutboundProxyConnectivityProbe.TARGET);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "代理连接测试被中断");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "代理 HTTPS 连接测试失败或超时，请检查代理服务及认证配置");
        }
    }

    private OutboundProxy proxy(Map<String, Object> params) throws IOException {
        String prefix = PROXY_PREFIXES.stream().filter(value -> params.containsKey(value + "host"))
                .findFirst().orElse(null);
        if (prefix == null) {
            if (params.containsKey("proxyHost")) {
                return OutboundProxy.http(text(params.get("proxyHost")), port(params.get("proxyPort")),
                        text(params.get("username")), text(params.get("password")));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供代理主机和端口");
        }
        String type = text(params.get(prefix + "type"));
        if (!type.isEmpty() && !"HTTP".equalsIgnoreCase(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前连接测试只支持 HTTP 代理网关");
        }
        String host = text(params.get(prefix + "host"));
        if (host.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理主机不能为空");
        }
        return OutboundProxy.http(host, port(params.get(prefix + "port")),
                text(params.get(prefix + "username")), text(params.get(prefix + "password")));
    }

    private int port(Object raw) {
        try {
            int port = Integer.parseInt(text(raw));
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代理端口必须在 1 到 65535 之间");
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Connection test type is required");
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
