ALTER TABLE companies
  ADD COLUMN IF NOT EXISTS headquarters_country TEXT,
  ADD COLUMN IF NOT EXISTS size TEXT,
  ADD COLUMN IF NOT EXISTS industry TEXT,
  ADD COLUMN IF NOT EXISTS company_type TEXT;
  
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'companies_company_type_check'
      AND conrelid = 'companies'::regclass
  ) THEN
    ALTER TABLE companies
      ADD CONSTRAINT companies_company_type_check
      CHECK (company_type IN (
        'Educational',
        'Educational Institution',
        'Government Agency',
        'Non Profit Partnership',
        'Privately Held',
        'Public Company',
        'Self Employed',
        'Self Owned',
        'Self Proprietorship'
      ));
  END IF;
END$$;