from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


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
