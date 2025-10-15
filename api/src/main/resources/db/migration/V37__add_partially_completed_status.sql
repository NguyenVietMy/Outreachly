-- Add partially_completed status to campaign_checkpoints status constraint
ALTER TABLE campaign_checkpoints DROP CONSTRAINT IF EXISTS campaign_checkpoints_status_check;
ALTER TABLE campaign_checkpoints ADD CONSTRAINT campaign_checkpoints_status_check 
    CHECK (status IN ('pending', 'active', 'paused', 'completed', 'partially_completed'));

