package com.ratelimiter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "client_tier", nullable = false)
    @Enumerated(EnumType.STRING)
    private ClientTier clientTier;

    @Column(name = "max_requests", nullable = false)
    private int maxRequests;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public static RateLimitRule defaultRule() {
        return RateLimitRule.builder()
                .endpoint("/**")
                .clientTier(ClientTier.FREE)
                .maxRequests(60)
                .windowSeconds(60)
                .build();
    }

    public enum ClientTier {
        FREE, PREMIUM, INTERNAL
    }
}
