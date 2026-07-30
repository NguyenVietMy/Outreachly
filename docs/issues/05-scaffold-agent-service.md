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

- [ ] `GET /health` returns 200
- [ ] `POST /chat` returns a hardcoded well-formed `ChatResponse`-shaped payload (contract PRD §6.1)
- [ ] Requests without a valid `X-Internal-Token` return 401
- [ ] `docker compose up` starts api + agent; agent reaches Qdrant and logs collection info
- [ ] `pytest` green; `ruff check` clean
- [ ] `agent/README.md` covers local run, env vars, and test commands

## Verify

```bash
cd agent && uv run pytest && uv run ruff check
docker compose up --build
curl -H "X-Internal-Token: dev" localhost:8001/health
```
