# HANDOFF — Agentic RAG Platform

Start here to pick up work on `PRD-agentic.md` / `docs/issues/`. Written to be readable cold, by a
fresh session or by you in three weeks.

**Status as of 2026-07-31:** issues **01, 02, 03, 04, 05, 06, 09** landed. **V66 is applied** — the
schema is at v66, `knowledge_chunks.embedding` is gone, and an authenticated chat turn was verified
in the browser with sources still cited. The Qdrant track (A) is complete, `agent/` is a running
container, and the Java side now serves `/internal/context/*`. Next up: **07** — every one of its
blockers (03, 05, 06) is done.

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

Issue **06** has no blockers — greenfield, zero regression risk to working code.

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

**Two retrieval constants must be carried over exactly.** `RRF_K = 60` and
`MIN_RRF_THRESHOLD = 0.008`. Qdrant has **no equivalent** of the threshold — it must be applied
client-side after fusion. Dropping it silently widens recall. Issue 03 refinements: `Points.Rrf` in
client 1.18.3 has a settable `k`, so 60 is passed explicitly rather than trusting the server
default; and with `fetchK = 10` the threshold is currently a no-op (worst single-arm score
`1/70 ≈ 0.0143`), kept because it binds again if `fetchK` grows past ~65.

**The pgvector keyword arm was nearly dead — don't treat it as the reference.** `plainto_tsquery`
ANDs its terms, so `"Terraform ECS Fargate"` needs one chunk containing all three and matches
nothing. Measured in issue 03: it returned rows on **3 of 20** frozen queries, never more than one
row; Qdrant's disjunctive BM25 contributes on 19 of 20. The shipped "hybrid" search was effectively
dense-only. This is why issue 03's fused parity landed at 77%, not ≥80% — see that issue's
*Why the gate was not met*. It also made issue 04's "keep `search_vector` as a fallback" question
mostly moot: there is little there to fall back to. Kept anyway, since a generated column costs only
disk — but that is the reason it can be dropped without ceremony after issue 08.

**`QdrantVectorStore` is gated on `qdrant.enabled` alone** and takes an `ObjectMapper` (it rebuilds
the metadata JSON string `RetrievedChunk` used to get from jsonb). Reads must not depend on a
write-side flag, so `pulse.vector.dual-write` moved into `KnowledgeIndexingService`.

**Tagged tests need their profile.** Surefire's default `<excludedGroups>evals,langfuse,qdrant</excludedGroups>`
is *not* overridden by `-Dtest=`. Run `./mvnw test -Pqdrant -Dtest=QdrantConnectivityTest`.
(`RetrievalParityTest` was the other one; issue 04 deleted it with the pgvector column it queried.)

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

**There is no dev database — local *is* prod.** `api/.env.local.properties` and
`api/.env.prod.properties` carry the identical `SUPABASE_SESSION_POOLER`, host, `postgres` database
and `DB_USER`. Running the app locally, running a migration, or running the tagged tests all touch
production data. Found in issue 04, whose acceptance criteria asked for "a copy of the dev database".
Postgres has transactional DDL, so the way to prove a migration out is `BEGIN; …; ROLLBACK;` over
psql before letting Flyway commit it.

**Supabase `anon`/`authenticated` were revoked on 2026-07-31 — do not re-grant.** Supabase ships
every project with the PostgREST Data API live and `ALTER DEFAULT PRIVILEGES` granting full
`arwdDxtm` on new tables to `anon` and `authenticated`. Pulse does not use PostgREST — it connects
as `postgres` (which has `rolbypassrls`, so RLS would be inert anyway) — so every Flyway table was
carrying an unused public-facing grant, including `user_profiles` (resume text) and
`user_integrations` (OAuth tokens). Both roles now hold **zero** table grants, and the `postgres`-
owned default ACLs no longer name them, so tables created by future migrations stay closed.
`service_role` is untouched (91 grants) — its key is a genuine secret and nothing here uses it.
Residual, deliberately left: `PUBLIC` still holds `USAGE` on schema `public`, so
`has_schema_privilege('anon','public','USAGE')` is still `t`. That is naming rights with no table
privileges behind them; closing it means revoking from `PUBLIC`, which Supabase internals rely on.

**`QDRANT_ENABLED` is load-bearing — and ECS already sets it.** It defaults to `false`
(`application.properties:56`), and since issue 04 dropped the pgvector column there is no fallback:
`HybridRetrievalService` refuses to retrieve and chat fails outright. Deployment is already covered —
issues 01/02 wired `QDRANT_ENABLED = "true"` into `extra_environment` and `QDRANT_URL` /
`QDRANT_API_KEY` into `secret_arns` in `infra/environments/dev/main.tf`, all committed. **Do not
read `api/.env.prod.properties` as the prod contract**; it carries no `QDRANT_*` keys, but ECS does
not use it — Terraform and Secrets Manager define the task environment. `api/.env.local.properties`
sets `QDRANT_ENABLED=true` (added 2026-07-31) so a plain `./mvnw spring-boot:run` works.

**`docker-compose.yml` exists at the repo root** (issue 05) with two services, `api` (:8080) and
`agent` (:8001). Qdrant is Qdrant Cloud, not a compose service. Both services read **gitignored** env
files — `api/.env.local.properties` and `agent/.env` — so compose fails outright on a fresh clone
until those exist. `agent/.env.example` lists what the agent needs; the four shared values are
copied from the api file.

**The `agent/` service is Python 3.12 + uv.** `uv sync` in `agent/`, `uv run pytest`, `uv run ruff
check`. `uv.lock` is committed and the Dockerfile builds `--frozen`, so a dependency change means
re-running `uv lock` or the image build fails. Two things about it a cold start will get wrong:

- **`/health` is deliberately unauthenticated** — only `/chat` takes `X-Internal-Token`. Container
  `HEALTHCHECK` and the issue-11 ALB target group cannot send headers. Do not "fix" this by moving
  the dependency to global middleware.
- **`INTERNAL_TOKEN` has no default** and `Settings` fails at import without it, so the agent will
  not boot until it is set. This is intentional (fail closed on a shared secret), not a bug. The
  Java side of that secret landed in issue 06.

**The `/internal/**` API has its own security chain, and `INTERNAL_TOKEN` now exists on both sides.**
`InternalApiSecurityConfig` registers a second `SecurityFilterChain` at `@Order(1)` matching
`/internal/**` — stateless, anonymous disabled, `InternalTokenFilter` in front. It fails closed: a
blank `pulse.internal.token` rejects everything, so a deployment that forgets the secret serves 401s
rather than resume text. Three consequences a cold start should know:

- **The 401 comes from the filter, not from authorization**, so `/internal` never redirects to
  Google the way `/api` does. If you see a 302 there, you are talking to an old process.
- **`INTERNAL_TOKEN=dev` locally** in *both* `api/.env.local.properties` and `agent/.env`; prod uses
  a 256-bit value in `pulse/dev/INTERNAL_TOKEN`, wired into the api task's `secret_arns`.
- **The public ALB listener answers `/internal/*` with a fixed 404**
  (`aws_lb_listener_rule.block_internal`, priority 1) — issue 06 pulled that forward from issue 11.
  It only exists after a manual `./spinup.ps1`; CI deploys the image, never Terraform.

**Endpoint shapes are structured, not formatted.** `/internal/context/profile|items|github` return
raw fields (`InternalContextService`'s records); the `=== GOALS ===` blob formatting belongs to the
agent, in issue 07. `items` returns uncompleted tasks only, `github` returns only repos that have a
README, truncated to 2000 chars — the same filtering `ChatService` does inline today, so the agent
can reproduce the routing thresholds from list sizes and `resumeChars`.

**Nothing in `agent/` talks to Anthropic or the Java API yet.** `clients/embeddings.py`,
`clients/pulse_api.py` and `graph/` are scaffolding; `/chat` returns a hardcoded payload in the
PRD §6.1 shape. The three endpoints `pulse_api.py` calls are live as of issue 06 — which did not
have to touch that file; it already had the right paths and header. Issue 07 fills in the graph.

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
- [x] Retrieval parity measured and recorded in issue 03 — **77% fused / 99.5% dense arm**. The
      ≥80% target was not met and was deliberately not chased; the gap is Postgres's broken keyword
      arm, not the migration. Read issue 03's *Why the gate was not met* before citing this number.
- [x] `embedding vector(1536)` dropped; `pgvector` gone from `api/pom.xml` — issue 04. V66 applied
      2026-07-31; post-migration backfill read 124 Postgres rows = 124 Qdrant points.
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

- ~~**Issue 04:** does `findByKeywordSearch` + the `search_vector` column get removed with the
  embedding column, or kept one release as a fallback?~~ **Decided in issue 04: kept.** Remove both,
  plus the GIN index, once issue 08 has the agent path serving prod chat.
- **Issue 08:** once `ChatService` becomes an HTTP client, `HybridRetrievalService` has zero Java
  callers — the issue-03 parity harness that also used to hold it alive was deleted by issue 04.
  Delete it in 08.
- **Left by issue 04:** `pulse.vector.dual-write` is now misnamed and unsafe. With pgvector gone,
  `VECTOR_DUAL_WRITE=false` writes vectors nowhere instead of falling back. Delete the flag or make
  `false` refuse to start.
- ~~**Found while verifying issue 04:** every service parsed raw model output with a strict
  `ObjectMapper`, so a ```` ```json ```` fence or a trailing comma returned a 500.~~ **Fixed** —
  `platform/ai/ModelJson.java` strips the fence and tolerates trailing commas, and all five
  model-output call sites go through it. The injected `ObjectMapper` stays strict for HTTP bodies.
- **Issue 11:** Cloud Map service discovery vs. an internal ALB listener for api → agent.
  Recommendation: Cloud Map — the agent should have no public listener at all.
