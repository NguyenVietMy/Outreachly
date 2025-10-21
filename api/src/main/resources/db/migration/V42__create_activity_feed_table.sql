-- Create activity_feed table for tracking all system activities
CREATE TABLE activity_feed (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL REFERENCES organizations(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    
    -- Activity identification
    activity_type VARCHAR(50) NOT NULL CHECK (activity_type IN ('import', 'campaign', 'domain', 'checkpoint')),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    
    -- Status
    status VARCHAR(50) CHECK (status IN ('success', 'error', 'warning', 'paused', 'processing')),
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_activity_feed_org_created ON activity_feed (org_id, created_at DESC);
CREATE INDEX idx_activity_feed_user_created ON activity_feed (user_id, created_at DESC);
CREATE INDEX idx_activity_feed_type ON activity_feed (activity_type);

-- Add RLS (Row Level Security) policy
ALTER TABLE activity_feed ENABLE ROW LEVEL SECURITY;

-- Create RLS policy for organization-based access
CREATE POLICY "Users can view org activity feed" ON activity_feed
    FOR SELECT USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can create org activity feed" ON activity_feed
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

CREATE POLICY "Users can update org activity feed" ON activity_feed
    FOR UPDATE USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can delete org activity feed" ON activity_feed
    FOR DELETE USING (org_id = get_current_user_org_id());
