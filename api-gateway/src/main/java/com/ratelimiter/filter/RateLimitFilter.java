package com.ratelimiter.filter;

import com.ratelimiter.metrics.RateLimitMetrics;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.service.RateLimiterService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements Filter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitMetrics metrics;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // Skip health check and actuator endpoints
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator") || uri.equals("/health")) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        long startTime = System.currentTimeMillis();

        // Resolve client identity
        String clientId = resolveClientId(request);

        // Check rate limit
        RateLimitResult result = rateLimiterService.isAllowed(clientId, uri);

        // Record metrics
        metrics.recordRequest(clientId, uri, result.isAllowed());
        metrics.recordRateLimitCheckDuration(uri, System.currentTimeMillis() - startTime);

        // Always set rate limit headers
        response.setHeader("X-RateLimit-Limit",
                String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Client",
                clientId);

        if (!result.isAllowed()) {
            log.warn("Blocked request: client={}, uri={}, count={}/{}",
                    clientId, uri, result.getCurrentCount(), result.getLimit());

            response.setStatus(429);
            response.setHeader("Retry-After",
                    String.valueOf(result.getRetryAfterSeconds()));
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write(
                    String.format(
                        "{\"error\": \"Too Many Requests\", " +
                        "\"limit\": %d, " +
                        "\"retryAfterSeconds\": %d}",
                        result.getLimit(),
                        result.getRetryAfterSeconds()
                    )
            );
            return; // Stop here — don't call chain.doFilter()
        }

        // Request allowed — pass through
        chain.doFilter(servletRequest, servletResponse);
    }

    /**
     * Client identity resolution priority:
     * 1. X-API-Key header      → "apikey:{value}"
     * 2. X-User-Id header      → "user:{value}"
     * 3. Remote IP address     → "ip:{address}"
     */
    private String resolveClientId(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "apikey:" + apiKey;
        }

        String userId = request.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
