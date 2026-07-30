# 09 — Langfuse tracing from Java (OTel GenAI semconv)

**Track:** C · Observability  ·  **Blocked by:** —  ·  **Blocks:** 10
**Estimate:** ~4h

Satisfies **`PLAN.md` Phase 2.2**. Update that checkbox when this lands.

## Goal

Every Anthropic call from the JVM appears in Langfuse as a generation, with model, token counts, and
latency — and **no raw resume text**.

## Why OTel and not an SDK

There is no Langfuse Java SDK. Langfuse accepts OTLP directly, and the project already has the full
export path: `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`,
`micrometer-registry-otlp`, with traces already reaching Grafana Tempo. This is a second exporter
target plus correct span attributes, not new infrastructure.

## Changes

- Langfuse Cloud project; get public/secret keys → Basic auth header for the OTLP endpoint.
- Config in `application.properties` / `.env.local.properties`:
  `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`.
- Point an OTLP span exporter at `${LANGFUSE_HOST}/api/public/otel/v1/traces` **in addition to**
  Grafana. Do not replace Grafana — traces should go to both.
- In `AnthropicService`, set GenAI semantic-convention attributes on the observation:

| Attribute | Value |
|---|---|
| `gen_ai.system` | `anthropic` |
| `gen_ai.request.model` | e.g. `claude-haiku-4-5` |
| `gen_ai.operation.name` | `chat` |
| `gen_ai.usage.input_tokens` | from the API response `usage` |
| `gen_ai.usage.output_tokens` | from the API response `usage` |

- **This requires reading the `usage` field**, which `AnthropicService` currently discards. Read it
  here and attach it to the span. Emitting Micrometer token/cost *counters* is `PLAN.md` 2.1 and
  stays out of scope — but note in `PLAN.md` that 2.1 is now one small step away.

## Redaction — hard requirement

`PLAN.md` mandates it and resume text is the most sensitive data in the system.

- [ ] Never attach full prompt or completion bodies as span attributes by default
- [ ] If prompt capture is enabled for debugging, truncate to ≤200 chars and redact
      email addresses and phone numbers
- [ ] Verify by inspecting an actual trace in the Langfuse UI after a resume-scoring run —
      not by reading the code

## Acceptance criteria

- [ ] A chat and a resume-scoring request each produce a Langfuse trace
- [ ] Model, input tokens, output tokens, and latency visible per generation
- [ ] Grafana Tempo still receives traces (no regression)
- [ ] Manual inspection of ≥3 traces shows zero resume content
- [ ] `PLAN.md` Phase 2.2 checked off

## Verify

```bash
cd api && ./mvnw spring-boot:run
# trigger chat + a resume score, then inspect traces in the Langfuse UI
```
