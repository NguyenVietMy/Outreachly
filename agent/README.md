# `agent/` — Pulse agentic RAG service

FastAPI service on **:8001**. It owns retrieval and answer generation for chat; the Java API
(`api/`, :8080) keeps indexing and owns Postgres. See `PRD-agentic.md` §6 for the contracts.

Right now this is the scaffold from `docs/issues/05-scaffold-agent-service.md`: `/chat` returns a
hardcoded payload. The LangGraph `StateGraph` behind it lands in issue 07.

## Endpoints

| Route | Auth | Notes |
|---|---|---|
| `GET /health` | none | Open on purpose — container and ALB health checks cannot set headers. |
| `POST /chat` | `X-Internal-Token` | 401 without a matching token. |

```
POST /chat
  body: { "userId": 1, "message": "...", "history": [{"role":"user","content":"..."}] }
  200:  { "message": "...",
          "sources":    [{"sourceType":"resume_section","sourceKey":"experience","score":0.031}],
          "trajectory": [{"sourceType":"plan","decision":"...","reason":"..."}] }
```

The wire format is camelCase because `trajectory[]` maps 1:1 onto `ChatService.RoutingDecision` in
Java, which the frontend already renders. Do not rename these fields.

## Environment

Copy `.env.example` to `.env` (gitignored) and fill it in — the values match
`api/.env.local.properties`.

| Var | Purpose |
|---|---|
| `INTERNAL_TOKEN` | Shared secret with the Java API, both directions. **Required** — no default, the service refuses to boot without it. |
| `QDRANT_URL`, `QDRANT_API_KEY` | Qdrant Cloud. The REST URL, unlike the Java client which needs the gRPC port. |
| `ANTHROPIC_API_KEY` | Generation. |
| `OPENAI_API_KEY` | **Embeddings only** — `text-embedding-3-small`, matching the Java `EmbeddingService`. Anthropic has no embeddings API, so the `openai` dependency here is deliberate and not a provider mixup. |
| `PULSE_API_URL` | Java internal context API (issue 06). Defaults to `http://localhost:8080`; compose overrides it to `http://api:8080`. |
| `LANGFUSE_*` | Wired in issue 10. |

## Local run

```bash
cd agent
uv sync
uv run uvicorn agent.main:app --reload --port 8001

curl localhost:8001/health
curl -X POST localhost:8001/chat -H "X-Internal-Token: dev" -H "Content-Type: application/json" \
  -d '{"userId":1,"message":"hello"}'
```

On startup the service reads the `knowledge_chunks` collection from Qdrant and logs its status and
point count. A failure there is logged, not fatal — `/health` still answers so the container reports
the reason instead of crash-looping.

## Tests

```bash
uv run pytest
uv run ruff check
```

Tests never reach Qdrant: `TestClient` is not used as a context manager, so the lifespan hook that
makes that call does not run.

## Docker

From the repo root, `docker-compose.yml` brings up `api` (:8080) and `agent` (:8001). Both read
gitignored env files — `api/.env.local.properties` and `agent/.env` — which must exist first.

```bash
docker compose up --build
```

Qdrant is Qdrant Cloud, not a compose service.
