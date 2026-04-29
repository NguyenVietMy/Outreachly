-- Remove workspace-related tables: leads, import, enrichment, templates, companies.

DROP TABLE IF EXISTS org_leads CASCADE;
DROP TABLE IF EXISTS leads CASCADE;
DROP TABLE IF EXISTS lists CASCADE;
DROP TABLE IF EXISTS import_jobs CASCADE;
DROP TABLE IF EXISTS enrichment_jobs CASCADE;
DROP TABLE IF EXISTS enrichment_cache CASCADE;
DROP TABLE IF EXISTS templates CASCADE;
DROP TABLE IF EXISTS companies CASCADE;
DROP TABLE IF EXISTS organization_settings CASCADE;
DROP TABLE IF EXISTS activity_feed CASCADE;
