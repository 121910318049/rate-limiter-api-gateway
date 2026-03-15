package com.ratelimiter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;

@Service
@Slf4j
public class ProxyService {

    private final RestTemplate restTemplate;

    @Value("${downstream.service.url}")
    private String downstreamUrl;

    public ProxyService() {
        this.restTemplate = new RestTemplate();
    }

    public ResponseEntity<String> forward(HttpServletRequest request, String body) {
        String targetUrl = downstreamUrl + request.getRequestURI();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        HttpHeaders headers = copyHeaders(request);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        log.debug("Forwarding {} {} to {}", method, request.getRequestURI(), targetUrl);

        try {
            return restTemplate.exchange(targetUrl, method, entity, String.class);
        } catch (Exception e) {
            log.error("Error forwarding request to downstream: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\": \"Downstream service unavailable\"}");
        }
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            Collections.list(headerNames).forEach(name -> {
                if (!name.equalsIgnoreCase("host")) {
                    headers.set(name, request.getHeader(name));
                }
            });
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
