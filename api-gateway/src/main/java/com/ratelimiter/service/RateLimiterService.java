package com.ratelimiter.service;

import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitRuleRepository ruleRepository;

    public RateLimitResult isAllowed(String clientId, String endpoint) {

        RateLimitRule rule = resolveRule(clientId, endpoint);

        String redisKey = buildKey(clientId, endpoint);
        long now = System.currentTimeMillis();
        long windowStart = now - (rule.getWindowSeconds() * 1000L);

        // Remove timestamps outside current window
        redisTemplate.opsForZSet()
                .removeRangeByScore(redisKey, 0, windowStart);

        // Count requests in current window
        Long currentCount = redisTemplate.opsForZSet().zCard(redisKey);
        long count = currentCount == null ? 0 : currentCount;

        log.debug("Rate check: client={}, endpoint={}, count={}, limit={}",
                clientId, endpoint, count, rule.getMaxRequests());

        // Block if at or above limit
        if (count >= rule.getMaxRequests()) {
            log.warn("Rate limit EXCEEDED: client={}, endpoint={}, count={}/{}",
                    clientId, endpoint, count, rule.getMaxRequests());
            return RateLimitResult.blocked(
                    count, rule.getMaxRequests(),
                    rule.getWindowSeconds(), clientId, endpoint);
        }

        // Allow — add current timestamp
        redisTemplate.opsForZSet().add(
                redisKey,
                UUID.randomUUID().toString(),
                now
        );

        // Set TTL
        redisTemplate.expire(
                redisKey,
                rule.getWindowSeconds() * 2L,
                TimeUnit.SECONDS);

        return RateLimitResult.allowed(
                count + 1, rule.getMaxRequests(), clientId, endpoint);
    }

    private RateLimitRule resolveRule(String clientId, String endpoint) {
        RateLimitRule.ClientTier tier = resolveTier(clientId);

        List<RateLimitRule> rules = ruleRepository.findMatchingRules(
                endpoint,
                tier,
                PageRequest.of(0, 1)  // get only top 1 — most specific match
        );

        if (rules.isEmpty()) {
            log.debug("No rule found for endpoint={}, tier={} — using default",
                    endpoint, tier);
            return RateLimitRule.defaultRule();
        }

        log.debug("Rule resolved: endpoint={}, limit={}/{}s",
                rules.get(0).getEndpoint(),
                rules.get(0).getMaxRequests(),
                rules.get(0).getWindowSeconds());

        return rules.get(0);
    }

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
        String normalizedEndpoint =
                endpoint.replaceAll("[^a-zA-Z0-9/]", "_");
        return "rate:" + clientId + ":" + normalizedEndpoint;
    }
}