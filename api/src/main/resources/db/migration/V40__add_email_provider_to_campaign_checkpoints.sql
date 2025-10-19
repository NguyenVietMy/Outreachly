-- Add email_provider column to campaign_checkpoints table
ALTER TABLE campaign_checkpoints 
ADD COLUMN email_provider TEXT NOT NULL DEFAULT 'GMAIL' 
CHECK (email_provider IN ('GMAIL', 'RESEND'));

-- Create index for better performance
CREATE INDEX IF NOT EXISTS idx_campaign_checkpoints_email_provider ON campaign_checkpoints(email_provider);
