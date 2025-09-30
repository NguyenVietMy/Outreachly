-- =====================================================
-- ROW LEVEL SECURITY (RLS) POLICIES MIGRATION
-- =====================================================
-- This migration adds comprehensive RLS policies for all tables
-- to ensure proper data isolation and security in a multi-tenant environment

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE lists ENABLE ROW LEVEL SECURITY;
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;
ALTER TABLE import_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE campaign_lead ENABLE ROW LEVEL SECURITY;
ALTER TABLE enrichment_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE enrichment_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_events ENABLE ROW LEVEL SECURITY;

-- =====================================================
-- HELPER FUNCTIONS
-- =====================================================

-- Function to get current user's organization ID
-- This assumes you have a way to get the current user's org_id
-- You might need to adjust this based on your authentication setup
CREATE OR REPLACE FUNCTION get_current_user_org_id()
RETURNS UUID AS $$
BEGIN
    -- This is a placeholder - you'll need to implement this based on your auth system
    -- For now, we'll use a session variable or JWT claim
    -- You might need to modify this to work with your OAuth setup
    RETURN COALESCE(
        current_setting('app.current_user_org_id', true)::UUID,
        NULL
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to check if user is admin
CREATE OR REPLACE FUNCTION is_admin()
RETURNS BOOLEAN AS $$
BEGIN
    -- Check if current user has admin role
    -- This is a placeholder - adjust based on your auth system
    RETURN COALESCE(
        current_setting('app.current_user_role', true) = 'ADMIN',
        false
    );
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- USERS TABLE POLICIES
-- =====================================================

-- Users can view their own profile
CREATE POLICY "Users can view own profile" ON users
    FOR SELECT USING (id = current_setting('app.current_user_id', true)::BIGINT);

-- Users can update their own profile
CREATE POLICY "Users can update own profile" ON users
    FOR UPDATE USING (id = current_setting('app.current_user_id', true)::BIGINT);

-- Admins can view all users
CREATE POLICY "Admins can view all users" ON users
    FOR SELECT USING (is_admin());

-- Admins can update all users
CREATE POLICY "Admins can update all users" ON users
    FOR UPDATE USING (is_admin());

-- =====================================================
-- ORGANIZATIONS TABLE POLICIES
-- =====================================================

-- Users can view organizations they belong to
-- Note: You'll need to implement a user_organizations table or similar
-- For now, this is a placeholder that allows all authenticated users
CREATE POLICY "Users can view their organizations" ON organizations
    FOR SELECT USING (true); -- Adjust based on your user-org relationship

-- Only admins can create organizations
CREATE POLICY "Only admins can create organizations" ON organizations
    FOR INSERT WITH CHECK (is_admin());

-- Only admins can update organizations
CREATE POLICY "Only admins can update organizations" ON organizations
    FOR UPDATE USING (is_admin());

-- Only admins can delete organizations
CREATE POLICY "Only admins can delete organizations" ON organizations
    FOR DELETE USING (is_admin());

-- =====================================================
-- LISTS TABLE POLICIES
-- =====================================================

-- Users can view lists from their organization
CREATE POLICY "Users can view org lists" ON lists
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create lists in their organization
CREATE POLICY "Users can create org lists" ON lists
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update lists in their organization
CREATE POLICY "Users can update org lists" ON lists
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete lists in their organization
CREATE POLICY "Users can delete org lists" ON lists
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- LEADS TABLE POLICIES
-- =====================================================

-- Users can view leads from their organization
CREATE POLICY "Users can view org leads" ON leads
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create leads in their organization
CREATE POLICY "Users can create org leads" ON leads
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update leads in their organization
CREATE POLICY "Users can update org leads" ON leads
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete leads in their organization
CREATE POLICY "Users can delete org leads" ON leads
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- IMPORT JOBS TABLE POLICIES
-- =====================================================

-- Users can view import jobs from their organization
CREATE POLICY "Users can view org import jobs" ON import_jobs
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create import jobs in their organization
CREATE POLICY "Users can create org import jobs" ON import_jobs
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update import jobs in their organization
CREATE POLICY "Users can update org import jobs" ON import_jobs
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete import jobs in their organization
CREATE POLICY "Users can delete org import jobs" ON import_jobs
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- TEMPLATES TABLE POLICIES
-- =====================================================

-- Users can view templates from their organization
CREATE POLICY "Users can view org templates" ON templates
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create templates in their organization
CREATE POLICY "Users can create org templates" ON templates
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update templates in their organization
CREATE POLICY "Users can update org templates" ON templates
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete templates in their organization
CREATE POLICY "Users can delete org templates" ON templates
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- CAMPAIGNS TABLE POLICIES
-- =====================================================

-- Users can view campaigns from their organization
CREATE POLICY "Users can view org campaigns" ON campaigns
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create campaigns in their organization
CREATE POLICY "Users can create org campaigns" ON campaigns
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update campaigns in their organization
CREATE POLICY "Users can update org campaigns" ON campaigns
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete campaigns in their organization
CREATE POLICY "Users can delete org campaigns" ON campaigns
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- CAMPAIGN_LEAD JOIN TABLE POLICIES
-- =====================================================

-- Users can view campaign-lead relationships for their organization
CREATE POLICY "Users can view org campaign leads" ON campaign_lead
    FOR SELECT USING (
        campaign_id IN (
            SELECT id FROM campaigns WHERE org_id = get_current_user_org_id()
        )
    );

-- Users can create campaign-lead relationships for their organization
CREATE POLICY "Users can create org campaign leads" ON campaign_lead
    FOR INSERT WITH CHECK (
        campaign_id IN (
            SELECT id FROM campaigns WHERE org_id = get_current_user_org_id()
        )
    );

-- Users can update campaign-lead relationships for their organization
CREATE POLICY "Users can update org campaign leads" ON campaign_lead
    FOR UPDATE USING (
        campaign_id IN (
            SELECT id FROM campaigns WHERE org_id = get_current_user_org_id()
        )
    );

-- Users can delete campaign-lead relationships for their organization
CREATE POLICY "Users can delete org campaign leads" ON campaign_lead
    FOR DELETE USING (
        campaign_id IN (
            SELECT id FROM campaigns WHERE org_id = get_current_user_org_id()
        )
    );

-- =====================================================
-- ENRICHMENT JOBS TABLE POLICIES
-- =====================================================

-- Users can view enrichment jobs from their organization
CREATE POLICY "Users can view org enrichment jobs" ON enrichment_jobs
    FOR SELECT USING (org_id = get_current_user_org_id());

-- Users can create enrichment jobs in their organization
CREATE POLICY "Users can create org enrichment jobs" ON enrichment_jobs
    FOR INSERT WITH CHECK (org_id = get_current_user_org_id());

-- Users can update enrichment jobs in their organization
CREATE POLICY "Users can update org enrichment jobs" ON enrichment_jobs
    FOR UPDATE USING (org_id = get_current_user_org_id());

-- Users can delete enrichment jobs in their organization
CREATE POLICY "Users can delete org enrichment jobs" ON enrichment_jobs
    FOR DELETE USING (org_id = get_current_user_org_id());

-- =====================================================
-- ENRICHMENT CACHE TABLE POLICIES
-- =====================================================

-- Enrichment cache is shared across organizations but should be read-only for users
-- Only system processes should write to this table
CREATE POLICY "Users can view enrichment cache" ON enrichment_cache
    FOR SELECT USING (true);

-- Only system processes can modify enrichment cache
-- This policy prevents user modifications
CREATE POLICY "System only enrichment cache modifications" ON enrichment_cache
    FOR ALL USING (false);

-- =====================================================
-- EMAIL EVENTS TABLE POLICIES
-- =====================================================

-- Users can view email events for leads in their organization
CREATE POLICY "Users can view org email events" ON email_events
    FOR SELECT USING (
        email_address IN (
            SELECT email FROM leads WHERE org_id = get_current_user_org_id()
        )
    );

-- Only system processes can create email events
CREATE POLICY "System only email events creation" ON email_events
    FOR INSERT WITH CHECK (false);

-- Only system processes can update email events
CREATE POLICY "System only email events updates" ON email_events
    FOR UPDATE USING (false);

-- Only system processes can delete email events
CREATE POLICY "System only email events deletion" ON email_events
    FOR DELETE USING (false);

-- =====================================================
-- ADDITIONAL SECURITY MEASURES
-- =====================================================

-- Create a function to set user context (to be called by your application)
CREATE OR REPLACE FUNCTION set_user_context(user_id BIGINT, org_id UUID, user_role TEXT)
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_user_id', user_id::TEXT, true);
    PERFORM set_config('app.current_user_org_id', org_id::TEXT, true);
    PERFORM set_config('app.current_user_role', user_role, true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create a function to clear user context
CREATE OR REPLACE FUNCTION clear_user_context()
RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_user_id', NULL, true);
    PERFORM set_config('app.current_user_org_id', NULL, true);
    PERFORM set_config('app.current_user_role', NULL, true);
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- =====================================================
-- GRANT PERMISSIONS
-- =====================================================

-- Grant necessary permissions to your application user
-- Adjust the username based on your setup
-- GRANT USAGE ON SCHEMA public TO your_app_user;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO your_app_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO your_app_user;

-- =====================================================
-- NOTES FOR IMPLEMENTATION
-- =====================================================

/*
IMPORTANT IMPLEMENTATION NOTES:

1. USER CONTEXT SETTING:
   - You need to call set_user_context() at the beginning of each request
   - This should be done in your authentication middleware
   - Example: SELECT set_user_context(123, 'uuid-here', 'USER');

2. AUTHENTICATION INTEGRATION:
   - The current policies assume you can get user_id, org_id, and role from session
   - You may need to modify the helper functions based on your OAuth setup
   - Consider using JWT claims or session variables

3. USER-ORGANIZATION RELATIONSHIP:
   - You may need to create a user_organizations table if users can belong to multiple orgs
   - Update the organization policies accordingly

4. ADMIN PERMISSIONS:
   - The admin policies are basic - you may want more granular admin controls
   - Consider different admin levels (org admin vs system admin)

5. TESTING:
   - Test each policy thoroughly with different user contexts
   - Verify that users can only access their organization's data
   - Ensure admins have appropriate access

6. PERFORMANCE:
   - The policies use subqueries which might impact performance
   - Consider adding indexes on frequently queried columns
   - Monitor query performance and optimize as needed

7. SECURITY:
   - Review all policies before deploying to production
   - Consider additional security measures like audit logging
   - Regularly review and update policies as your application evolves
*/