"""The four nodes of the chat graph.

Cost safety, per `docs/issues/07`: at most 2 retrieval rounds and at most 3 LLM calls per request.
The second bound is what makes `reflect` short-circuit once the iteration cap is reached — a second
reflection could only route to `answer` anyway, so it is not worth a model call.
"""

import asyncio
import logging

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from agent.clients.anthropic import get_llm
from agent.graph import tools
from agent.graph.state import CONTEXT_SECTIONS, SOURCE_TYPES, AgentState, RetrievedChunk
from agent.models import TrajectoryStep

logger = logging.getLogger(__name__)

MAX_ITERATIONS = 2
RAG_TOP_K = 5
MAX_HISTORY_TURNS = 10

PLAN_MAX_TOKENS = 600
ANSWER_MAX_TOKENS = 800

# Verbatim from ChatService.java:181-186. The eval harness's groundedness judge assumes this
# wording — do not paraphrase it.
GROUNDING_PROMPT = (
    "You are a personal CS career coach for this student. "
    "Answer their question using ONLY the information provided below about the student. "
    "Be specific and reference real data from the context. "
    "Do not invent information not present in the context. "
    "If the context doesn't contain enough information to answer, say so.\n\n"
)

PLAN_PROMPT = """You are the planning step of a retrieval agent answering questions about one CS \
student. Decide what to pull before the answer is written. You have two mechanisms.

1. Search — hybrid dense + BM25 search over the student's indexed knowledge base. Pick any subset \
of these source types:
   resume_section  sections of the resume
   goal            tracked goals
   task            study-plan tasks
   roadmap         career roadmap items
   github_readme   READMEs of the student's GitHub projects
   obsidian_diff   dated study notes

2. Whole-record context — the complete list rather than a top-k slice of it. Pick any subset of:
   resume  the full resume text
   items   every goal, uncompleted task and roadmap entry
   github  every project README

Prefer search when the question is about a topic. Prefer whole-record context when the question is \
about coverage, counts, or "all of my X", and the inventory below shows the list is small enough \
to read whole. Pulling everything is expensive and dilutes the answer — pull what the question \
needs, and nothing more.

Also write the search query. Resolve pronouns and references against the conversation so the query \
stands on its own.

INVENTORY OF WHAT EXISTS FOR THIS STUDENT
{inventory}"""

REFLECT_PROMPT = """You are the reflection step of a retrieval agent. Decide whether the material \
gathered so far is enough to answer the student's question.

Say sufficient=true if the material covers what was asked, or if no further search is likely to \
help. Say sufficient=false only when a *different* search would plausibly surface something \
missing — there is exactly one more retrieval round available, and it can only run a search.

MATERIAL GATHERED SO FAR
{material}"""


class Plan(BaseModel):
    """What to retrieve, and why."""

    reason: str = Field(description="One sentence: why these sources answer this question.")
    search_query: str = Field(description="Standalone query for the knowledge-base search.")
    source_types: list[str] = Field(description="Source types to search. May be empty.")
    context_sections: list[str] = Field(
        description="Whole-record sections to pull in full. May be empty."
    )


class Reflection(BaseModel):
    """Whether what was gathered is enough."""

    sufficient: bool = Field(description="True if the question can be answered from the material.")
    reason: str = Field(description="One sentence justifying the verdict.")
    follow_up_query: str = Field(default="", description="Search query for the second round.")
    source_types: list[str] = Field(default_factory=list, description="Source types to search.")


async def plan_node(state: AgentState) -> dict:
    user_id = state["user_id"]
    profile, items, github = await asyncio.gather(
        tools.get_profile(user_id), tools.get_items(user_id), tools.get_github_projects(user_id)
    )

    llm = get_llm(PLAN_MAX_TOKENS).with_structured_output(Plan)
    plan: Plan = await llm.ainvoke(
        [
            SystemMessage(content=PLAN_PROMPT.format(inventory=_inventory(profile, items, github))),
            *_history_messages(state),
            HumanMessage(content=state["message"]),
        ]
    )

    source_types = _keep_known(plan.source_types, SOURCE_TYPES)
    context_sections = _keep_known(plan.context_sections, CONTEXT_SECTIONS)

    return {
        "profile": profile,
        "items": items,
        "github": github,
        "plan": plan.reason,
        "search_query": plan.search_query or state["message"],
        "sources_to_query": source_types,
        "context_sections": context_sections,
        "trajectory": [
            TrajectoryStep(source_type="plan", decision="retrieve", reason=plan.reason)
        ],
    }


async def retrieve_node(state: AgentState) -> dict:
    iteration = state["iterations"] + 1
    source_types = state["sources_to_query"]

    chunks = await tools.search_knowledge(
        state["user_id"], state["search_query"], source_types, RAG_TOP_K
    )
    merged = _merge(state["retrieved"], chunks)

    trajectory = []
    # Whole-record context is decided once, by the planner, so it is only reported on round one.
    if iteration == 1:
        trajectory += _context_steps(state)
    for source_type in source_types:
        hits = [chunk for chunk in chunks if chunk.source_type == source_type]
        if hits:
            top = max(hit.score for hit in hits)
            trajectory.append(
                TrajectoryStep(
                    source_type=source_type,
                    decision="retrieved",
                    reason=f"{len(hits)} chunks, top RRF {top:.3f}",
                )
            )
        else:
            trajectory.append(
                TrajectoryStep(
                    source_type=source_type,
                    decision="no_results",
                    reason=f"round {iteration}: nothing above the {tools.MIN_RRF_THRESHOLD} "
                    f"RRF floor",
                )
            )

    return {"retrieved": merged, "iterations": iteration, "trajectory": trajectory}


async def reflect_node(state: AgentState) -> dict:
    if state["iterations"] >= MAX_ITERATIONS:
        return {
            "sufficient": True,
            "trajectory": [
                TrajectoryStep(
                    source_type="reflect",
                    decision="sufficient",
                    reason=f"iteration cap ({MAX_ITERATIONS}) reached — answering with what is "
                    f"available",
                )
            ],
        }

    llm = get_llm(PLAN_MAX_TOKENS).with_structured_output(Reflection)
    reflection: Reflection = await llm.ainvoke(
        [
            SystemMessage(content=REFLECT_PROMPT.format(material=_material(state))),
            HumanMessage(content=state["message"]),
        ]
    )

    update = {
        "sufficient": reflection.sufficient,
        "trajectory": [
            TrajectoryStep(
                source_type="reflect",
                decision="sufficient" if reflection.sufficient else "insufficient",
                reason=reflection.reason,
            )
        ],
    }
    if not reflection.sufficient:
        follow_up = _keep_known(reflection.source_types, SOURCE_TYPES) or state["sources_to_query"]
        update["search_query"] = reflection.follow_up_query or state["search_query"]
        update["sources_to_query"] = follow_up
    return update


async def answer_node(state: AgentState) -> dict:
    context = _context(state)

    llm = get_llm(ANSWER_MAX_TOKENS)
    response = await llm.ainvoke(
        [
            SystemMessage(content=GROUNDING_PROMPT + context),
            *_history_messages(state),
            HumanMessage(content=state["message"]),
        ]
    )

    return {
        "answer": _text(response),
        "trajectory": [
            TrajectoryStep(
                source_type="answer",
                decision="generated",
                reason=f"{len(state['retrieved'])} retrieved chunks over "
                f"{state['iterations']} round(s)",
            )
        ],
    }


def route_after_reflect(state: AgentState) -> str:
    """The named conditional edge: the only place the graph can loop."""
    return "answer" if state["sufficient"] else "retrieve"


def _inventory(profile: dict, items: dict, github: dict) -> str:
    repos = github.get("repos") or []
    repo_names = ", ".join(repo["name"] for repo in repos) if repos else "none"
    return "\n".join(
        [
            f"Target role: {profile.get('targetRole') or 'not set'}",
            f"Graduation year: {profile.get('graduationYear') or 'not set'}",
            f"Resume on file: {profile.get('resumeChars', 0)} characters",
            f"Goals: {len(items.get('goals') or [])}",
            f"Uncompleted study tasks: {len(items.get('tasks') or [])}",
            f"Roadmap items: {len(items.get('roadmap') or [])}",
            f"GitHub projects with READMEs: {len(repos)} ({repo_names})",
        ]
    )


def _context_steps(state: AgentState) -> list[TrajectoryStep]:
    sections = state["context_sections"]
    steps = []
    if "resume" in sections:
        steps.append(
            TrajectoryStep(
                source_type="resume",
                decision="inline",
                reason=f"{state['profile'].get('resumeChars', 0)} chars pulled in full",
            )
        )
    if "items" in sections:
        items = state["items"]
        total = sum(len(items.get(key) or []) for key in ("goals", "tasks", "roadmap"))
        steps.append(
            TrajectoryStep(
                source_type="goals_tasks_roadmap",
                decision="inline",
                reason=f"{total} items pulled in full",
            )
        )
    if "github" in sections:
        steps.append(
            TrajectoryStep(
                source_type="github_readmes",
                decision="inline",
                reason=f"{len(state['github'].get('repos') or [])} repos pulled in full",
            )
        )
    return steps


def _context(state: AgentState) -> str:
    sections = state["context_sections"]
    blocks = [tools.format_profile(state["profile"])]

    if "resume" in sections:
        blocks.append(tools.format_resume(state["profile"]))
    # LeetCode stats are a handful of integers; ChatService always inlines them and so do we.
    blocks.append(tools.format_leetcode(state["profile"]))
    if "items" in sections:
        blocks.append(tools.format_items(state["items"]))
    if "github" in sections:
        blocks.append(tools.format_github(state["github"]))

    if state["retrieved"]:
        lines = ["=== RETRIEVED KNOWLEDGE ==="]
        for chunk in state["retrieved"]:
            label = tools.format_source_label(chunk.source_type, chunk.source_key)
            lines.append(f"[Source: {label}]\n{chunk.content}\n")
        blocks.append("\n".join(lines))

    return "\n".join(block for block in blocks if block)


def _material(state: AgentState) -> str:
    # The profile header is always in the answer's context, so reflect has to see it too —
    # otherwise it burns a retrieval round looking for the target role it already has.
    profile = state["profile"]
    lines = [
        f"- profile: target role {profile.get('targetRole') or 'not set'}, "
        f"graduating {profile.get('graduationYear') or 'unknown'}"
    ]
    for section in state["context_sections"]:
        lines.append(f"- whole-record context pulled: {section}")
    if state["retrieved"]:
        for chunk in state["retrieved"]:
            label = tools.format_source_label(chunk.source_type, chunk.source_key)
            lines.append(f"- [{label}] {chunk.content[:400]}")
    else:
        lines.append("- the search returned nothing")
    return "\n".join(lines)


def _history_messages(state: AgentState) -> list[AIMessage | HumanMessage]:
    history = state["history"][-MAX_HISTORY_TURNS * 2 :]
    return [
        AIMessage(content=message.content)
        if message.role == "assistant"
        else HumanMessage(content=message.content)
        for message in history
    ]


def _merge(existing: list[RetrievedChunk], new: list[RetrievedChunk]) -> list[RetrievedChunk]:
    """Rounds overlap; keep one entry per chunk at its best score, ordered by score."""
    best: dict[tuple[str, str, int], RetrievedChunk] = {}
    for chunk in [*existing, *new]:
        key = (chunk.source_type, chunk.source_key, chunk.chunk_index)
        if key not in best or chunk.score > best[key].score:
            best[key] = chunk
    return sorted(best.values(), key=lambda chunk: chunk.score, reverse=True)


def _keep_known(chosen: list[str], allowed: list[str]) -> list[str]:
    """Models invent vocabulary; anything off the list is dropped rather than sent to Qdrant."""
    unknown = [value for value in chosen if value not in allowed]
    if unknown:
        logger.warning("Planner returned unknown values %s, dropping", unknown)
    return [value for value in allowed if value in chosen]


def _text(response: AIMessage) -> str:
    """Anthropic returns content as a block list once thinking or tools are in play."""
    if isinstance(response.content, str):
        return response.content
    return "".join(
        block.get("text", "")
        for block in response.content
        if isinstance(block, dict) and block.get("type") == "text"
    )
