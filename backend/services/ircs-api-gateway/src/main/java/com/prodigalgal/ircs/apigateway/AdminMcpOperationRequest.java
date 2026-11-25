package com.prodigalgal.ircs.apigateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

final class AdminMcpOperationRequest extends HttpServletRequestWrapper {

    private static final List<String> FORWARDED_HEADERS = List.of(
            HttpHeaders.ACCEPT, HttpHeaders.CONTENT_TYPE, HttpHeaders.USER_AGENT,
            "traceparent", "tracestate", "X-Request-ID");

    private final String method;
    private final String path;
    private final String query;

    AdminMcpOperationRequest(HttpServletRequest request, String method, String path, String query) {
        super(request);
        this.method = method;
        this.path = path;
        this.query = query;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getRequestURI() {
        return path;
    }

    @Override
    public String getQueryString() {
        return query;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(FORWARDED_HEADERS);
    }

    @Override
    public String getHeader(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "accept", "content-type" -> MediaType.APPLICATION_JSON_VALUE;
            case "user-agent" -> "ircs-admin-mcp";
            default -> super.getHeader(name);
        };
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String value = getHeader(name);
        return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
    }
}
