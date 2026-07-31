"""The four things the graph can pull: one vector search and three context fetches.

The context formatters reproduce ChatService's `=== SECTION ===` blobs verbatim — the Java
endpoints hand back raw fields on purpose, and the wording is what the eval harness's groundedness
judge has seen.
"""

from typing import Any

from agent.clients import embeddings, pulse_api, qdrant
from agent.graph.state import RetrievedChunk

# Carried over from HybridRetrievalService. Qdrant has no equivalent of this cutoff — dropping it
# silently widens recall.
MIN_RRF_THRESHOLD = 0.008

README_INLINE_CHARS = 2000


async def search_knowledge(
    user_id: int, query: str, source_types: list[str], top_k: int
) -> list[RetrievedChunk]:
    if not source_types:
        return []

    dense_vector = await embeddings.embed(query)
    points = await qdrant.hybrid_search(
        user_id=user_id,
        query_text=query,
        dense_vector=dense_vector,
        source_types=source_types,
        prefetch_limit=top_k * 2,
        limit=top_k,
    )
    return [
        RetrievedChunk(
            source_type=point.payload["source_type"],
            source_key=point.payload["source_key"],
            chunk_index=int(point.payload["chunk_index"]),
            content=point.payload["content"],
            score=point.score,
        )
        for point in points
        if point.score >= MIN_RRF_THRESHOLD
    ]


async def get_profile(user_id: int) -> dict[str, Any]:
    return await pulse_api.get_profile(user_id)


async def get_items(user_id: int) -> dict[str, Any]:
    return await pulse_api.get_items(user_id)


async def get_github_projects(user_id: int) -> dict[str, Any]:
    return await pulse_api.get_github(user_id)


def format_profile(profile: dict[str, Any]) -> str:
    lines = ["=== STUDENT PROFILE ==="]
    if profile.get("profileMarkdown"):
        lines.append(profile["profileMarkdown"])
    lines.append(f"Target Role: {profile.get('targetRole') or 'not set'}")
    if profile.get("graduationYear"):
        lines.append(f"Graduation Year: {profile['graduationYear']}")
    if profile.get("axisScores"):
        lines.append(f"Axis Scores: {profile['axisScores']}")
    return "\n".join(lines) + "\n"


def format_leetcode(profile: dict[str, Any]) -> str:
    if not profile.get("leetcodeStats"):
        return ""
    return f"=== LEETCODE ===\n{profile['leetcodeStats']}\n"


def format_resume(profile: dict[str, Any]) -> str:
    if not profile.get("resumeText"):
        return ""
    return f"=== RESUME ===\n{profile['resumeText']}\n"


def format_items(items: dict[str, Any]) -> str:
    blocks = []

    goals = items.get("goals") or []
    if goals:
        lines = ["=== GOALS ==="]
        for goal in goals:
            due = f" due {goal['deadline']}" if goal.get("deadline") else ""
            lines.append(
                f"- {goal['title']} ({goal['currentValue']}/{goal['targetValue']} {goal['unit']})"
                f"{due} [{goal['status']}]"
            )
        blocks.append("\n".join(lines))

    tasks = items.get("tasks") or []
    if tasks:
        lines = ["=== STUDY PLAN (UNCOMPLETED TASKS) ==="]
        lines += [f"- [{task['axis']}] {task['title']}" for task in tasks]
        blocks.append("\n".join(lines))

    roadmap = items.get("roadmap") or []
    if roadmap:
        lines = ["=== CAREER ROADMAP ==="]
        for entry in roadmap:
            due = f" due {entry['deadline']}" if entry.get("deadline") else ""
            lines.append(f"- {entry['title']} ({entry['phase']}){due} [{entry['status']}]")
        blocks.append("\n".join(lines))

    return "\n\n".join(blocks) + "\n" if blocks else ""


def format_github(github: dict[str, Any]) -> str:
    blocks = []
    for repo in github.get("repos") or []:
        lines = [f"=== PROJECT: {repo['name']} ==="]
        if repo.get("description"):
            lines.append(repo["description"])
        if repo.get("primaryLanguage"):
            lines.append(f"Language: {repo['primaryLanguage']}")
        lines.append((repo.get("readmeContent") or "")[:README_INLINE_CHARS])
        blocks.append("\n".join(lines))
    return "\n\n".join(blocks) + "\n" if blocks else ""


def format_source_label(source_type: str, source_key: str) -> str:
    match source_type:
        case "github_readme":
            return f"{source_key} (GitHub README)"
        case "obsidian_diff":
            return f"{source_key} (Study Notes)"
        case "resume_section":
            return f"{source_key} (Resume)"
        case "goal":
            return f"Goal (ID: {source_key})"
        case "task":
            return f"Study Task (ID: {source_key})"
        case "roadmap":
            return f"Roadmap Item (ID: {source_key})"
        case _:
            return f"{source_type}: {source_key}"
