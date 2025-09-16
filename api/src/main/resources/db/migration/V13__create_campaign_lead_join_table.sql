-- Create campaign_lead join table for many-to-many relationship
CREATE TABLE IF NOT EXISTS campaign_lead (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
  lead_id UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
  added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  added_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
  status TEXT CHECK (status IN ('active', 'paused', 'completed', 'removed')) DEFAULT 'active',
  notes TEXT,
  UNIQUE(campaign_id, lead_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_campaign_lead_campaign_id ON campaign_lead(campaign_id);
CREATE INDEX IF NOT EXISTS idx_campaign_lead_lead_id ON campaign_lead(lead_id);
CREATE INDEX IF NOT EXISTS idx_campaign_lead_status ON campaign_lead(status);
CREATE INDEX IF NOT EXISTS idx_campaign_lead_added_at ON campaign_lead(added_at);

-- Migrate existing campaign_id data to the new join table
INSERT INTO campaign_lead (campaign_id, lead_id, added_at, status)
SELECT campaign_id, id, created_at, 'active'
FROM leads 
WHERE campaign_id IS NOT NULL;

-- Remove the campaign_id column from leads table
ALTER TABLE leads DROP COLUMN IF EXISTS campaign_id;

-- Drop the old index
DROP INDEX IF EXISTS idx_leads_campaign_id;
