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

### Result — confirmed, no fallback needed

1. Cluster reports `{"version":"1.18.3"}` — well past 1.15.2.
2. The Java client exposes server-side inference directly: `VectorFactory.vector(Points.Document)`
   for writes and `QueryFactory.nearest(Points.Document)` for reads, both present in
   `io.qdrant:client:1.18.3`. `QdrantConnectivityTest` upserts a point whose `bm25` vector is a
   `Document` (text + `qdrant/bm25`, no client-side encoder) and retrieves it by a BM25 text query
   with a positive score. **No BM25 encoder is needed in Java.**

Collection state read back from the cluster after bootstrap:

```
dense:           {"dense": {"size": 1536, "distance": "Cosine"}}
sparse:          {"bm25": {"modifier": "idf"}}
payload_indexes: {"user_id": {"data_type": "integer"}, "source_type": {"data_type": "keyword"}}
```

## Acceptance criteria

- [x] Server-side BM25 confirmed available to the Java client, or fallback documented
- [x] Collection exists with both named vectors and both payload indexes
- [x] A throwaway integration test upserts one point with text + dense vector and retrieves it by
      both a dense query and a BM25 query
- [x] No credentials committed; `git status` clean of `.env.*`

## Verify

```bash
cd api && ./mvnw test -Pqdrant     # requires QDRANT_URL + QDRANT_API_KEY in the environment
```

The test is tagged `qdrant` and excluded from `mvn test`, matching how the `evals` and `langfuse`
network-dependent suites are already gated — so `-Dtest=QdrantConnectivityTest` alone would filter
it out.

## Notes

- `qdrant.enabled` defaults to **false**. Both `QdrantConfig` and `QdrantSchemaInitializer` are gated
  on it, so the app boots unchanged where QDRANT_URL is absent (CI, local runs). Issue 02 turns it on.
- `QDRANT_URL` is the REST URL Qdrant Cloud hands out and carries no port; the client is gRPC, so
  `QdrantConfig` substitutes 6334.
- Terraform declares the `QDRANT_URL` / `QDRANT_API_KEY` secrets (plus `ANTHROPIC_API_KEY` and the
  two `LANGFUSE_*` keys, which were missing). Values are carried in the gitignored
  `infra/environments/dev/secrets.json` and pushed by `spinup.ps1`, which runs `terraform apply`
  and then `put-secret-value` for every entry — so no manual AWS step is needed. All 22 entries in
  `secrets.json` now match the 22 secrets declared in `main.tf` one-for-one.
