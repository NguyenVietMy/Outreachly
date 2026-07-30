# PRD — Agentic RAG Platform (LangGraph + Qdrant + Langfuse)

**Status:** scope agreed 2026-07-30 · not started
**Owner:** Viet
**Related:** `PLAN.md` (Phase 2.2 is subsumed by Track C here), `OBSERVABILITY_TODO.md`

---

## 1. Problem

Pulse's AI chat is a good RAG system wearing a bad label.

- **Retrieval** is genuinely hybrid (dense pgvector + Postgres `ts_rank_cd` keyword, fused with
  Reciprocal Rank Fusion) — stronger than the typical "Pinecone + cosine top-k" project. But it
  runs on a Postgres extension, not a vector database, so it doesn't read as one.
- **"Smart routing"** in `ChatService` is four hard-coded integer thresholds
  (`RESUME_INLINE_THRESHOLD=6000`, `GITHUB_INLINE_THRESHOLD=3`, `OBSIDIAN_INLINE_THRESHOLD=5000`,
  `ITEMS_INLINE_THRESHOLD=20`). There is no planning, no reflection, no loop. It is not an agent.
- **LLM observability** is Micrometer counters and timers. There is no trace of *what the model saw*,
  *what it retrieved*, or *what it cost per request*. `AnthropicService` discards the API `usage`
  field entirely.

The goal is to close the gap between what the system does and what it can honestly be called, in a
way that is defensible in an interview — not to bolt on name-brand dependencies.

## 2. Goals

| # | Goal | Measured by |
|---|---|---|
| G1 | Real agentic control flow: the model decides what to retrieve and whether it has enough | A LangGraph `StateGraph` with a conditional edge that can loop `retrieve → reflect → retrieve` |
| G2 | A real vector database, with hybrid retrieval preserved | Qdrant Cloud serving dense+sparse RRF; retrieval parity vs. current pgvector on a fixture query set |
| G3 | End-to-end LLM tracing across both services | One Langfuse trace per chat request spanning Java → Python → Anthropic, with token counts |
| G4 | Deployed, not prototyped | Both services live on ECS; prod chat served by the agent path |

## 3. Non-goals

- Replacing the Anthropic provider or the model assignments (`claude-sonnet-5` scoring,
  `claude-haiku-4-5` cheap ops). Out of scope.
- `PLAN.md` Phase 1 (labeling harness, human labels, judge alignment, CI eval gate). Untouched.
- `PLAN.md` Phase 2.1 (token/cost Micrometer counters), 2.3, 2.4. **2.1 is adjacent** — Langfuse will
  surface tokens/cost in traces, which partly overlaps, but the Prometheus counters stay unbuilt.
- `OBSERVABILITY_TODO.md` items. Untouched.
- Moving indexing to Python. Indexing stays in Java (3 production callers across 2 modules).
- Streaming responses. Chat stays request/response.

## 4. Decisions (locked 2026-07-30)

| Decision | Choice | Rationale |
|---|---|---|
| Agent runtime | **New Python service `agent/`, FastAPI :8001** | LangGraph is Python/TS only. `langgraph4j` is a community port and a weak interview claim. |
| Vector DB | **Full migration to Qdrant Cloud (free tier)** | Managed, zero Terraform, 1GB is far beyond current volume. pgvector column is dropped. |
| Hybrid retrieval | **Qdrant Universal Query API: `prefetch` (dense + sparse) → `FusionQuery(RRF)`** | Server-side RRF is rank-based, exactly like the current Java implementation. |
| Sparse vectors | **BM25 generated server-side by Qdrant** (≥1.15.2, all clients) | Removes the need for a BM25 encoder in Java. This was the main migration risk; it is resolved. |
| Agent boundary | **Agent owns retrieval + reasoning.** Java exposes an internal context API. | The interesting decisions (what to fetch, is it enough) must live in the graph, or it isn't agentic. |
| Tracing | **Langfuse Cloud.** Java → OTLP w/ GenAI semconv; Python → Langfuse SDK v3 (OTel-native). | One vendor, one trace. v3 captures foreign OTel spans and nests them, so W3C `traceparent` propagation is all the glue needed. |
| Agent deployment | **Second ECS Fargate service now**, ALB path routing | ~$10–15/mo. "Deployed" is the difference between a claim and a demo. |
| Issue tracking | **Markdown in `docs/issues/`** | Version-controlled alongside the code. |
| Cutover | **Dual-write, then flip reads, then drop** | Chunk *content* lives in Postgres and is regenerable, so reindex is cheap — but staged cutover keeps chat working throughout. |

## 5. Architecture

```
                       ┌──────────────────────────────┐
   Next.js :3000 ─────▶│  Spring Boot  api/  :8080    │
                       │                              │
                       │  ChatController              │
                       │    └─ ChatService ───────────┼──HTTP──┐
                       │  KnowledgeIndexingService ───┼──gRPC─┐│
                       │  /internal/context/*  ◀──────┼───────┼┼──┐
                       └──────────────────────────────┘       ││  │
                                                              ││  │
                       ┌──────────────────────────────┐       ││  │
                       │  FastAPI  agent/  :8001      │◀──────┘│  │
                       │                              │        │  │
                       │  POST /chat                  │────────┼──┘
                       │    └─ LangGraph StateGraph   │        │
                       │         plan                 │        │
                       │          ↓                   │        │
                       │       retrieve ──gRPC────────┼────────┤
                       │          ↓        ▲          │        │
                       │       reflect ────┘          │        │
                       │          ↓  (sufficient)     │        │
                       │        answer ──▶ Anthropic  │        │
                       └──────────────────────────────┘        │
                                                               ▼
                                                    ┌─────────────────────┐
                                                    │ Qdrant Cloud        │
                                                    │ knowledge_chunks    │
                                                    │  dense  (1536, cos) │
                                                    │  bm25   (sparse,IDF)│
                                                    └─────────────────────┘

        both services ──OTLP (shared traceparent)──▶ Langfuse Cloud
```

**Data ownership after the migration**

| Data | Home | Written by | Read by |
|---|---|---|---|
| Chunk content, metadata, source keys | Postgres `knowledge_chunks` | Java `KnowledgeIndexingService` | Java (index status), Qdrant payload mirror |
| Dense + sparse vectors | Qdrant `knowledge_chunks` collection | Java (dense from OpenAI; sparse server-side) | Python agent |
| Profile / goals / tasks / roadmap / GitHub | Postgres | Java | Python agent via `/internal/context/*` |

**Why Java keeps indexing:** `KnowledgeIndexingService` has three production callers
(`GitHubProjectSyncService`, `ChatController`, `PersonalService`) plus a Mockito mock in
`PersonalServiceResumeScoreTest`. Moving it would ripple across two modules for no benefit.
`HybridRetrievalService`, by contrast, has exactly **one** caller — `ChatService` — which is the very
class being rewired. Retrieval moves to Python at zero incidental cost.

## 6. Contracts

### 6.1 Agent HTTP API (`agent/` :8001)

```
POST /chat
  headers: X-Internal-Token, traceparent
  body: { "userId": 1, "message": "...", "history": [{"role":"user","content":"..."}] }
  200:  { "message": "...",
          "sources":    [{"sourceType":"resume_section","sourceKey":"experience","score":0.031}],
          "trajectory": [{"sourceType":"plan","decision":"retrieve","reason":"..."}] }

GET /health  -> {"status":"ok"}
```

### 6.2 Java internal context API (`api/` :8080)

```
GET /internal/context/profile?userId=      -> profile markdown, targetRole, gradYear,
                                              axisScores, leetcodeStats, resumeChars
GET /internal/context/items?userId=        -> goals[], uncompleted tasks[], roadmap[]
GET /internal/context/github?userId=       -> repos[] (name, description, language, readme)
  auth: X-Internal-Token (shared secret, AWS Secrets Manager)
```

### 6.3 Frontend contract — **unchanged**

`ChatService`'s public records are the compatibility boundary and must survive verbatim:

```java
public record ChatMessage(String role, String content) {}
public record SourceCitation(String sourceType, String sourceKey, double score) {}
public record RoutingDecision(String sourceType, String decision, String reason) {}
public record ChatResponse(String message, List<SourceCitation> sources, List<RoutingDecision> routingDecisions) {}
```

The agent's `trajectory[]` maps 1:1 onto `RoutingDecision`. The frontend already renders these, so
the agent's reasoning steps appear in the existing UI with **no frontend changes**. This is the
single most valuable structural accident in the current codebase — do not break it.

### 6.4 Qdrant collection

```
collection: knowledge_chunks
  vectors:
    dense:  size=1536, distance=Cosine        # OpenAI text-embedding-3-small
  sparse_vectors:
    bm25:   modifier=IDF                       # generated server-side by Qdrant
  payload:
    user_id (int, indexed)      source_type (keyword, indexed)
    source_key (keyword)        chunk_index (int)
    content (text)              metadata (json)
  point id: deterministic UUIDv5 of (user_id, source_type, source_key, chunk_index)
```

Deterministic point IDs preserve the existing `ON CONFLICT (user_id, source_type, source_key,
chunk_index) DO UPDATE` upsert semantics without a lookup round-trip.

## 7. Retrieval parity requirement

The current fusion must be reproduced, not approximated:

- Current: two queries at `fetchK = topK * 2`, ranks fused as `1/(60 + rank)`, filtered at
  `rrfScore >= 0.008`, sorted desc, limited to `topK`.
- Qdrant: `prefetch=[dense(limit=fetchK), bm25(limit=fetchK)]`, `query=FusionQuery(RRF)`,
  `limit=topK`. Qdrant's RRF uses the same `1/(k + rank)` form with `k=60`.
- The `MIN_RRF_THRESHOLD = 0.008` floor has no Qdrant equivalent and must be applied client-side
  after fusion. **It is not optional** — it is what suppresses single-list weak matches
  (`1/(60+1) = 0.0164` for a rank-1 single-list hit vs. `0.008` floor ≈ rank 65 cutoff).

**Acceptance:** on a frozen set of ≥20 real queries, top-5 result sets from Qdrant and pgvector must
overlap ≥80%, and any disagreement must be explainable. Measured in issue 03.

## 8. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Server-side BM25 behaves differently from Postgres `ts_rank_cd` (stemming, stopwords) | **High** — this is the likeliest source of parity failure | Issue 03 measures parity before issue 04 drops anything. If parity fails, tune analyzer config or reconsider dropping the Postgres keyword path. |
| Extra network hop (Java → Python → Qdrant) adds chat latency | Medium | Measure p95 before/after. Agent loop capped at 2 retrieval rounds. |
| Second ECS service doubles deploy surface and cost | Medium | Accepted. Health-check gated rollout; Java falls back to a plain non-agentic answer if the agent is unreachable. |
| Resume text leaking into Langfuse traces | **High** (privacy) | Redaction is a hard requirement in issues 09/10, not a follow-up. |
| Qdrant free tier limits (1GB) | Low | Current volume is a few thousand chunks. Alert if >50% used. |
| Agent loop runs away (cost) | Medium | Hard cap: max 2 retrieval iterations, max 3 total LLM calls per request. |

## 9. Success criteria

- [ ] `mvn test` green; new Python test suite green
- [ ] Retrieval parity ≥80% top-5 overlap (§7)
- [ ] `embedding vector(1536)` column dropped; `pgvector` dependency removed from `api/pom.xml`
- [ ] One Langfuse trace per chat request contains Java span → Python span → Anthropic generation,
      with input/output token counts, and **zero raw resume text**
- [ ] Chat p95 latency within +40% of the pre-migration baseline
- [ ] Both services healthy on ECS; prod chat served by the agent path
- [ ] Frontend unchanged and still rendering routing decisions

## 10. Issue breakdown

Tracked in `docs/issues/`. Dependencies are strict — do not start an issue before its blockers land.

| # | Issue | Track | Blocked by |
|---|---|---|---|
| 01 | Provision Qdrant Cloud + collection schema | A · Qdrant | — |
| 02 | Java write path → Qdrant (dual-write) | A · Qdrant | 01 |
| 03 | Java read path → Qdrant hybrid RRF + parity harness | A · Qdrant | 02 |
| 04 | Drop pgvector column and dependency | A · Qdrant | 03 |
| 05 | Scaffold `agent/` FastAPI service + docker-compose | B · Agent | — |
| 06 | Java internal context API + shared-secret auth | B · Agent | — |
| 07 | LangGraph `StateGraph` (plan → retrieve → reflect → answer) | B · Agent | 03, 05, 06 |
| 08 | `ChatService` → HTTP client, contract preserved | B · Agent | 07 |
| 09 | Langfuse tracing from Java (OTel GenAI semconv) | C · Obs | — |
| 10 | Langfuse in Python + trace propagation + redaction | C · Obs | 07, 09 |
| 11 | Deploy `agent/` to ECS behind the ALB | D · Deploy | 08, 10 |

Critical path: **01 → 02 → 03 → 07 → 08 → 10 → 11**. Issues 05, 06, and 09 are independent and can
land early to shorten it.

## 11. Resume bullets this unlocks

Fill in the bracketed numbers only after measuring — unmeasured claims are the ones that collapse
under questioning.

> - Built an agentic RAG service (Python/FastAPI/**LangGraph**) whose `StateGraph` plans retrieval,
>   reflects on sufficiency, and loops before answering — replacing a hard-coded threshold router;
>   deployed on ECS Fargate alongside a Spring Boot API.
> - Migrated a hybrid retrieval pipeline from pgvector to **Qdrant**, preserving dense + BM25 sparse
>   Reciprocal Rank Fusion via the Universal Query API at [N]% top-5 parity.
> - Instrumented both services with **Langfuse** + OpenTelemetry, propagating W3C trace context
>   across the JVM/Python boundary for single-trace visibility into retrieval, token usage, and
>   per-request cost.
