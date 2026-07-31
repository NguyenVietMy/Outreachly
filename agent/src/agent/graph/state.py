import operator
from typing import Annotated, Any, TypedDict

from pydantic import BaseModel

from agent.models import ChatMessage, TrajectoryStep

# The six source types the Java write path indexes into Qdrant. The planner may pick any subset.
SOURCE_TYPES = [
    "resume_section",
    "goal",
    "task",
    "roadmap",
    "github_readme",
    "obsidian_diff",
]

# Whole-record context pulled from /internal/context/* rather than searched. This is what replaces
# ChatService's RESUME_INLINE_THRESHOLD / ITEMS_INLINE_THRESHOLD / GITHUB_INLINE_THRESHOLD: the
# model decides when the complete list beats a top-k slice of it.
CONTEXT_SECTIONS = ["resume", "items", "github"]


class RetrievedChunk(BaseModel):
    source_type: str
    source_key: str
    chunk_index: int
    content: str
    score: float


class AgentState(TypedDict):
    user_id: int
    message: str
    history: list[ChatMessage]

    # Fetched once, before planning: the planner needs the inventory (list sizes, resume length,
    # repo names) to choose between searching and inlining.
    profile: dict[str, Any]
    items: dict[str, Any]
    github: dict[str, Any]

    plan: str
    search_query: str
    sources_to_query: list[str]
    context_sections: list[str]

    retrieved: list[RetrievedChunk]
    iterations: int
    sufficient: bool

    # Appended to by every node; maps 1:1 onto ChatService.RoutingDecision on the Java side.
    trajectory: Annotated[list[TrajectoryStep], operator.add]

    answer: str
