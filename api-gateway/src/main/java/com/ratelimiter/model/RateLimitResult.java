package com.ratelimiter.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateLimitResult {

    private boolean allowed;
    private long currentCount;
    private int limit;
    private long remaining;
    private int retryAfterSeconds;
    private String clientId;
    private String endpoint;

    public static RateLimitResult allowed(long count, int limit,
                                           String clientId, String endpoint) {
        return RateLimitResult.builder()
                .allowed(true)
                .currentCount(count)
                .limit(limit)
                .remaining(Math.max(0, limit - count))
                .clientId(clientId)
                .endpoint(endpoint)
                .build();
    }

    public static RateLimitResult blocked(long count, int limit,
                                           int retryAfter,
                                           String clientId, String endpoint) {
        return RateLimitResult.builder()
                .allowed(false)
                .currentCount(count)
                .limit(limit)
                .remaining(0)
                .retryAfterSeconds(retryAfter)
                .clientId(clientId)
                .endpoint(endpoint)
                .build();
    }
}
