ALTER TABLE user_integrations
    DROP COLUMN auto_sync_enabled,
    DROP COLUMN consecutive_failures,
    DROP COLUMN next_sync_after,
    DROP COLUMN last_sync_duration_ms,
    ADD COLUMN webhook_status VARCHAR(30) NOT NULL DEFAULT 'pending',
    ADD COLUMN last_webhook_received_at TIMESTAMPTZ,
    ADD COLUMN last_webhook_error_at TIMESTAMPTZ,
    ADD COLUMN last_webhook_error TEXT;

CREATE TABLE webhook_deliveries (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider             VARCHAR(30) NOT NULL,
    delivery_id          VARCHAR(255) NOT NULL,
    user_id              BIGINT,
    integration_provider VARCHAR(30),
    status               VARCHAR(20) NOT NULL,
    error_message        TEXT,
    headers              JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload              JSONB NOT NULL DEFAULT '{}'::jsonb,
    received_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at         TIMESTAMPTZ,
    CONSTRAINT webhook_deliveries_provider_delivery_unique UNIQUE (provider, delivery_id)
);

CREATE INDEX idx_webhook_deliveries_provider_received_at
    ON webhook_deliveries(provider, received_at DESC);

DELETE FROM github_repositories;
DELETE FROM user_integrations;
