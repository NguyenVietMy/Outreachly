-- Issue 04: Qdrant is now the only store for dense vectors (issues 02/03). Postgres keeps chunk
-- content and metadata.
--
-- V63 created no index on `embedding` (no ivfflat/hnsw), so there is nothing to drop first.
-- `search_vector` and idx_chunks_search_vector stay for one release as an emergency keyword
-- fallback; see docs/issues/04-drop-pgvector.md.
--
-- The `vector` extension is deliberately left installed — it may have other users on this instance.

ALTER TABLE knowledge_chunks DROP COLUMN embedding;
