package com.ratelimiter.config;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RateLimitRuleRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            log.info("Seeding default rate limit rules...");

            List<RateLimitRule> rules = List.of(
                // Global default — FREE tier
                RateLimitRule.builder()
                    .endpoint("/api/**")
                    .clientTier(RateLimitRule.ClientTier.FREE)
                    .maxRequests(60)
                    .windowSeconds(60)
                    .build(),

                // Global default — PREMIUM tier
                RateLimitRule.builder()
                    .endpoint("/api/**")
                    .clientTier(RateLimitRule.ClientTier.PREMIUM)
                    .maxRequests(300)
                    .windowSeconds(60)
                    .build(),

                // Internal — unlimited
                RateLimitRule.builder()
                    .endpoint("/api/**")
                    .clientTier(RateLimitRule.ClientTier.INTERNAL)
                    .maxRequests(99999)
                    .windowSeconds(60)
                    .build(),

                // Specific endpoint — stricter limit
                RateLimitRule.builder()
                    .endpoint("/api/payments/**")
                    .clientTier(RateLimitRule.ClientTier.FREE)
                    .maxRequests(10)
                    .windowSeconds(60)
                    .build(),

                // Auth endpoint — very strict
                RateLimitRule.builder()
                    .endpoint("/api/auth/**")
                    .clientTier(RateLimitRule.ClientTier.FREE)
                    .maxRequests(5)
                    .windowSeconds(60)
                    .build()
            );

            repository.saveAll(rules);
            log.info("Seeded {} rate limit rules", rules.size());
        }
    }
}
