# Rate Limiter & API Gateway

A production-grade rate limiting API gateway built with Java, Spring Boot, Redis, PostgreSQL, Prometheus, and Grafana.

## Architecture

```
[Client] → [API Gateway :8080] → [Rate Limiter] → [Downstream Service :8081]
                                       ↓
                                    [Redis]
                              (sliding window counters)
                                       ↓
                               [Prometheus :9090]
                                       ↓
                               [Grafana :3000]
                              (real-time dashboards)
```

## Tech Stack

| Component       | Technology                        |
|----------------|-----------------------------------|
| Language        | Java 17                           |
| Framework       | Spring Boot 3.2                   |
| Cache           | Redis 7 (Sorted Sets)             |
| Database        | PostgreSQL 15 (rule config)       |
| Metrics         | Prometheus + Micrometer           |
| Dashboards      | Grafana                           |
| Containers      | Docker + Docker Compose           |
| Testing         | JUnit 5 + Mockito + TestContainers|

## Algorithm — Sliding Window Log

```
Every request:
1. Remove all timestamps older than (now - windowSize) from Redis Sorted Set
2. Count remaining timestamps = requests in current window
3. If count >= limit → return 429 Too Many Requests
4. If count < limit  → allow, add current timestamp to Sorted Set
```

**Why Sliding Window over Fixed Window:**  
Fixed window allows burst at boundaries (100 req at 59s + 100 at 61s = 200 in 2s).  
Sliding window prevents this — no boundary spike possible.

**Why Redis Sorted Set:**  
Scores are timestamps. `ZREMRANGEBYSCORE` removes expired entries in O(log N).  
`ZCARD` counts remaining in O(1).

## How to Run

### Prerequisites
- Docker
- Docker Compose

### Start Everything
```bash
docker-compose up --build
```

### Services
| Service            | URL                          |
|-------------------|------------------------------|
| API Gateway        | http://localhost:8080         |
| Downstream Service | http://localhost:8081         |
| Prometheus         | http://localhost:9090         |
| Grafana            | http://localhost:3000 (admin/admin) |

## API Endpoints

### Proxied via Gateway (rate limited)
```
GET  /api/data           → proxied to downstream
POST /api/payments       → proxied, strict limit (10/min FREE)
GET  /api/auth/token     → proxied, very strict (5/min FREE)
```

### Admin (rule management)
```
GET    /admin/rules         → list all rules
POST   /admin/rules         → create rule
PUT    /admin/rules/{id}    → update rule
DELETE /admin/rules/{id}    → delete rule
```

## Rate Limit Rules (Default)

| Endpoint          | Tier     | Limit     | Window |
|------------------|----------|-----------|--------|
| /api/**           | FREE     | 60 req    | 60s    |
| /api/**           | PREMIUM  | 300 req   | 60s    |
| /api/**           | INTERNAL | Unlimited | 60s    |
| /api/payments/**  | FREE     | 10 req    | 60s    |
| /api/auth/**      | FREE     | 5 req     | 60s    |

## Client Identity Resolution

Priority order:
1. `X-API-Key` header → `apikey:{value}`
2. `X-User-Id` header → `user:{value}`
3. `X-Forwarded-For` header → `ip:{value}`
4. Remote IP address → `ip:{address}`

Premium tier: API keys starting with `apikey:premium`  
Internal tier: Client IDs starting with `internal:`

## Sample Requests

### Allowed Request
```bash
curl -H "X-User-Id: user123" http://localhost:8080/api/data

# Response headers:
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 59
X-RateLimit-Client: user:user123
```

### Blocked Request (429)
```bash
# After exceeding limit
curl -H "X-User-Id: user123" http://localhost:8080/api/data

# HTTP 429
# Retry-After: 60
# {"error": "Too Many Requests", "limit": 60, "retryAfterSeconds": 60}
```

### Premium Client
```bash
curl -H "X-API-Key: premium_mykey123" http://localhost:8080/api/data

# X-RateLimit-Limit: 300  ← higher limit
```

### Create Custom Rule via Admin API
```bash
curl -X POST http://localhost:8080/admin/rules \
  -H "Content-Type: application/json" \
  -d '{
    "endpoint": "/api/reports/**",
    "clientTier": "FREE",
    "maxRequests": 5,
    "windowSeconds": 60
  }'
```

## Prometheus Metrics

Access raw metrics: `http://localhost:8080/actuator/prometheus`

Key metrics:
```
ratelimit_requests_total     # total requests by client + endpoint + result
ratelimit_allowed_total      # allowed requests
ratelimit_blocked_total      # blocked requests
ratelimit_check_duration_ms  # time taken to check rate limit
```

## Grafana Dashboard

Auto-provisioned at startup. Open `http://localhost:3000` (admin/admin).

Dashboard panels:
- **Total Requests/sec** — overall traffic
- **Blocked Requests/sec** — rate of blocked traffic
- **Block Rate % by Endpoint** — which endpoints are most throttled
- **Top 10 Clients** — busiest clients
- **Allowed vs Blocked Pie** — ratio over last 5 minutes
- **Check Duration** — Redis lookup latency

## Running Tests

```bash
# Unit tests only
cd api-gateway && mvn test -Dtest="RateLimiterServiceTest,RateLimitFilterTest"

# Integration tests (requires Docker for TestContainers)
cd api-gateway && mvn test -Dtest="RateLimiterIntegrationTest"

# All tests
cd api-gateway && mvn test
```

## Design Decisions

**Redis Sorted Set for sliding window:**  
Each request adds a UUID member with timestamp score. Expired entries removed on each check.  
Atomic operations ensure correctness under concurrent requests.

**PostgreSQL for rule config:**  
Rules are configurable at runtime via admin API without redeployment.  
Different client tiers get different limits from the same codebase.

**Filter chain over AOP:**  
Spring Filter runs before DispatcherServlet — blocks request before any controller code runs.  
AOP would still invoke Spring infrastructure. Filter is cleaner for request-level concerns.

**Separate metrics per client + endpoint:**  
Prometheus labels allow filtering by any dimension in Grafana.  
Cardinality kept low by sanitizing client IDs (truncated, special chars replaced).
