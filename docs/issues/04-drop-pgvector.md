# 04 — Drop pgvector column and dependency

**Track:** A · Qdrant  ·  **Blocked by:** 03  ·  **Blocks:** —
**Estimate:** ~2h

## Goal

Complete the migration. Postgres keeps chunk content and metadata; it stops storing vectors.

**Do not start until issue 03 records ≥80% parity.** This step is irreversible without a reindex.

## Changes

- Flyway `V66__drop_knowledge_chunk_embedding.sql`:
  - `DROP INDEX` on the embedding column (whatever V63 created — check it)
  - `ALTER TABLE knowledge_chunks DROP COLUMN embedding;`
  - Leave `search_vector` and its GIN index **in place** for now — see below.
- `KnowledgeChunk.java`: remove the embedding field.
- `KnowledgeChunkRepository.java`: remove `findByVectorSimilarity`; rewrite `upsertChunk` without the
  `embedding` column and `::vector` cast.
- `KnowledgeIndexingService`: stop passing `EmbeddingService.toVectorString(...)` to the repository
  (dense vectors now go only to Qdrant). Remove the resulting orphaned locals.
- `api/pom.xml`: remove the `pgvector` dependency.
- Do **not** drop the `vector` extension — it may be used elsewhere on the Supabase instance.

## Open decision inside this issue

`findByKeywordSearch` + `search_vector` become dead once Qdrant BM25 serves keyword retrieval.
Options:

1. **Keep them** as an emergency fallback for one release, then remove. *(recommended)*
2. Remove now in the same migration.

Pick one and note it here. Whichever is chosen, `search_vector` must eventually go or be justified —
leaving an unused trigger-maintained tsvector column is a cost with no reader.

## Acceptance criteria

- [ ] Migration applies cleanly on a copy of the dev database
- [ ] `grep -rn "pgvector\|vector(1536)\|findByVectorSimilarity" api/src` returns nothing live
- [ ] `mvn clean package` succeeds
- [ ] Chat still works end to end after the migration
- [ ] Rollback plan written: re-add column + rerun backfill from issue 02

## Verify

```bash
cd api && ./mvnw clean package && ./mvnw spring-boot:run
# exercise chat, confirm sources still cited
```
