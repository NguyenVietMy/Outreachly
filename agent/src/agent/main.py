import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from agent.auth import require_internal_token
from agent.clients import pulse_api, qdrant
from agent.graph import build_graph, run_chat
from agent.models import ChatRequest, ChatResponse

logging.basicConfig(level=logging.INFO)

graph = build_graph()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await qdrant.log_collection_info()
    yield
    await qdrant.close_client()
    await pulse_api.close_client()


app = FastAPI(title="Pulse Agent", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", dependencies=[Depends(require_internal_token)])
async def chat(request: ChatRequest) -> ChatResponse:
    return await run_chat(graph, request.user_id, request.message, request.history)
