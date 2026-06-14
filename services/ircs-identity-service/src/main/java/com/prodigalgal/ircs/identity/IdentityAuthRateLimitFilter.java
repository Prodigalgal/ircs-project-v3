package com.prodigalgal.ircs.identity;

import com.prodigalgal.ircs.common.web.ApiErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdentityAuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RATE_LIMITED_POST_PATHS = Set.of(
            "/api/portal/auth/register",
            "/api/portal/auth/login",
            "/api/portal/auth/forgot-password",
            "/api/portal/auth/reset-password",
            "/api/portal/auth/activate",
            "/api/portal/auth/resend-code",
            "/api/v1/auth/login");

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int maxAttempts;
    private final Duration window;

    public IdentityAuthRateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${app.identity.auth-rate-limit.enabled:true}") boolean enabled,
            @Value("${app.identity.auth-rate-limit.max-attempts:30}") int maxAttempts,
            @Value("${app.identity.auth-rate-limit.window:PT5M}") Duration window) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.maxAttempts = maxAttempts;
        this.window = window == null || window.isZero() || window.isNegative() ? Duration.ofMinutes(5) : window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !RATE_LIMITED_POST_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String bucket = request.getRequestURI() + ":" + clientIp(request);
        String key = IdentityRedisKeys.authRateLimit(bucket);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > maxAttempts) {
            ApiErrorResponses.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "auth.rate.limit",
                    "Too many authentication attempts",
                    "auth");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp.trim() : request.getRemoteAddr();
    }
}
