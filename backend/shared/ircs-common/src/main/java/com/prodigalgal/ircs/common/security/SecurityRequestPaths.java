package com.prodigalgal.ircs.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

public final class SecurityRequestPaths {
    private SecurityRequestPaths() { }

    /** Match MVC-equivalent trailing slashes, matrix parameters and percent-encoded characters identically. */
    public static String canonicalPath(HttpServletRequest request) {
        String path = UriUtils.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) path = path.substring(context.length());
        if (path.indexOf('\\') >= 0 || path.indexOf('%') >= 0) throw new IllegalArgumentException("Ambiguous request path");
        StringBuilder canonical = new StringBuilder();
        for (String segment : path.split("/")) {
            String value = segment.split(";", 2)[0];
            if (value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Ambiguous request path");
            if (!value.isEmpty()) canonical.append('/').append(value);
        }
        return canonical.isEmpty() ? "/" : canonical.toString();
    }

    public static boolean gatewayApiPath(String path) {
        return path.equals("/api/v1") || path.startsWith("/api/v1/")
                || path.equals("/api/portal") || path.startsWith("/api/portal/");
    }
}
