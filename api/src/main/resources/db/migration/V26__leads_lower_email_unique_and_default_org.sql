-- Enforce case-insensitive uniqueness on leads.email
CREATE UNIQUE INDEX IF NOT EXISTS idx_leads_lower_email_unique
ON leads ((LOWER(email)))
WHERE email IS NOT NULL;

-- Set default org_id for global/secret leads
ALTER TABLE leads
    ALTER COLUMN org_id SET DEFAULT 'b8470f71-e5c8-4974-b6af-3d7af17aa55c'::uuid;

-- Normalize existing org_id values to the default
UPDATE leads
SET org_id = 'b8470f71-e5c8-4974-b6af-3d7af17aa55c'::uuid
WHERE org_id IS DISTINCT FROM 'b8470f71-e5c8-4974-b6af-3d7af17aa55c'::uuid;

-- Prevent updates to org_id (immutable once set)
CREATE OR REPLACE FUNCTION prevent_leads_org_id_update()
RETURNS trigger AS $$
BEGIN
    IF NEW.org_id <> OLD.org_id THEN
        RAISE EXCEPTION 'org_id is immutable for leads';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_leads_org_id_update ON leads;
CREATE TRIGGER trg_prevent_leads_org_id_update
BEFORE UPDATE OF org_id ON leads
FOR EACH ROW
EXECUTE FUNCTION prevent_leads_org_id_update();


