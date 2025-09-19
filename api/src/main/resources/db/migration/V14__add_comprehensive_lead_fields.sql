-- Add comprehensive lead fields for enhanced CSV import
-- This migration adds all the new fields to support comprehensive lead data

-- PERSONAL INFO FIELDS
ALTER TABLE leads ADD COLUMN position VARCHAR(255);
ALTER TABLE leads ADD COLUMN position_raw VARCHAR(255);
ALTER TABLE leads ADD COLUMN seniority VARCHAR(255);
ALTER TABLE leads ADD COLUMN department VARCHAR(255);


-- SOCIAL & PROFESSIONAL FIELDS
ALTER TABLE leads ADD COLUMN twitter VARCHAR(500);
ALTER TABLE leads ADD COLUMN confidence_score INTEGER;


