-- Add timezone field to users table
-- This allows users to set their preferred timezone for display purposes
-- All times are still stored in UTC in the database

ALTER TABLE users 
ADD COLUMN timezone VARCHAR(50) DEFAULT 'UTC' NOT NULL;

-- Add comment to explain the field
COMMENT ON COLUMN users.timezone IS 'User timezone preference for display (e.g., America/New_York, Europe/London). All times stored in UTC.';

-- Create index for timezone queries (optional, but useful for analytics)
CREATE INDEX idx_users_timezone ON users(timezone);
