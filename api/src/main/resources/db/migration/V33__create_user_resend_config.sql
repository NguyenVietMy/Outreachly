-- Create user_resend_config table for per-user Resend API configuration
-- Using correct BIGINT type for user_id to match users.id

CREATE TABLE user_resend_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    api_key VARCHAR(255) NOT NULL,
    from_email VARCHAR(255) NOT NULL,
    from_name VARCHAR(255),
    domain VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id)
);

-- Add indexes for performance
CREATE INDEX idx_user_resend_config_user_id ON user_resend_config(user_id);
CREATE INDEX idx_user_resend_config_active ON user_resend_config(is_active);

-- Add comment for documentation
COMMENT ON TABLE user_resend_config IS 'Stores per-user Resend API configuration allowing users to use their own Resend accounts';

