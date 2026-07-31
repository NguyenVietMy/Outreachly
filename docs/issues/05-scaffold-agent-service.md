# 05 — Scaffold `agent/` FastAPI service + docker-compose

**Track:** B · Agent  ·  **Blocked by:** —  ·  **Blocks:** 07
**Estimate:** ~4h

## Goal

A runnable, tested, containerised Python service skeleton. No LangGraph logic yet — that is issue 07.
This is independent of the whole Qdrant track and can land first.

## Structure

```
agent/
├── pyproject.toml          # uv-managed
├── Dockerfile
├── README.md
├── .env.example
├── src/agent/
│   ├── main.py             # FastAPI app, /health, /chat stub
│   ├── config.py           # pydantic-settings
│   ├── auth.py             # X-Internal-Token dependency
│   ├── clients/
│   │   ├── qdrant.py       # AsyncQdrantClient
│   │   ├── pulse_api.py    # httpx client for /internal/context/*
│   │   └── embeddings.py   # OpenAI text-embedding-3-small
│   └── graph/              # (empty until 07)
└── tests/
```

Root `docker-compose.yml` (new — none exists today):

```yaml
services:
  api:    # existing api/Dockerfile, :8080
  agent:  # agent/Dockerfile,        :8001
```

Qdrant is Qdrant Cloud (issue 01), not a compose service.

## Dependencies

`fastapi`, `uvicorn`, `httpx`, `pydantic-settings`, `qdrant-client`, `openai`, `langgraph`,
`langchain-anthropic`, `langfuse`; dev: `pytest`, `pytest-asyncio`, `ruff`.

**Note on the `openai` dependency:** it is there **only** for `text-embedding-3-small` — Anthropic has
no embeddings API, and this matches the existing Java `EmbeddingService`. All chat/generation goes
through `langchain-anthropic`. Anyone (or any tool) flagging the OpenAI import as a provider mixup
should read this paragraph; it is intentional and matches `PLAN.md`.

## Config

| Var | Purpose |
|---|---|
| `QDRANT_URL`, `QDRANT_API_KEY` | vector store |
| `ANTHROPIC_API_KEY` | generation |
| `OPENAI_API_KEY` | embeddings only |
| `PULSE_API_URL` | Java internal context API |
| `INTERNAL_TOKEN` | shared secret, both directions |
| `LANGFUSE_*` | wired in issue 10 |

## Acceptance criteria

- [x] `GET /health` returns 200
- [x] `POST /chat` returns a hardcoded well-formed `ChatResponse`-shaped payload (contract PRD §6.1)
- [x] Requests without a valid `X-Internal-Token` return 401
- [x] `docker compose up` starts api + agent; agent reaches Qdrant and logs collection info
- [x] `pytest` green; `ruff check` clean
- [x] `agent/README.md` covers local run, env vars, and test commands

## Verify

```bash
cd agent && uv run pytest && uv run ruff check
docker compose up --build
curl -H "X-Internal-Token: dev" localhost:8001/health
```

## Result

```
5 passed  ·  ruff: All checks passed!

agent-1 | INFO:agent.clients.qdrant:Qdrant collection knowledge_chunks: status=green points=124 vectors=136

GET  /health                        -> 200 {"status":"ok"}
POST /chat  (no token)              -> 401
POST /chat  (X-Internal-Token: bad) -> 401
POST /chat  (X-Internal-Token: dev) -> 200
  {"message":"Agent scaffold received: what did I ship?",
   "sources":[{"sourceType":"resume_section","sourceKey":"experience","score":0.031}],
   "trajectory":[{"sourceType":"plan","decision":"stub","reason":"..."}]}

docker compose ps
  agent  Up (healthy)  0.0.0.0:8001->8001/tcp
  api    Up            0.0.0.0:8081->8080/tcp   /actuator/health -> {"status":"UP", db UP}
```

The api host port reads 8081, not the committed 8080 — see the last decision below.

124 points matches the issue-04 post-migration backfill count, so the Python client is reading the
same collection the Java write path fills.

## Decisions made while implementing

**`/health` is unauthenticated; only `/chat` requires the token.** The two acceptance criteria above
are in tension as written. Container `HEALTHCHECK` and the ALB target-group check in issue 11 cannot
send headers, so gating `/health` would make the service permanently unhealthy. `require_internal_token`
is therefore a per-route dependency, not global middleware. The Verify curl passes either way — the
extra header is simply ignored.

**`INTERNAL_TOKEN` has no default.** `Settings` fails at import if it is unset, so a misconfigured
deploy refuses to boot instead of coming up with an open or guessable `/chat`. Every other setting
has a default.

**Qdrant failure at startup is logged, not fatal.** The lifespan hook catches, so `/health` still
answers and the container reports the reason rather than crash-looping. Retrieval does not exist yet
(issue 07); revisit whether this should be fatal once it does.

**The compose api port was remapped to 8081 during verification only.** Port 8080 was held by a local
`spring-boot:run` that was left running. The committed `docker-compose.yml` maps `8080:8080`; the
`8081:8080` in the output above came from a throwaway `-f` override file, which is why the container
port and health check are still 8080. The 8080 host mapping itself is the one thing here that was not
exercised.
