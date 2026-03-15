package com.ratelimiter.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RateLimitMetrics {

    private final MeterRegistry registry;

    public RateLimitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String clientId,
                              String endpoint,
                              boolean allowed) {
        // Use registry.counter() with tags directly
        // Micrometer caches these — safe to call on every request
        registry.counter(
                "ratelimit_requests_total",
                "client",   sanitize(clientId),
                "endpoint", sanitize(endpoint),
                "result",   allowed ? "allowed" : "blocked"
        ).increment();

        if (allowed) {
            registry.counter(
                    "ratelimit_allowed_total",
                    "client",   sanitize(clientId),
                    "endpoint", sanitize(endpoint)
            ).increment();
        } else {
            registry.counter(
                    "ratelimit_blocked_total",
                    "client",   sanitize(clientId),
                    "endpoint", sanitize(endpoint)
            ).increment();
        }

        log.debug("Metric recorded: client={}, endpoint={}, allowed={}",
                clientId, endpoint, allowed);
    }

    public void recordRateLimitCheckDuration(String endpoint,
                                             long durationMs) {
        registry.timer(
                "ratelimit_check_duration_ms",
                "endpoint", sanitize(endpoint)
        ).record(durationMs, TimeUnit.MILLISECONDS);
    }

    private String sanitize(String value) {
        if (value == null) return "unknown";
        return value
                .replaceAll("[^a-zA-Z0-9._/-]", "_")
                .substring(0, Math.min(value.length(), 50));
    }
}