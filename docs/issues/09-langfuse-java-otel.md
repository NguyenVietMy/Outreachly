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

- [x] Never attach full prompt or completion bodies as span attributes by default
- [ ] ~~If prompt capture is enabled for debugging, truncate to ≤200 chars and redact
      email addresses and phone numbers~~ — **not built.** No prompt-capture toggle exists, so
      there is nothing to truncate. Building the toggle purely to redact it would be speculative
      configurability. Revisit if a debugging need appears.
- [x] Verified by fetching traces back through `langfuse-cli` after a resume-scoring run with
      deliberately PII-laden input — not by reading the code

## Acceptance criteria

- [x] A chat and a resume-scoring request each produce a Langfuse trace
- [x] Model, input tokens, output tokens, and latency visible per generation
- [ ] ~~Grafana Tempo still receives traces (no regression)~~ — **not exercisable.** The premise is
      wrong: `management.otlp.tracing.export.enabled` defaults to `false` and
      `OBSERVABILITY_OTLP_ENDPOINT` is unset in both `.env.local.properties` and
      `.env.prod.properties`. No Grafana target exists to regress. Coexistence is instead
      *proven structurally* by `LangfuseConfigTest.primaryOtlpExporterSurvivesAlongsideLangfuse`.
- [x] Manual inspection of ≥3 traces shows zero resume content — 6 observations audited
- [x] `PLAN.md` Phase 2.2 checked off

## Verify

```bash
cd api
set -a && . ./.env.local.properties && set +a
./mvnw test -Plangfuse          # real Anthropic call -> real Langfuse export
export LANGFUSE_BASE_URL="$LANGFUSE_HOST"
npx langfuse-cli api observations list --fields core,basic,io,model,usage --limit 5
# expect: type=GENERATION, model set, usageDetails populated, input=null, output=null
```

## Result

| Check | Outcome |
|---|---|
| Observations audited | 6 |
| Observation type | `GENERATION` (auto-typed from `gen_ai.request.model`) |
| `input` / `output` | `null` on all 6 |
| PII string search across full JSON | no matches |
| Cost — digest (`claude-haiku-4-5`) | $0.000300 |
| Cost — resume score (`claude-sonnet-5`) | $0.038128 |

Langfuse computes cost itself from the token counts; no pricing table needed in Java. The
**~127× cost gap** between the two models is the measurement `PLAN.md` 2.4's routing
experiment exists to attack.
