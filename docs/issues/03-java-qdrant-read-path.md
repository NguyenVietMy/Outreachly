# 03 — Java read path → Qdrant hybrid RRF + parity harness

**Track:** A · Qdrant  ·  **Blocked by:** 02  ·  **Blocks:** 04, 07
**Estimate:** ~6h

## Goal

`HybridRetrievalService` retrieves from Qdrant using server-side RRF, and we have **measured proof**
it matches the pgvector implementation before anything is deleted.

## The parity harness comes first

Write the harness **before** changing the service. It needs both implementations alive
simultaneously, which is only true during this issue.

- Freeze ≥20 representative queries (career questions, project questions, keyword-heavy lookups,
  vague/semantic lookups) against a seeded user.
- For each: run old pgvector path and new Qdrant path, record top-5 `(sourceType, sourceKey)` sets.
- Report per-query overlap and mean overlap.

**Gate: mean top-5 overlap ≥80%.** Below that, do not proceed to issue 04 — investigate first.
The likeliest cause is BM25 vs Postgres `ts_rank_cd` disagreeing on stemming/stopwords.

## Changes

- `HybridRetrievalService.retrieve(...)` swaps its two repository calls for one Qdrant Query API call:

```
prefetch = [
    nearest(denseQueryVector, using="dense", limit=fetchK),
    nearest(bm25(queryText),  using="bm25",  limit=fetchK),
]
query  = FusionQuery(RRF)
filter = user_id == :userId AND source_type IN :sourceTypes
limit  = topK
```

- **Keep `MIN_RRF_THRESHOLD = 0.008` applied client-side after fusion.** Qdrant has no equivalent
  parameter. Dropping it silently widens recall and is a behaviour change, not a simplification.
- `RRF_K = 60` matches Qdrant's built-in RRF constant — no change needed, but assert it.
- `RetrievedChunk` record is unchanged. `vectorSimilarity` / `keywordScore` now come from the
  prefetch hit scores; keep populating them (the frontend shows them).
- `EmbeddingService` still produces the dense query vector. `EmbeddingService.toVectorString(...)`
  becomes unused by this service — it is still used by `KnowledgeIndexingService`, so leave it.

## Acceptance criteria

- [ ] Parity harness committed and runnable; mean top-5 overlap ≥80% recorded in this file
- [ ] `HybridRetrievalService` reads only from Qdrant
- [ ] `pulse.rag.retrieve` observation still emitted with the same tags
- [ ] Chat responses in local dev are qualitatively unchanged on the 20 frozen queries
- [ ] Retrieval p95 recorded before/after

## Verify

```bash
cd api && ./mvnw test -Dtest=RetrievalParityTest
```

## Result (fill in)

| Metric | pgvector | Qdrant |
|---|---|---|
| Mean top-5 overlap | 100% (baseline) | __% |
| Retrieval p95 | __ ms | __ ms |
