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
  `LANGFUSE_HOST`.
- Java side (`AgentClient`, issue 08): inject the current `traceparent` header on the outbound call.
- Python side: extract `traceparent` from the incoming request and use it as the parent context
  (FastAPI OTel instrumentation does this; verify it rather than assuming).
- Instrument the graph:
  - `CallbackHandler` passed to the LangGraph invocation — gives per-node spans for free
  - `@observe` on `/chat` and on the retrieval tool
- Span attributes worth having: `retrieval.top_k`, `retrieval.hit_count`, `retrieval.top_rrf_score`,
  `agent.iterations`, `agent.sources_queried`.

## Redaction — hard requirement

Same bar as issue 09, and **more exposed**: the agent's prompts contain retrieved resume chunks by
construction.

- [ ] Configure Langfuse masking so chunk content and resume text are truncated/redacted before export
- [ ] Verify in the Langfuse UI on a real trace, not by reading code
- [ ] Retrieval spans record scores and source keys — **not** chunk bodies

## Acceptance criteria

- [ ] One chat request → **one** trace containing both Java and Python spans
- [ ] Nesting is correct: `pulse.rag.chat` → `POST /chat` → `plan` / `retrieve` / `reflect` / `answer`
      → Anthropic generation
- [ ] Token counts and cost visible for every generation in the trace
- [ ] A two-round retrieval question shows **two** `retrieve` spans — the agentic loop is visible in
      the trace. This is the screenshot worth keeping.
- [ ] Manual inspection of ≥3 traces shows zero resume content
- [ ] `pytest` green

## Verify

```bash
docker compose up
curl -X POST localhost:8080/api/personal/chat -b "$SESSION" \
  -d '{"message":"Compare my GitHub projects against my roadmap and resume"}'
# open the trace in Langfuse; confirm single trace, both services, two retrieve spans
```
