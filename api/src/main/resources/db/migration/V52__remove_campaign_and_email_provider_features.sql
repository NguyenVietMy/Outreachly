-- Remove legacy campaign, provider-switching, and delivery-tracking schema.

ALTER TABLE organization_settings
    DROP COLUMN IF EXISTS email_provider,
    DROP COLUMN IF EXISTS email_provider_config;

DROP INDEX IF EXISTS idx_org_settings_email_provider;

DROP TABLE IF EXISTS campaign_checkpoint_leads CASCADE;
DROP TABLE IF EXISTS campaign_checkpoints CASCADE;
DROP TABLE IF EXISTS campaign_lead CASCADE;
DROP TABLE IF EXISTS campaigns CASCADE;
DROP TABLE IF EXISTS user_resend_config CASCADE;
DROP TABLE IF EXISTS short_links CASCADE;
DROP TABLE IF EXISTS email_events CASCADE;
