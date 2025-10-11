-- Fix user_id data type to handle large Google user IDs
-- Drop the existing foreign key constraint and column
ALTER TABLE email_events DROP CONSTRAINT IF EXISTS email_events_user_id_fkey;
ALTER TABLE email_events DROP COLUMN IF EXISTS user_id;

-- Add user_id as VARCHAR(255) to handle large Google user IDs
ALTER TABLE email_events ADD COLUMN user_id VARCHAR(255);

-- Recreate the index
CREATE INDEX IF NOT EXISTS idx_email_events_user_id ON email_events(user_id);
