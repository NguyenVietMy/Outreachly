-- Replace day_of_week with scheduled_date for more flexible scheduling
-- This allows users to schedule checkpoints for specific dates instead of just days of the week

-- Add the new scheduled_date column
ALTER TABLE campaign_checkpoints 
ADD COLUMN scheduled_date DATE;

-- Convert existing day_of_week data to scheduled_date
-- We'll set all existing checkpoints to today's date as a default
-- Users can then update them to their desired dates
UPDATE campaign_checkpoints 
SET scheduled_date = CURRENT_DATE;

-- Make scheduled_date NOT NULL after populating it
ALTER TABLE campaign_checkpoints 
ALTER COLUMN scheduled_date SET NOT NULL;

-- Drop the old day_of_week column
ALTER TABLE campaign_checkpoints 
DROP COLUMN day_of_week;

-- Add an index for efficient querying by date
CREATE INDEX idx_campaign_checkpoints_scheduled_date ON campaign_checkpoints(scheduled_date);
