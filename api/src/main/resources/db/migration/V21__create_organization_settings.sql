-- =====================================================
-- ORGANIZATION SETTINGS TABLE MIGRATION
-- =====================================================
-- This migration creates the organization_settings table for storing
-- per-organization configuration including email provider preferences

-- Create organization_settings table
CREATE TABLE IF NOT EXISTS organization_settings (
    org_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    email_provider VARCHAR(50) NOT NULL DEFAULT 'aws-ses' CHECK (email_provider IN ('aws-ses', 'resend', 'mailgun', 'sendgrid', 'postmark', 'smtp')),
    email_provider_config JSONB DEFAULT '{}'::jsonb,
    notification_settings JSONB DEFAULT '{}'::jsonb,
    feature_flags JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_org_settings_org_id ON organization_settings(org_id);
CREATE INDEX IF NOT EXISTS idx_org_settings_email_provider ON organization_settings(email_provider);

-- Enable RLS on organization_settings
ALTER TABLE organization_settings ENABLE ROW LEVEL SECURITY;

-- RLS Policies for organization_settings
CREATE POLICY "Users can view their org settings" ON organization_settings
    FOR SELECT USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can update their org settings" ON organization_settings
    FOR UPDATE USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can insert their org settings" ON organization_settings
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Only admins can delete organization settings
CREATE POLICY "Only admins can delete org settings" ON organization_settings
    FOR DELETE USING (is_admin());

-- Create trigger to automatically update updated_at timestamp
CREATE TRIGGER update_organization_settings_updated_at 
    BEFORE UPDATE ON organization_settings 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Insert default settings for existing organizations
INSERT INTO organization_settings (org_id, email_provider, email_provider_config)
SELECT 
    id, 
    'resend', -- Default to resend as per your current application.properties
    '{"fromEmail": "noreply@outreach-ly.com", "fromName": "Outreachly"}'::jsonb
FROM organizations
WHERE id NOT IN (SELECT org_id FROM organization_settings);

-- Add comments for documentation
COMMENT ON TABLE organization_settings IS 'Stores per-organization configuration settings including email provider preferences';
COMMENT ON COLUMN organization_settings.org_id IS 'Reference to the organization this settings belongs to';
COMMENT ON COLUMN organization_settings.email_provider IS 'The email provider type to use for this organization';
COMMENT ON COLUMN organization_settings.email_provider_config IS 'JSON configuration for the selected email provider (API keys, SMTP settings, etc.)';
COMMENT ON COLUMN organization_settings.notification_settings IS 'Organization-wide notification preferences';
COMMENT ON COLUMN organization_settings.feature_flags IS 'Feature flags and toggles for this organization';