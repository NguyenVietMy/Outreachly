-- Add description and billing_email to organizations, and enforce name length (2-30)
ALTER TABLE organizations
  ADD COLUMN IF NOT EXISTS description TEXT,
  ADD COLUMN IF NOT EXISTS billing_email TEXT;

-- Enforce organization name length between 2 and 30 characters
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'chk_organizations_name_length'
  ) THEN
    ALTER TABLE organizations
      ADD CONSTRAINT chk_organizations_name_length
      CHECK (char_length(name) BETWEEN 2 AND 30);
  END IF;
END$$; 