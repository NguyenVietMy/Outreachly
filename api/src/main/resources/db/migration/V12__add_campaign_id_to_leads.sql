-- Add campaign_id column to leads table
ALTER TABLE leads ADD COLUMN campaign_id UUID REFERENCES campaigns(id) ON DELETE SET NULL;

-- Create index for better performance on campaign_id queries
CREATE INDEX IF NOT EXISTS idx_leads_campaign_id ON leads(campaign_id);

-- Update existing leads to have campaign_id = null
UPDATE leads SET campaign_id = NULL WHERE campaign_id IS NULL;
