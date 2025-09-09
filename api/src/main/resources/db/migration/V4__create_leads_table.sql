CREATE TABLE IF NOT EXISTS leads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  list_id UUID REFERENCES lists(id) ON DELETE SET NULL,
  first_name TEXT,
  last_name TEXT,
  company TEXT,
  domain TEXT,
  title TEXT,
  email TEXT,
  phone TEXT,
  linkedin_url TEXT,
  country TEXT,
  state TEXT,
  city TEXT,
  custom_text_field TEXT,
  source TEXT,
  verified_status TEXT CHECK (verified_status IN ('unknown','valid','risky','invalid')) DEFAULT 'unknown',
  enriched_json JSONB DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_leads_org_id ON leads(org_id);
CREATE INDEX IF NOT EXISTS idx_leads_email ON leads(email);
CREATE INDEX IF NOT EXISTS idx_leads_list_id ON leads(list_id);
