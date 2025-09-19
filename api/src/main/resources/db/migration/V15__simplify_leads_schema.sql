-- Simplify leads table to match Hunter API structure
-- Remove unnecessary fields and add email_type

-- Remove columns that are not needed for Hunter API
ALTER TABLE leads DROP COLUMN IF EXISTS company;
ALTER TABLE leads DROP COLUMN IF EXISTS title;
ALTER TABLE leads DROP COLUMN IF EXISTS country;
ALTER TABLE leads DROP COLUMN IF EXISTS state;
ALTER TABLE leads DROP COLUMN IF EXISTS city;

-- Add email_type field for Hunter API
ALTER TABLE leads ADD COLUMN email_type VARCHAR(50);

-- Add index for email_type
CREATE INDEX IF NOT EXISTS idx_leads_email_type ON leads (email_type);

-- Add check constraint for email_type
ALTER TABLE leads ADD CONSTRAINT leads_email_type_check 
CHECK (email_type IN ('personal', 'generic', 'role', 'catch_all', 'unknown'));
