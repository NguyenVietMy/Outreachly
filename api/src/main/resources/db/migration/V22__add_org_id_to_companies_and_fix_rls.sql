-- =====================================================
-- ADD ORG_ID TO COMPANIES TABLE AND FIX RLS POLICIES
-- =====================================================
-- This migration adds org_id to companies table and fixes missing RLS policies

-- Add org_id column to companies table
ALTER TABLE companies ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE CASCADE;

-- Create index for better performance
CREATE INDEX IF NOT EXISTS idx_companies_org_id ON companies(org_id);

-- Enable RLS on companies table
ALTER TABLE companies ENABLE ROW LEVEL SECURITY;

-- RLS Policies for companies table
CREATE POLICY "Users can view org companies" ON companies
    FOR SELECT USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can create org companies" ON companies
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

CREATE POLICY "Users can update org companies" ON companies
    FOR UPDATE USING (org_id = get_current_user_org_id());

CREATE POLICY "Users can delete org companies" ON companies
    FOR DELETE USING (org_id = get_current_user_org_id());

-- Enable RLS on organization_settings table (if not already enabled)
ALTER TABLE organization_settings ENABLE ROW LEVEL SECURITY;

-- RLS Policies for organization_settings table (only create if they don't exist)
DO $$
BEGIN
    -- Check if policies exist before creating them
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'organization_settings' 
        AND policyname = 'Users can view their org settings'
    ) THEN
        CREATE POLICY "Users can view their org settings" ON organization_settings
            FOR SELECT USING (org_id = get_current_user_org_id());
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'organization_settings' 
        AND policyname = 'Users can update their org settings'
    ) THEN
        CREATE POLICY "Users can update their org settings" ON organization_settings
            FOR UPDATE USING (org_id = get_current_user_org_id());
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'organization_settings' 
        AND policyname = 'Users can insert their org settings'
    ) THEN
        CREATE POLICY "Users can insert their org settings" ON organization_settings
            FOR INSERT WITH CHECK (org_id = get_current_user_org_id());
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'organization_settings' 
        AND policyname = 'Only admins can delete org settings'
    ) THEN
        CREATE POLICY "Only admins can delete org settings" ON organization_settings
            FOR DELETE USING (is_admin());
    END IF;
END $$;
