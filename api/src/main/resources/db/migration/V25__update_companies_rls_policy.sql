-- =====================================================
-- UPDATE COMPANIES RLS POLICY TO ALLOW GLOBAL ACCESS
-- =====================================================
-- This migration updates the companies RLS policy to allow access to all companies

-- Drop existing policies
DROP POLICY IF EXISTS "Users can view org companies" ON companies;
DROP POLICY IF EXISTS "Users can create org companies" ON companies;
DROP POLICY IF EXISTS "Users can update org companies" ON companies;
DROP POLICY IF EXISTS "Users can delete org companies" ON companies;

-- Create new policies that allow access to ALL companies (view only)
CREATE POLICY "Users can view all companies" ON companies
    FOR SELECT USING (true);

-- No create/update/delete policies - users cannot modify companies
