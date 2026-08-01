# 10 — Langfuse in Python + cross-service trace propagation + redaction

**Track:** C · Observability  ·  **Blocked by:** 07, 09  ·  **Blocks:** 11
**Estimate:** ~4h

## Goal

**One** Langfuse trace per chat request, spanning Java → Python → each graph node → Anthropic. This
is the payoff of running two services; without it they are two disconnected systems.

## Why this works with no custom glue

Langfuse Python SDK **v3 is OpenTelemetry-native**: it captures spans emitted by other OTel
instrumentation and nests them correctly under the active trace. So standard W3C `traceparent`
propagation over the HTTP hop is sufficient — the Python spans join the Java trace automatically.

## Changes

- `langfuse` in `agent/pyproject.toml`; config `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`,
  `LANGFUSE_HOST`. Landed as `agent/src/agent/observability.py`.
- ~~Java side (`AgentClient`, issue 08): inject the current `traceparent` header on the outbound
  call.~~ **Already true, no change needed.** `AgentClient` builds on the injected
  `WebClient.Builder`, whose client observations inject `traceparent`; issue 08 wrote it that way
  deliberately.
- Python side: extract `traceparent` from the incoming request and use it as the parent context.
  **Done by hand, not by `opentelemetry-instrumentation-fastapi`** — Langfuse's default export
  filter (`is_default_export_span`) only forwards its own spans, `gen_ai.*` spans and known LLM
  instrumentation scopes, so a FastAPI server span would be dropped before it ever reached
  Langfuse. The instrumentation would have bought nothing but the three lines in
  `continue_upstream_trace`, and the `POST /chat` span comes from `@observe` instead.
- Instrument the graph:
  - `CallbackHandler` passed to the LangGraph invocation — gives per-node spans for free
  - `@observe` on `/chat` and on the retrieval tool
- Span attributes worth having: `retrieval.top_k`, `retrieval.hit_count`, `retrieval.top_rrf_score`,
  `agent.iterations`, `agent.sources_queried`. Also landed: `retrieval.source_types` and
  `retrieval.sources` (source keys with scores).

`langchain` joins the dependency list. Nothing here imports it — `langfuse.langchain` does, purely
to branch on its major version, and raises `ModuleNotFoundError` without it.

## Redaction — hard requirement

Same bar as issue 09, and **more exposed**: the agent's prompts contain retrieved resume chunks by
construction.

- [x] Configure Langfuse masking so chunk content and resume text are truncated/redacted before
      export. **Stronger than asked:** `mask_otel_spans` deletes the Langfuse input/output
      attributes from *every* exported span and marks it `pulse.redacted=true`, so no prompt or
      completion body leaves the process at all. Truncating would have meant parsing serialised
      LangChain message JSON to find the resume inside it, and one miss leaks it; this matches the
      call issue 09 made on the Java side, and it makes "zero resume content" checkable rather
      than arguable. The cost is that prompts cannot be debugged from Langfuse — the structured
      attributes below exist to compensate.
- [x] Verify on a real trace, not by reading code — three live traces fetched back through
      `langfuse-cli` and diffed against the user's actual resume text (see Result)
- [x] Retrieval spans record scores and source keys — **not** chunk bodies

## Acceptance criteria

- [x] One chat request → **one** trace containing both Java and Python spans
- [x] Nesting is correct: `pulse.rag.chat` → `http post` → `POST /chat` → `plan` / `retrieve` /
      `reflect` / `answer` → Anthropic generation. The `http post` span in the middle is the
      WebClient client observation — it is the span whose id goes into `traceparent`, so the Python
      root parents to it rather than to `pulse.rag.chat` directly.
- [x] Token counts and cost visible for every generation in the trace
- [x] A two-round retrieval question shows **two** `retrieve` spans — the agentic loop is visible in
      the trace. This is the screenshot worth keeping.
- [x] Manual inspection of ≥3 traces shows zero resume content
- [x] `pytest` green — 20 tests, `ruff check` clean

## Verify

```bash
cd agent && uv run pytest && uv run ruff check

# End to end. Both services need Langfuse keys from the same project, or the two halves of the
# trace land in different projects.
docker compose up
curl -X POST localhost:8080/api/personal/chat -b "$SESSION" \
  -H "Content-Type: application/json" \
  -d '{"message":"Compare my GitHub projects against my roadmap and resume"}'

# The Python half alone, without a browser session — hand it the traceparent Java would have sent:
curl -X POST localhost:8001/chat -H "X-Internal-Token: dev" -H "Content-Type: application/json" \
  -H "traceparent: 00-$(openssl rand -hex 16)-$(openssl rand -hex 8)-01" \
  -d '{"userId":5,"message":"What did I write in my study notes about database indexing?"}'

# The Java→Python hop, without a browser session. Needs the agent already listening on :8001 and
# LANGFUSE_* in the environment; prints the trace id to look up.
cd api && ./mvnw test -Plangfuse -Dtest=AgentTracePropagationTest

export LANGFUSE_BASE_URL="$LANGFUSE_HOST"
npx langfuse-cli api observations list --trace-id <traceparent trace id> \
  --fields core,basic,io,model,usage,metadata --limit 60 --json
# expect: input/output null on every row, model + usageDetails + costDetails on the generations
```

`/api/personal/chat` sits behind Google OAuth, so there is no headless way to drive the browser
path. `AgentTracePropagationTest` covers the same hop instead: it wires the registry the way Boot's
observation auto-configuration does, applies Boot's own `ObservationWebClientCustomizer` to the
builder, and calls the **real** running agent inside a `pulse.rag.chat` observation. It asserts the
response is not the degraded one — otherwise an agent that was down would let it pass green with no
Python spans in the trace at all.

## Result

Three live traces, user 5 (3099-char resume on file), `claude-haiku-4-5` throughout. Two drove the
Python half alone with a hand-written `traceparent`; the third (`ce5d1e1aaee404fd23e8153bc63d52a3`,
21 observations) went through the real `AgentClient` and is the one that proves the hop:

```
SPAN        pulse.rag.chat        ROOT          ← Java
SPAN          http post                         ← Java, WebClient client observation
SPAN            POST /chat                      ← Python, continues the traceparent
CHAIN             LangGraph
CHAIN               plan     → GENERATION ChatAnthropic
CHAIN               retrieve                    (×2)
CHAIN               reflect  → GENERATION ChatAnthropic
CHAIN               answer   → GENERATION ChatAnthropic
RETRIEVER         search_knowledge              (×2)
```

| Check | Outcome |
|---|---|
| Observations per trace | 15 (single round) / 19 (two rounds) / 21 (cross-service, two rounds) |
| Trace continuation | one trace id end to end; `POST /chat` parents to the Java `http post` span |
| Nesting | `pulse.rag.chat` → `http post` → `POST /chat` → `LangGraph` → nodes → `ChatAnthropic` |
| Two-round question | 2 × `retrieve`, 2 × `search_knowledge`, `agent.iterations: 2` |
| `input` / `output` | `null` on all 55 observations across the three traces |
| Resume-token search across all three full JSON payloads | no content hits. Of 276 distinct ≥5-char resume tokens, the 19 that appear are all structural: `retrieval.sources` source keys, `internalModelId`, `telemetry.sdk.language`, `timeToFirstToken`, `retrieval.top_rrf_score`, and the type names in `agent.sources_queried` |
| Cost — plan / reflect / answer (two-round trace) | $0.001742 / $0.001748 / $0.003062 |
| Cost — answer with resume + 113 items + 2 READMEs inlined | $0.009039 |
| `user_id` | `5` on every Python observation, via `propagate_attributes`; blank on the two Java spans, which predate the user attribute |

**Source keys are not anonymous, by design.** `retrieval.sources` carries values like
`github_readme:NguyenVietMy/Outreachly#0@0.0331` — repo owner and name, which is the account handle.
That is the point of recording source keys at all (you cannot debug retrieval without knowing what
came back), and it is metadata rather than chunk content, but it is worth knowing that the trace is
not de-identified.

**The retriever span sits one level too high.** `search_knowledge` lands as a sibling of
`LangGraph` under `POST /chat`, not under the `retrieve` node that called it. LangGraph runs each
node in its own asyncio task, which copies the context *before* `CallbackHandler` attaches the
node's span, so `@observe` inside the node still sees `POST /chat` as current. Both spans and all
their attributes are present and the two rounds are distinguishable by `retrieval.top_rrf_score`;
fixing the depth would mean reaching into LangGraph's task creation, which is not worth it.

**The whole-record path is the expensive one.** The answer generation that inlined the resume, 113
goal/task/roadmap items and 2 READMEs cost 3× the one that answered from a single retrieved chunk
($0.009039 vs $0.003062; 5039 vs 2422 input tokens, and it ran to the 800-token output cap). That
is the planner's search-vs-inline choice showing up as money, and it is a measurement `PLAN.md` 2.4
can act on.
