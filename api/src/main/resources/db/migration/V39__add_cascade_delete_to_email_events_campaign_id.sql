-- Add CASCADE DELETE to email_events.campaign_id foreign key constraint
-- This allows campaigns to be deleted even when they have email events

-- First, drop the existing foreign key constraint
ALTER TABLE email_events DROP CONSTRAINT IF EXISTS email_events_campaign_id_fkey;

-- Add the foreign key constraint with CASCADE DELETE
ALTER TABLE email_events ADD CONSTRAINT email_events_campaign_id_fkey 
    FOREIGN KEY (campaign_id) REFERENCES campaigns(id) ON DELETE CASCADE;

