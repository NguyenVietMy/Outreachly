CREATE TABLE integration_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider         VARCHAR(30) NOT NULL
                         CHECK (provider IN ('github', 'obsidian', 'slack', 'linear')),
    event_type       VARCHAR(50) NOT NULL,
    title            TEXT NOT NULL,
    external_id      VARCHAR(255) NOT NULL,
    event_timestamp  TIMESTAMPTZ NOT NULL,
    raw_payload      JSONB DEFAULT '{}',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, provider, external_id)
);

CREATE INDEX idx_integration_events_user_provider ON integration_events(user_id, provider);
CREATE INDEX idx_integration_events_timestamp ON integration_events(user_id, event_timestamp DESC);
