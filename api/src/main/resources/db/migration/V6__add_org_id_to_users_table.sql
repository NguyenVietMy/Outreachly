-- Add org_id column to users table
ALTER TABLE users ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE SET NULL;

-- Create index for better performance
CREATE INDEX IF NOT EXISTS idx_users_org_id ON users(org_id);
