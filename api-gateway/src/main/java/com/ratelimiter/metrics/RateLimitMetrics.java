package com.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitMetrics {

    private final Counter totalRequests;
    private final Counter blockedRequests;
    private final Counter allowedRequests;
    private final MeterRegistry registry;

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.totalRequests = Counter.builder("ratelimit_requests_total")
                .description("Total number of requests processed by rate limiter")
                .register(registry);

        this.blockedRequests = Counter.builder("ratelimit_blocked_total")
                .description("Total number of requests blocked by rate limiter")
                .register(registry);

        this.allowedRequests = Counter.builder("ratelimit_allowed_total")
                .description("Total number of requests allowed by rate limiter")
                .register(registry);
    }

    public void recordRequest(String clientId, String endpoint, boolean allowed) {
        // Total requests tagged by client and endpoint
        registry.counter("ratelimit_requests_total",
                Tags.of(
                        "client", sanitize(clientId),
                        "endpoint", sanitize(endpoint),
                        "result", allowed ? "allowed" : "blocked"
                )).increment();

        // Separate allowed/blocked counters for easy PromQL
        if (allowed) {
            registry.counter("ratelimit_allowed_total",
                    Tags.of(
                            "client", sanitize(clientId),
                            "endpoint", sanitize(endpoint)
                    )).increment();
        } else {
            registry.counter("ratelimit_blocked_total",
                    Tags.of(
                            "client", sanitize(clientId),
                            "endpoint", sanitize(endpoint)
                    )).increment();
        }
    }

    public void recordRateLimitCheckDuration(String endpoint, long durationMs) {
        registry.timer("ratelimit_check_duration_ms",
                Tags.of("endpoint", sanitize(endpoint)))
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // Sanitize to avoid high-cardinality label issues in Prometheus
    private String sanitize(String value) {
        if (value == null) return "unknown";
        // Truncate long values, replace special chars
        return value.replaceAll("[^a-zA-Z0-9._/-]", "_")
                    .substring(0, Math.min(value.length(), 50));
    }
}
