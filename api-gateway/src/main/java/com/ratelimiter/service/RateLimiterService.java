package com.ratelimiter.service;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitRuleRepository ruleRepository;

    /**
     * Sliding Window Log Algorithm using Redis Sorted Sets.
     *
     * How it works:
     * 1. Each request adds a timestamp entry to a Sorted Set (score = timestamp)
     * 2. Remove all entries older than (now - windowSize) — outside the window
     * 3. Count remaining entries = current request count in window
     * 4. If count >= limit → BLOCK
     * 5. If count < limit → ALLOW, add new entry
     *
     * Redis key: "rate:{clientId}:{endpoint}"
     * Redis value: Sorted Set of UUID strings scored by timestamp (ms)
     */
    public RateLimitResult isAllowed(String clientId, String endpoint) {

        RateLimitRule rule = resolveRule(clientId, endpoint);

        String redisKey = buildKey(clientId, endpoint);
        long now = System.currentTimeMillis();
        long windowStart = now - (rule.getWindowSeconds() * 1000L);

        // Step 1: Remove timestamps outside the current window
        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        // Step 2: Count how many requests are in the current window
        Long currentCount = redisTemplate.opsForZSet().zCard(redisKey);
        long count = currentCount == null ? 0 : currentCount;

        log.debug("Rate check: client={}, endpoint={}, count={}, limit={}",
                clientId, endpoint, count, rule.getMaxRequests());

        // Step 3: Block if at or above limit
        if (count >= rule.getMaxRequests()) {
            log.warn("Rate limit EXCEEDED: client={}, endpoint={}, count={}/{}",
                    clientId, endpoint, count, rule.getMaxRequests());
            return RateLimitResult.blocked(
                    count, rule.getMaxRequests(),
                    rule.getWindowSeconds(), clientId, endpoint);
        }

        // Step 4: Allow — add current timestamp to sorted set
        redisTemplate.opsForZSet().add(
                redisKey,
                UUID.randomUUID().toString(),  // unique member
                now                             // score = timestamp
        );

        // Step 5: Set TTL on the key so Redis auto-cleans
        redisTemplate.expire(redisKey, rule.getWindowSeconds() * 2L, TimeUnit.SECONDS);

        return RateLimitResult.allowed(
                count + 1, rule.getMaxRequests(), clientId, endpoint);
    }

    /**
     * Resolve the best matching rule for this client and endpoint.
     * Falls back to default rule if nothing matches.
     */
    private RateLimitRule resolveRule(String clientId, String endpoint) {
        RateLimitRule.ClientTier tier = resolveTier(clientId);

        return ruleRepository
                .findBestMatchingRule(endpoint, tier)
                .orElse(RateLimitRule.defaultRule());
    }

    /**
     * Determine client tier from clientId prefix.
     * - "apikey:premium_..." → PREMIUM
     * - "internal:..."       → INTERNAL
     * - everything else      → FREE
     */
    private RateLimitRule.ClientTier resolveTier(String clientId) {
        if (clientId.startsWith("internal:")) {
            return RateLimitRule.ClientTier.INTERNAL;
        }
        if (clientId.startsWith("apikey:premium")) {
            return RateLimitRule.ClientTier.PREMIUM;
        }
        return RateLimitRule.ClientTier.FREE;
    }

    private String buildKey(String clientId, String endpoint) {
        // Normalize endpoint to avoid Redis key issues
        String normalizedEndpoint = endpoint.replaceAll("[^a-zA-Z0-9/]", "_");
        return "rate:" + clientId + ":" + normalizedEndpoint;
    }
}
