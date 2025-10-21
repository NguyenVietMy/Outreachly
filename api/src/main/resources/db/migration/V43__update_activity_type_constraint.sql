-- Update activity_type constraint to use csv_import instead of import
-- since 'import' is a reserved keyword in Java

-- Drop the existing constraint
ALTER TABLE activity_feed DROP CONSTRAINT IF EXISTS activity_feed_activity_type_check;

-- Add the new constraint with csv_import
ALTER TABLE activity_feed ADD CONSTRAINT activity_feed_activity_type_check 
    CHECK (activity_type IN ('csv_import', 'campaign', 'domain', 'checkpoint'));
