# 02 — Java write path → Qdrant (dual-write)

**Track:** A · Qdrant  ·  **Blocked by:** 01  ·  **Blocks:** 03
**Estimate:** ~4h

## Goal

Every chunk written to Postgres is also written to Qdrant, behind a flag, with deletes mirrored.
pgvector remains the read path — nothing user-facing changes yet.

## Why this is small

`KnowledgeIndexingService` funnels **all** writes through one private method:

- `KnowledgeIndexingService.java:231` — `private void upsertChunk(...)` → `chunkRepo.upsertChunk(...)`

All five public indexers (`indexGitHubReadme`, `indexObsidianDiff`, `indexResumeSections`,
`indexGoalsAndTasks`, `reindexAll`) route through it. One seam.

## Changes

- New `platform/vector/QdrantVectorStore.java`:
  - `upsert(userId, sourceType, sourceKey, chunkIndex, content, denseVector, metadata)`
  - `deleteByUser(userId)`, `deleteBySource(userId, sourceType)`,
    `deleteBySourceKey(userId, sourceType, sourceKey)`
  - Point ID = deterministic UUIDv5 over `user_id|source_type|source_key|chunk_index`, so upserts
    replace in place and match the existing SQL `ON CONFLICT` semantics.
- Wire into `KnowledgeIndexingService.upsertChunk` (the single seam) and mirror the delete calls
  that currently hit `KnowledgeChunkRepository.deleteBy*`.
- Feature flag `pulse.vector.dual-write=true` (default true in this issue).
- Batch upserts where the caller already batches (`indexResumeSections`, `indexGoalsAndTasks` both
  call `embeddingService.embedBatch`) — do not degrade them to per-point round trips.
- One-time backfill: a CLI/test entry point that reads existing `knowledge_chunks` rows and upserts
  them into Qdrant. Content and metadata come from Postgres; dense vectors are **re-embedded** via
  `EmbeddingService` (the stored pgvector column is not readable as a `float[]` through JPA without
  extra mapping, and re-embedding a few thousand chunks costs cents at $0.02/1M tokens).

## Non-goals

- Do not touch `HybridRetrievalService`. Reads stay on pgvector until issue 03.
- Do not drop anything. Issue 04 owns removal.

## Acceptance criteria

- [ ] All five indexing paths write to both stores
- [ ] All three delete paths remove from both stores
- [ ] Backfill run; Qdrant point count == `SELECT count(*) FROM knowledge_chunks`
- [ ] Re-running an indexer does not duplicate points (deterministic IDs verified)
- [ ] Qdrant failure does not fail the Postgres write — log and continue while dual-writing
- [ ] `mvn test` green; `PersonalServiceResumeScoreTest` still passes with its mock

## Verify

```bash
cd api && ./mvnw test
# then compare counts
psql -c "select count(*) from knowledge_chunks;"
# vs Qdrant collection info point count
```
