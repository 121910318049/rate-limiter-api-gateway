package com.ratelimiter.repository;

import com.ratelimiter.model.RateLimitRule;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RateLimitRuleRepository
        extends JpaRepository<RateLimitRule, UUID> {

    @Query("SELECT r FROM RateLimitRule r " +
            "WHERE :endpoint LIKE REPLACE(r.endpoint, '**', '%') " +
            "AND r.clientTier = :tier " +
            "ORDER BY LENGTH(r.endpoint) DESC")
    List<RateLimitRule> findMatchingRules(
            @Param("endpoint") String endpoint,
            @Param("tier") RateLimitRule.ClientTier tier,
            Pageable pageable);
}