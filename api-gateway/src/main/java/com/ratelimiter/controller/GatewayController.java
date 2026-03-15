package com.ratelimiter.controller;

import com.ratelimiter.model.RateLimitRule;
import com.ratelimiter.repository.RateLimitRuleRepository;
import com.ratelimiter.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GatewayController {

    private final RateLimitRuleRepository ruleRepository;
    private final ProxyService proxyService;

    // ── Rate Limit Rules Management ─────────────────────────────────────────

    @GetMapping("/admin/rules")
    public ResponseEntity<List<RateLimitRule>> getAllRules() {
        return ResponseEntity.ok(ruleRepository.findAll());
    }

    @PostMapping("/admin/rules")
    public ResponseEntity<RateLimitRule> createRule(
            @RequestBody RateLimitRule rule) {
        RateLimitRule saved = ruleRepository.save(rule);
        log.info("Created rule: endpoint={}, tier={}, limit={}/{}s",
                saved.getEndpoint(), saved.getClientTier(),
                saved.getMaxRequests(), saved.getWindowSeconds());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/admin/rules/{id}")
    public ResponseEntity<RateLimitRule> updateRule(
            @PathVariable UUID id,
            @RequestBody RateLimitRule rule) {
        return ruleRepository.findById(id)
                .map(existing -> {
                    existing.setMaxRequests(rule.getMaxRequests());
                    existing.setWindowSeconds(rule.getWindowSeconds());
                    existing.setClientTier(rule.getClientTier());
                    return ResponseEntity.ok(ruleRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/admin/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Proxy — forward to downstream ────────────────────────────────────────

    @RequestMapping("/api/**")
    public ResponseEntity<String> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {
        log.debug("Proxying request: {} {}", request.getMethod(), request.getRequestURI());
        return proxyService.forward(request, body);
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("{\"status\": \"UP\"}");
    }
}
