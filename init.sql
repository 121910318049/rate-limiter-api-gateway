-- This runs automatically when PostgreSQL container starts for the first time

CREATE TABLE IF NOT EXISTS rate_limit_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint        VARCHAR(255) NOT NULL,
    client_tier     VARCHAR(50)  NOT NULL DEFAULT 'FREE',
    max_requests    INT          NOT NULL DEFAULT 60,
    window_seconds  INT          NOT NULL DEFAULT 60,
    created_at      TIMESTAMP    DEFAULT NOW()
);

-- Default rules
INSERT INTO rate_limit_rules (endpoint, client_tier, max_requests, window_seconds) VALUES
    ('/api/**',         'FREE',     60,    60),
    ('/api/**',         'PREMIUM',  300,   60),
    ('/api/**',         'INTERNAL', 99999, 60),
    ('/api/payments/**','FREE',     10,    60),
    ('/api/auth/**',    'FREE',     5,     60);
