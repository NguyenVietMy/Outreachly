-- Create org_leads table (organization-visible mapping to global leads)
CREATE TABLE IF NOT EXISTS org_leads (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    lead_id uuid NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
    -- Denormalized lowercase email for fast org-scoped lookups (store as lowercase in app)
    email text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (org_id, lead_id),
    UNIQUE (org_id, email)
);

-- Helpful indexes
CREATE INDEX IF NOT EXISTS idx_org_leads_org_email ON org_leads (org_id, email);
CREATE INDEX IF NOT EXISTS idx_org_leads_org_lead ON org_leads (org_id, lead_id);

-- Update timestamp trigger
CREATE OR REPLACE FUNCTION set_updated_at_timestamp()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_org_leads_set_updated_at ON org_leads;
CREATE TRIGGER trg_org_leads_set_updated_at
BEFORE UPDATE ON org_leads
FOR EACH ROW
EXECUTE FUNCTION set_updated_at_timestamp();


