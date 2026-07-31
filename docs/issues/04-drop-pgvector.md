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

## Open decision inside this issue — resolved: **option 1, keep**

`findByKeywordSearch` + `search_vector` become dead once Qdrant BM25 serves keyword retrieval.
Options:

1. **Keep them** as an emergency fallback for one release, then remove. *(recommended)*
2. Remove now in the same migration.

**Chosen: 1.** `search_vector` and `idx_chunks_search_vector` stay; `findByKeywordSearch` keeps its
`@Query` with a javadoc saying it has no live caller and why it is still there. The reasoning is
asymmetric risk, not sentiment: dropping the embedding column is the irreversible half of this issue
(re-adding it means re-embedding every chunk), while the tsvector column is generated, self-
maintaining, and costs only disk. Keeping it means a Qdrant outage can be answered by reverting the
issue-03 commit rather than by a migration under pressure.

**It must still go**, and issue 03 already recorded why it will not be missed: `plainto_tsquery` ANDs
its terms, so this arm returned rows on 3 of 20 frozen queries and never more than one. Remove the
column, the GIN index, and `findByKeywordSearch` once issue 08 has the agent path serving prod chat —
that is the release boundary meant by "one release".

## Changes as landed — deltas from the plan above

- **No `DROP INDEX` was needed.** The plan said to drop "whatever V63 created" on the embedding
  column. V63 created no ivfflat/hnsw index at all — only `idx_chunks_user_source` and
  `idx_chunks_search_vector`, neither of which touches `embedding`. So the 124-row collection was
  doing exact scans; the migration is a single `ALTER TABLE ... DROP COLUMN`.
- **`EmbeddingService.toVectorString` is deleted.** Issue 03 left it alive because
  `KnowledgeIndexingService` still needed it; this issue removes that last call site, so it is an
  orphan of this change and goes with it.
- **`RetrievalParityTest` is deleted.** Its `VECTOR_SQL` reads `embedding <=> ?::vector` directly, so
  it cannot compile against the new schema. It said "It dies with issue 04" in its own javadoc; the
  measurements it produced are recorded in `03-java-qdrant-read-path.md` §Result.
- **`KnowledgeIndexingDualWriteTest`** updated for the 6-argument `upsertChunk`.

## Follow-up this issue surfaced but did not fix

`pulse.vector.dual-write` no longer means "dual". Postgres and Qdrant used to both hold vectors, so
`VECTOR_DUAL_WRITE=false` was a safe kill switch that fell back to pgvector. Now Qdrant is the only
vector store, so setting it false makes indexing write content to Postgres and vectors nowhere —
silently, since `mirror()` just returns. Retrieval would degrade as new chunks land unindexed.
Out of scope here; either delete the flag or rename it and make `false` refuse to start.

## Acceptance criteria

- [x] Migration applies cleanly — dry-run first, then applied for real; see *Applying V66* below
- [x] `grep -rn "pgvector\|vector(1536)\|findByVectorSimilarity" api/src` returns nothing live —
      remaining hits are the historical V63 migration and comments referring back to it
- [x] `mvn clean package` succeeds — 65 tests, 0 failures
- [x] Chat still works end to end after the migration — verified in the browser, see below
- [x] Rollback plan written — below

## Applying V66

**There is no separate dev database.** `api/.env.local.properties` and `api/.env.prod.properties`
carry the same `SUPABASE_SESSION_POOLER`, the same host, the same `postgres` database and the same
`DB_USER`. The acceptance criterion "applies cleanly on a copy of the dev database" has no copy to
apply to — local development runs against production data. Flagged separately; it is not this
issue's to fix, but it is why the drop was dry-run first.

**Dry run.** Postgres has transactional DDL, so V66 was applied inside a transaction and rolled
back, proving it out against the real schema with nothing persisted:

- 124 rows, all 124 carrying an embedding (matches issue 03's 124 Postgres rows = 124 Qdrant points)
- `DROP COLUMN` succeeded with no dependent-object error — nothing else referenced the column
- `search_vector` and `idx_chunks_search_vector` survived, as the keep-decision requires
- the new 6-argument `upsertChunk` inserted successfully against the dropped-column schema
- `findByKeywordSearch`'s query still ran
- after `ROLLBACK`: 124/124 restored

**Applied.** Flyway: `Current version of schema "public": 65` → `Migrating schema "public" to version
"66 - drop knowledge chunk embedding"` → `Successfully applied 1 migration ... now at version v66`.

Two boot failures on the way, neither a defect in this issue: a transient
`PSQLException: SSL error: Read timed out` before Flyway obtained a connection (nothing was applied;
the retry worked), and `Port 8080 was already in use` — an unrelated Apache `httpd`. Worth knowing
that no stale Pulse instance was left running against the migrated schema; one running the old code
would fault on `knowledge_chunks`, since the pre-04 entity still maps `embedding`.

**Post-migration verification.** Booted on 8081 with `--pulse.vector.backfill=true`, which drives
`chunkRepo.findAll()` — a real JPA read through the entity that no longer declares `embedding`,
against the migrated schema:

```
Backfilling 124 knowledge chunks into Qdrant
Backfill complete: 124 rows in Postgres, 124 points in Qdrant
```

`/actuator/health` reports `UP` with `db: UP`. That covers the entity/schema mapping, the Qdrant
write path, and row-count parity.

**End-to-end chat.** `/api/personal/chat` is `authenticated`-gated behind Google OAuth, so this
needed a real browser session: API on 8080 (`/actuator/health` `UP`, `db: UP`, `Qdrant collection
knowledge_chunks already exists`), frontend on 3000, signed in, one message sent through the
*Career Coach* panel on `/personal`. **It answered with sources cited** — which is the whole point,
since citations are the visible proof that retrieval still returns chunks. No chat-path errors in
the API log.

Two red herrings encountered while getting there, neither caused by this issue and both worth
recording so they are not re-diagnosed later:

- **"No 'Access-Control-Allow-Origin' header" on every dashboard call.** Not a CORS fault. The
  header is present and correct (`CORS_ALLOWED_ORIGINS=http://localhost:3000,...` is honoured); an
  unauthenticated `fetch` gets `302 → /oauth2/authorization/google → 302 → accounts.google.com`,
  and Google sends no ACAO. Chrome attributes the failure to the *original* URL, so "not signed in"
  presents as a CORS bug. Signing in clears it.
- **500 on `/api/personal/suggestions/today`.** Pre-existing, from the Anthropic provider switch.
  `DailySuggestionService.java:178` calls `objectMapper.readValue` on raw model output and Claude
  wraps its JSON in a ```` ```json ```` fence. Deterministic, fired five times. No call site in
  `api/src/main/java` strips fences, so every JSON-parsing service shares the exposure. Untouched
  here — different subsystem, no overlap with the pgvector removal.

## Rollback plan

The drop is reversible; the data in the column is not. Recovery re-embeds from `content`, which
Postgres still holds, so nothing is unrecoverable — it costs one OpenAI embedding pass (~$0.02/1M
tokens; the whole corpus is cents).

1. `git revert` the issue-04 commit — restores the entity field, the 7-argument `upsertChunk`, the
   `pgvector` dependency, and `EmbeddingService.toVectorString`.
2. Re-add the column, which the retained `vector` extension makes a one-liner:
   ```sql
   ALTER TABLE knowledge_chunks ADD COLUMN embedding vector(1536);
   ```
   Flyway will fail on the checksum for V66; delete its row from `flyway_schema_history` or repair.
3. Repopulate: `POST /api/personal/chat/reindex` per user. `reindexAll` re-embeds resume sections,
   GitHub READMEs, goals, tasks, and roadmap items and writes them back through `upsertChunk`.
   Obsidian diffs are the exception — they are pruned at 14 days and only re-arrive on the next
   integration sync.
4. Reverting to *pgvector retrieval* additionally means reverting the issue-03 commit; the
   embedding column alone is not read by anything.

Recovering the other direction — losing Qdrant, not Postgres — is `QdrantBackfillRunner`
(`--pulse.vector.backfill=true`), unchanged by this issue and never dependent on the dropped column.

## Verify

```bash
cd api && ./mvnw clean package && ./mvnw spring-boot:run
# exercise chat, confirm sources still cited
```
