package com.ratelimiter.repository;

import com.ratelimiter.model.RateLimitRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RateLimitRuleRepository extends JpaRepository<RateLimitRule, UUID> {

    // Find exact endpoint match first
    Optional<RateLimitRule> findByEndpointAndClientTier(
            String endpoint,
            RateLimitRule.ClientTier clientTier);

    // Find by endpoint only (use default tier)
    Optional<RateLimitRule> findFirstByEndpoint(String endpoint);

    // Find most specific matching rule using LIKE
    @Query("SELECT r FROM RateLimitRule r " +
           "WHERE :endpoint LIKE REPLACE(r.endpoint, '**', '%') " +
           "AND r.clientTier = :tier " +
           "ORDER BY LENGTH(r.endpoint) DESC")
    Optional<RateLimitRule> findBestMatchingRule(
            @Param("endpoint") String endpoint,
            @Param("tier") RateLimitRule.ClientTier tier);
}
