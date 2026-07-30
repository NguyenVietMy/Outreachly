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

- [x] All five indexing paths write to both stores
- [x] All three delete paths remove from both stores
- [x] Backfill run; Qdrant point count == `SELECT count(*) FROM knowledge_chunks` (124 == 124)
- [x] Re-running an indexer does not duplicate points (deterministic IDs verified)
- [x] Qdrant failure does not fail the Postgres write — log and continue while dual-writing
- [x] `mvn test` green; `PersonalServiceResumeScoreTest` still passes with its mock

## Verify

```bash
cd api && ./mvnw test                # 65 tests, green
cd api && ./mvnw test -Pqdrant       # real cluster; needs QDRANT_URL + QDRANT_API_KEY

# backfill (one-time), then compare counts
QDRANT_ENABLED=true ./mvnw spring-boot:run -Dspring-boot.run.arguments=--pulse.vector.backfill=true
psql -c "select count(*) from knowledge_chunks;"
```

The runner logs both numbers itself on the last line: `Backfill complete: N rows in Postgres,
M points in Qdrant`.

## Result

`QdrantVectorStoreWriteTest` (tagged `qdrant`) proves against the live cluster that a second upsert
of the same logical chunk replaces its point rather than adding one, and that each of the three
delete paths removes exactly its own points. `KnowledgeIndexingDualWriteTest` covers the seam
offline: both stores written, resume sections mirrored as **one** batch after the delete, and a
throwing Qdrant leaves the Postgres write intact.

**Schema change needed by the delete path.** Qdrant Cloud rejects a filter on an unindexed payload
field instead of falling back to a scan, so `deleteBySourceKey` failed with `Index required but not
found for "source_key"`. `QdrantSchemaInitializer` now creates the payload indexes on every boot
rather than only when it creates the collection — `source_key` (keyword) is added, and an existing
collection picks it up. Issue 01's recorded `payload_indexes` is therefore now
`user_id` (integer), `source_type` (keyword), `source_key` (keyword).

**Backfill.** Ran clean in two batches:

```
Backfilling 124 knowledge chunks into Qdrant
Backfilled 100/124 chunks
Backfilled 124/124 chunks
Backfill complete: 124 rows in Postgres, 124 points in Qdrant
```

The Qdrant number is a collection-wide count, so matching exactly also confirms nothing else is
in the collection. Point ids are deterministic, so re-running the backfill is harmless. Add
`--server.port=0` if the API is already running locally on 8080.
