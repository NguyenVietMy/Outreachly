import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from langfuse import get_client, observe, propagate_attributes

from agent.auth import require_internal_token
from agent.clients import pulse_api, qdrant
from agent.graph import build_graph, run_chat
from agent.models import ChatRequest, ChatResponse
from agent.observability import continue_upstream_trace, init_tracing

logging.basicConfig(level=logging.INFO)

init_tracing()
graph = build_graph()


@asynccontextmanager
async def lifespan(app: FastAPI):
    await qdrant.log_collection_info()
    yield
    # A batched span processor would otherwise lose whatever it is holding on shutdown.
    get_client().flush()
    await qdrant.close_client()
    await pulse_api.close_client()


app = FastAPI(title="Pulse Agent", lifespan=lifespan)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", dependencies=[Depends(require_internal_token)])
async def chat(request: ChatRequest, http_request: Request) -> ChatResponse:
    # Attaching the upstream context has to happen outside the observed call, so the span it
    # creates already has the Java span as its parent.
    with continue_upstream_trace(http_request.headers):
        return await _observed_chat(request)


@observe(name="POST /chat", capture_input=False, capture_output=False)
async def _observed_chat(request: ChatRequest) -> ChatResponse:
    with propagate_attributes(user_id=str(request.user_id)):
        return await run_chat(graph, request.user_id, request.message, request.history)
