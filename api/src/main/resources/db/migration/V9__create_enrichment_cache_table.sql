-- Cache of enrichment results (forever or TTL)
CREATE TABLE IF NOT EXISTS enrichment_cache (
  key_hash TEXT PRIMARY KEY,
  provider TEXT NOT NULL CHECK (provider IN ('HUNTER')),
  json JSONB NOT NULL,
  confidence NUMERIC,
  fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


