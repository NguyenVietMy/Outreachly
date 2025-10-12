-- Migration to create short_links table for double masking
CREATE TABLE short_links (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(10) UNIQUE NOT NULL,
    original_url TEXT NOT NULL,
    message_id VARCHAR(255),
    user_id VARCHAR(255),
    campaign_id VARCHAR(255),
    org_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    click_count INTEGER DEFAULT 0
);

-- Index for fast lookups
CREATE INDEX idx_short_links_code ON short_links(short_code);
CREATE INDEX idx_short_links_message_id ON short_links(message_id);
CREATE INDEX idx_short_links_user_id ON short_links(user_id);