-- Add missing fields for delivery tracking to email_events table
ALTER TABLE email_events ADD COLUMN campaign_id UUID REFERENCES campaigns(id);
ALTER TABLE email_events ADD COLUMN user_id BIGINT REFERENCES users(id);
ALTER TABLE email_events ADD COLUMN org_id UUID REFERENCES organizations(id);

-- Create indexes for better query performance
CREATE INDEX idx_email_events_campaign_id ON email_events(campaign_id);
CREATE INDEX idx_email_events_user_id ON email_events(user_id);
CREATE INDEX idx_email_events_org_id ON email_events(org_id);

-- Update RLS policy to include new fields
DROP POLICY "Users can view org email events" ON email_events;
CREATE POLICY "Users can view org email events" ON email_events
    FOR SELECT USING (
        org_id = get_current_user_org_id()
    );
