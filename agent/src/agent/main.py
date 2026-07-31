import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from agent.auth import require_internal_token
from agent.clients import pulse_api, qdrant

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await qdrant.log_collection_info()
    yield
    await qdrant.close_client()
    await pulse_api.close_client()


app = FastAPI(title="Pulse Agent", lifespan=lifespan)


class CamelModel(BaseModel):
    """The wire contract is camelCase — it maps 1:1 onto ChatService's Java records."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChatMessage(CamelModel):
    role: str
    content: str


class ChatRequest(CamelModel):
    user_id: int
    message: str
    history: list[ChatMessage] = []


class SourceCitation(CamelModel):
    source_type: str
    source_key: str
    score: float


class TrajectoryStep(CamelModel):
    source_type: str
    decision: str
    reason: str


class ChatResponse(CamelModel):
    message: str
    sources: list[SourceCitation] = []
    trajectory: list[TrajectoryStep] = []


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", dependencies=[Depends(require_internal_token)])
async def chat(request: ChatRequest) -> ChatResponse:
    # Hardcoded until issue 07 replaces this with the LangGraph StateGraph.
    return ChatResponse(
        message=f"Agent scaffold received: {request.message}",
        sources=[
            SourceCitation(source_type="resume_section", source_key="experience", score=0.031)
        ],
        trajectory=[
            TrajectoryStep(
                source_type="plan",
                decision="stub",
                reason="LangGraph graph not implemented yet (issue 07)",
            )
        ],
    )
