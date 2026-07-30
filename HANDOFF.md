# HANDOFF — Agentic RAG Platform

Start here to pick up work on `PRD-agentic.md` / `docs/issues/`. Written to be readable cold, by a
fresh session or by you in three weeks.

**Status as of 2026-07-30:** scope agreed, PRD written, 11 issues broken out. **Zero code written.**

---

## 0. Do this before touching any issue

### Commit the in-flight Anthropic work

`main` is carrying a substantial uncommitted provider switch. Do not start an issue on top of it —
you will not be able to tell your diff from that one.

```
 D api/.../platform/ai/OpenAiService.java          (deleted)
 D api/.../platform/ai/OpenAiServiceTest.java      (deleted)
?? api/.../platform/ai/AnthropicService.java       (new)
?? api/.../platform/config/AnthropicConfig.java    (new)
?? api/.../platform/ai/AnthropicServiceTest.java   (new)
 M api/pom.xml, application.properties, ChatService, DashboardService,
   PersonalService, RoadmapService, DailySuggestionService + 4 tests
```

```bash
cd api && ./mvnw test          # confirm green first
git add -A && git commit -m "switch AI provider from OpenAI to Anthropic"
```

`PLAN.md`, `PRD-agentic.md`, `docs/issues/`, and this file are also untracked — commit them too.

### Accounts and keys to create

| What | Needed by | Notes |
|---|---|---|
| Qdrant Cloud free-tier cluster | issue 01 | 1 GB, free forever. Gives `QDRANT_URL` + `QDRANT_API_KEY`. |
| Langfuse Cloud project | issue 09 | Free tier. Gives public + secret key + host. |
| `ANTHROPIC_API_KEY` | already have | generation |
| `OPENAI_API_KEY` | already have | **embeddings only** — see §3 |

Local secrets go in `api/.env.local.properties` (gitignored) and later `agent/.env` (gitignore it).
Production secrets go in AWS Secrets Manager, injected at ECS task startup.

---

## 1. Per-issue workflow

```bash
git checkout main && git pull
git checkout -b issue-07-langgraph-stategraph
```

1. Read `docs/issues/NN-*.md` in full. Every issue has **Acceptance criteria** and a **Verify** block —
   those are the definition of done, not "it seems to work."
2. Check the issue's **Blocked by** row. Dependencies are strict; 03 gates 04 on a *measurement*.
3. Implement.
4. Run the Verify block. Paste real output into the issue's Result table where it has one
   (issues 03 and 11 both have numbers to fill in).
5. Tick the acceptance boxes in the issue file, flip the status in `docs/issues/README.md`.
6. Commit; PR to `main`.

### Starting a fresh Claude Code session on an issue

Paste something like:

> Read `HANDOFF.md`, `PRD-agentic.md`, and `docs/issues/07-langgraph-stategraph.md`.
> Implement issue 07. Follow the acceptance criteria exactly and run the Verify block before
> claiming it's done.

---

## 2. Recommended order

Issues **05**, **06**, **09** have no blockers. Do 05+06 first if you want momentum with zero
regression risk to working code.

```
01 → 05 → 06 → 02 → 03 → 09 → 07 → 08 → 10 → 04 → 11
```

Critical path is `01 → 02 → 03 → 07 → 08 → 10 → 11` (~34h of ~45h total).
**04 (drop pgvector) is deliberately second-to-last** — never delete the old path before the new one
serves real traffic.

---

## 3. Facts a cold start will otherwise get wrong

**`CLAUDE.md` is stale in two places.** It documents a flat `controller/service/entity/` layout —
the actual structure is modular DDD: `application/`, `domain/`, `infrastructure/persistence/`, `api/`
per module (`activity`, `identity`, `integrations`, `organizations`, `personal`, `platform`). It also
says migrations run V1–V54; they are at **V65**. Match the real structure, not the doc.

**Anthropic API, current shape.** Model IDs are complete as written — `claude-sonnet-5`,
`claude-haiku-4-5`, `claude-opus-5`. **Never append date suffixes.** `budget_tokens` returns 400 on
Sonnet 5 / Opus 5 — use `thinking: {"type": "adaptive"}`. Non-default `temperature`/`top_p`/`top_k`
are rejected with 400 on Sonnet 5. Assignments: `claude-sonnet-5` for resume scoring + eval judge,
`claude-haiku-4-5` for everything cheap including chat.

**The `openai` dependency in `agent/` is intentional.** Anthropic has no embeddings API, so
`text-embedding-3-small` stays — matching the existing Java `EmbeddingService`. Tooling (including
the `claude-api` skill) is primed to flag `import openai` as a provider mixup. It isn't. Documented
in issue 05.

**Qdrant generates BM25 sparse vectors server-side** (since 1.15.2, all clients). This is what makes
hybrid retrieval survive the migration without a BM25 encoder in Java. **Confirmed in issue 01** on
the real cluster (1.18.3) via the Java client: `VectorFactory.vector(Points.Document)` to write and
`QueryFactory.nearest(Points.Document)` to read. The fallback written down in issue 01 is not needed.

**Qdrant Cloud rejects filters on unindexed payload fields** rather than falling back to a scan
(`Index required but not found for "…"`). Every field the read or write path filters on needs a
payload index. Found in issue 02 via `deleteBySourceKey`; `QdrantSchemaInitializer` now creates the
indexes on every boot, so adding one is a one-line change that back-fills onto the live collection.

**Two retrieval constants must be carried over exactly.** `RRF_K = 60` (matches Qdrant's built-in
RRF) and `MIN_RRF_THRESHOLD = 0.008`. Qdrant has **no equivalent** of the threshold — it must be
applied client-side after fusion. Dropping it silently widens recall.

**The frontend contract is the compatibility boundary.** These four records in `ChatService` must
survive verbatim through issue 08:

```java
public record ChatMessage(String role, String content) {}
public record SourceCitation(String sourceType, String sourceKey, double score) {}
public record RoutingDecision(String sourceType, String decision, String reason) {}
public record ChatResponse(String message, List<SourceCitation> sources, List<RoutingDecision> routingDecisions) {}
```

The agent's `trajectory[]` maps 1:1 onto `RoutingDecision`, so the agent's reasoning renders in the
existing UI with **no changes to `app/`**. This is the single most valuable accident in the codebase.

**Two seams that make the work small.**
- `KnowledgeIndexingService.java:231` — one private `upsertChunk` funnels *all* five indexing paths.
  That is the entire Qdrant write integration point (issue 02).
- `HybridRetrievalService` has exactly **one** caller (`ChatService`), while `KnowledgeIndexingService`
  has three across two modules. That asymmetry is why retrieval moves to Python and indexing stays
  in Java.

**Pre-existing dead code — leave it.** `ChatService.java:17` imports `java.util.stream.Collectors`
unused, *today*, before any of our changes. `CLAUDE.md` says mention, don't delete. Noted here so it
isn't re-litigated in every issue.

**There is no `docker-compose.yml` yet.** Only `api/Dockerfile`. Issue 05 creates compose.

---

## 4. Ground rules (from `CLAUDE.md`)

- Surgical changes. Every changed line traces to the issue you're on.
- Remove only the orphans *your* change creates — not pre-existing dead code.
- Simplicity first. No speculative abstraction, no unrequested configurability.
- State assumptions before implementing; if two readings exist, ask.
- All schema changes go through Flyway. `ddl-auto=none`.
- **Redact resume text in traces.** Hard requirement in issues 09 and 10, verified by *looking at a
  real trace in the Langfuse UI*, not by reading the code.
- Keep `PLAN.md` checkboxes in sync — Phase 2.2 is satisfied by issue 09.

---

## 5. What "done" looks like

From `PRD-agentic.md` §9:

- [ ] `mvn test` green; Python suite green
- [ ] Retrieval parity ≥80% top-5 overlap, measured and recorded in issue 03
- [ ] `embedding vector(1536)` dropped; `pgvector` gone from `api/pom.xml`
- [ ] One Langfuse trace per chat spanning Java → Python → Anthropic, with token counts and
      **zero raw resume text**
- [ ] Chat p95 within +40% of the pre-migration baseline
- [ ] Both services healthy on ECS; prod chat served by the agent path
- [ ] `app/` unchanged and still rendering routing decisions

Then fill in the bracketed numbers in `PRD-agentic.md` §11. Unmeasured resume claims are the ones
that collapse under questioning.

---

## 6. Open questions carried forward

Not blockers, but decide them when you reach the issue:

- **Issue 04:** does `findByKeywordSearch` + the `search_vector` column get removed with the embedding
  column, or kept one release as a fallback? Recommendation in the issue: keep, then remove.
- **Issue 08:** once `ChatService` becomes an HTTP client, `HybridRetrievalService` has zero Java
  callers. Delete it, or keep it powering the issue-03 parity harness? Recommendation: keep until 04
  closes, then delete separately.
- **Issue 11:** Cloud Map service discovery vs. an internal ALB listener for api → agent.
  Recommendation: Cloud Map — the agent should have no public listener at all.
