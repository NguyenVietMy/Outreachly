-- Add enrichment history column to track all enrichment attempts
ALTER TABLE leads ADD COLUMN enrichment_history JSONB DEFAULT '[]'::jsonb;

-- Create index for better performance on enrichment history queries
CREATE INDEX IF NOT EXISTS idx_leads_enrichment_history ON leads USING GIN (enrichment_history);
