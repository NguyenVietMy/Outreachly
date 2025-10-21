-- Add domain verification fields to user_resend_config table
ALTER TABLE user_resend_config 
ADD COLUMN is_domain_verified BOOLEAN DEFAULT FALSE,
ADD COLUMN verification_code VARCHAR(10);

-- Add index for verification code lookups
CREATE INDEX idx_user_resend_config_verification_code ON user_resend_config(verification_code);
