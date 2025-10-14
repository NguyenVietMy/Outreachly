-- Create campaign checkpoints table
CREATE TABLE IF NOT EXISTS campaign_checkpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    day_of_week TEXT NOT NULL CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    time_of_day TIME NOT NULL DEFAULT '09:00:00',
    email_template_id UUID, -- Will be linked to templates later
    status TEXT NOT NULL CHECK (status IN ('pending', 'active', 'paused', 'completed')) DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create campaign checkpoint leads table (tracks individual lead sending status)
CREATE TABLE IF NOT EXISTS campaign_checkpoint_leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkpoint_id UUID NOT NULL REFERENCES campaign_checkpoints(id) ON DELETE CASCADE,
    lead_id UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('pending', 'sent', 'delivered', 'failed')) DEFAULT 'pending',
    scheduled_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Ensure unique checkpoint-lead combination
    UNIQUE(checkpoint_id, lead_id)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoints_campaign_id ON campaign_checkpoints(campaign_id);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoints_org_id ON campaign_checkpoints(org_id);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoints_day_of_week ON campaign_checkpoints(day_of_week);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoints_status ON campaign_checkpoints(status);

CREATE INDEX IF NOT EXISTS idx_campaign_checkpoint_leads_checkpoint_id ON campaign_checkpoint_leads(checkpoint_id);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoint_leads_lead_id ON campaign_checkpoint_leads(lead_id);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoint_leads_org_id ON campaign_checkpoint_leads(org_id);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoint_leads_status ON campaign_checkpoint_leads(status);
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoint_leads_scheduled_at ON campaign_checkpoint_leads(scheduled_at);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_campaign_checkpoints_updated_at 
    BEFORE UPDATE ON campaign_checkpoints 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_campaign_checkpoint_leads_updated_at 
    BEFORE UPDATE ON campaign_checkpoint_leads 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
