# Observability Roadmap

Current state: traces and metrics flowing to Grafana Cloud, logs in CloudWatch, 4 CloudWatch alarms (unwired). Solid foundation, gaps in usability.

## Must-haves (7 → 9)

### 1. Wire CloudWatch alarms to SNS
The 4 alarms (high CPU, high memory, 5xx errors, unhealthy hosts) have no `alarm_actions` — they flip state silently. Add an SNS topic with email subscription in Terraform and pass it to `var.alarm_actions`.

**Where:** `infra/modules/ecs_api/main.tf` (lines 229-307), `infra/environments/dev/main.tf`
**Effort:** ~10 min

### 2. Build one Grafana dashboard
Four panels to start:
- Request rate
- Error rate
- p95 latency
- LLM call duration (`pulse.ai.call.duration` — renamed from `pulse.openai.call.duration` in the Anthropic switch)

All this data is already being collected — just needs a dashboard to look at it.

**Effort:** ~20 min in Grafana UI

### 3. Ship logs to Grafana Cloud (Loki)
Right now traces are in Grafana (Tempo) but logs are in CloudWatch — you have to context-switch between two UIs. Grafana Cloud accepts logs via OTLP at the same gateway. Add a Logback OTLP appender and point it at `https://otlp-gateway-prod-us-east-2.grafana.net/otlp/v1/logs` with the same auth token.

This unlocks: click a trace in Tempo → see the logs for that exact request.

**Where:** `api/pom.xml` (new dependency), `api/src/main/resources/application.properties` or logback config
**Effort:** ~1 hour

### 4. Add one Grafana alert
Something CloudWatch can't see — app-level. For example:
- OpenAI error rate > 20% over 5 minutes
- Resume parsing latency p95 > 10s

Set up a notification channel (email/Slack) in Grafana Cloud and create the alert rule.

**Effort:** ~15 min in Grafana UI

## Nice-to-haves (9 → 10)

### 5. Trace-to-log correlation
Once Loki is set up, ensure the trace ID is included in structured log output. Spring Boot 3.4+ with the Micrometer tracing bridge does this automatically for MDC-based loggers. Verify the `trace_id` field appears in Loki, then configure Grafana datasource correlation between Tempo and Loki.

### 6. Even out instrumentation coverage
Some services have counters + timers + traces (OpenAI, resume), others only have traces (dashboard, LeetCode, suggestions). When touching those services for bug fixes, add counters/timers where useful — don't make a separate pass for this.

### 7. SLO tracking
Grafana Cloud has a built-in SLO feature. Define something like "99% of API requests complete in < 2s" and let it track error budget burn rate. Pure polish.
