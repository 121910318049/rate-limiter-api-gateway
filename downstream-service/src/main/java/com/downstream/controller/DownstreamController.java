package com.downstream.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class DownstreamController {

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getData() {
        log.info("Downstream received GET /api/data");
        return ResponseEntity.ok(Map.of(
                "message", "Data retrieved successfully",
                "timestamp", LocalDateTime.now().toString(),
                "service", "downstream-service"
        ));
    }

    @PostMapping("/payments")
    public ResponseEntity<Map<String, Object>> processPayment(
            @RequestBody(required = false) String body) {
        log.info("Downstream received POST /api/payments");
        return ResponseEntity.ok(Map.of(
                "message", "Payment processed successfully",
                "transactionId", "TXN-" + System.currentTimeMillis(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/auth/token")
    public ResponseEntity<Map<String, Object>> getToken() {
        log.info("Downstream received GET /api/auth/token");
        return ResponseEntity.ok(Map.of(
                "token", "mock-jwt-token-" + System.currentTimeMillis(),
                "expiresIn", 3600
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "downstream-service"
        ));
    }
}
