# 01 — Provision Qdrant Cloud + collection schema

**Track:** A · Qdrant  ·  **Blocked by:** —  ·  **Blocks:** 02
**Estimate:** ~1h

## Goal

A live Qdrant Cloud free-tier cluster with a `knowledge_chunks` collection configured for hybrid
(dense + server-side BM25 sparse) retrieval, reachable from both services.

## Changes

- Create a Qdrant Cloud free-tier cluster. Record URL + API key.
- Add secrets:
  - `.env.local.properties` (gitignored): `QDRANT_URL`, `QDRANT_API_KEY`
  - AWS Secrets Manager entries for prod, injected at ECS task startup
  - `application.properties`: `qdrant.url=${QDRANT_URL}`, `qdrant.api-key=${QDRANT_API_KEY}`
- Add `io.qdrant:client:1.18.3` to `api/pom.xml`.
- New `api/src/main/java/com/pulse/pulse/platform/vector/QdrantConfig.java` — a `QdrantClient` bean,
  following the existing `AnthropicConfig` pattern.
- A one-shot collection bootstrap (idempotent, runs on startup or as a documented CLI step):

```
collection: knowledge_chunks
  vectors:        dense -> size 1536, Cosine
  sparse_vectors: bm25  -> modifier IDF
  payload indexes: user_id (integer), source_type (keyword)
```

## Verification requirement — do this first

The PRD assumes **Qdrant generates BM25 sparse vectors server-side** (documented as landing in
1.15.2, "for all supported Qdrant clients"). Confirm against
<https://qdrant.tech/documentation/> that:

1. The Qdrant Cloud free tier runs ≥1.15.2.
2. The **Java** client can upsert a document for server-side BM25 inference (not Python/FastEmbed only).

If (2) is false, stop and re-plan issue 02 — the fallback is Java writing dense-only while a small
Python reindex path writes sparse, which changes the ownership table in PRD §5.

## Acceptance criteria

- [ ] Server-side BM25 confirmed available to the Java client, or fallback documented
- [ ] Collection exists with both named vectors and both payload indexes
- [ ] A throwaway integration test upserts one point with text + dense vector and retrieves it by
      both a dense query and a BM25 query
- [ ] No credentials committed; `git status` clean of `.env.*`

## Verify

```bash
cd api && ./mvnw test -Dtest=QdrantConnectivityTest
```
