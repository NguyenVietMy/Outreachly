CREATE TABLE IF NOT EXISTS import_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  filename TEXT NOT NULL,
  status TEXT CHECK (status IN ('pending','processing','completed','failed')) DEFAULT 'pending',
  total_rows INTEGER DEFAULT 0,
  processed_rows INTEGER DEFAULT 0,
  error_rows INTEGER DEFAULT 0,
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_import_jobs_org_id ON import_jobs(org_id);
CREATE INDEX IF NOT EXISTS idx_import_jobs_user_id ON import_jobs(user_id);
CREATE INDEX IF NOT EXISTS idx_import_jobs_status ON import_jobs(status);
