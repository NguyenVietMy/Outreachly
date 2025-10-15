-- Add created_by column to campaigns table (nullable first)
ALTER TABLE campaigns ADD COLUMN created_by BIGINT;

-- Update existing campaigns to use the first user in each org as creator
UPDATE campaigns 
SET created_by = (
    SELECT u.id 
    FROM users u 
    WHERE u.org_id = campaigns.org_id 
    ORDER BY u.created_at ASC 
    LIMIT 1
)
WHERE created_by IS NULL;

-- Now make the column NOT NULL after updating existing data
ALTER TABLE campaigns ALTER COLUMN created_by SET NOT NULL;

-- Add foreign key constraint to users table
ALTER TABLE campaigns ADD CONSTRAINT fk_campaigns_created_by 
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT;

-- Add index for performance
CREATE INDEX idx_campaigns_created_by ON campaigns(created_by);
