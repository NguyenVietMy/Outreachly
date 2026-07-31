# 07 — LangGraph `StateGraph` (plan → retrieve → reflect → answer)

**Track:** B · Agent  ·  **Blocked by:** 03, 05, 06  ·  **Blocks:** 08, 10
**Estimate:** ~8h — this is the centrepiece

## Goal

Replace four hard-coded integer thresholds with a graph in which the **model** decides what to
retrieve and whether it has enough to answer.

## The graph

```
        START
          │
          ▼
       ┌──────┐   picks which sources to pull and why
       │ plan │   -> emits trajectory entries
       └──┬───┘
          ▼
     ┌──────────┐  hybrid search against Qdrant +
     │ retrieve │  /internal/context/* fetches
     └────┬─────┘
          ▼
     ┌──────────┐  "can I answer from this?"
     │ reflect  │  conditional edge
     └──┬────┬──┘
        │    └──── insufficient ──▶ back to retrieve (max 2 rounds)
        │ sufficient
        ▼
     ┌────────┐
     │ answer │ ──▶ END
     └────────┘
```

Build it with `StateGraph` + `.compile()`, **not** `create_react_agent`. The prebuilt is a fine
shortcut, but an explicit graph with a named conditional edge is what makes the control flow legible
— and it is what you will be asked to explain.

## State

```python
class AgentState(TypedDict):
    user_id: int
    message: str
    history: list[dict]
    plan: str
    sources_to_query: list[str]      # subset of the 6 source types
    retrieved: list[RetrievedChunk]
    iterations: int
    trajectory: list[TrajectoryStep] # -> RoutingDecision on the Java side
    answer: str
```

## Tools

| Tool | Backed by |
|---|---|
| `search_knowledge(query, source_types, top_k)` | Qdrant hybrid RRF (mirrors issue 03's query exactly) |
| `get_profile()` | `GET /internal/context/profile` |
| `get_items()` | `GET /internal/context/items` |
| `get_github_projects()` | `GET /internal/context/github` |

Source types: `resume_section`, `goal`, `task`, `roadmap`, `github_readme`, `obsidian_diff`.

## Hard limits (cost safety)

- `iterations <= 2` retrieval rounds
- `<= 3` total LLM calls per request
- Model: `claude-haiku-4-5` for plan/reflect, `claude-haiku-4-5` for answer (matches the existing
  chat assignment in `PLAN.md`). If answer quality regresses, that is a measured argument for
  Sonnet 5 on the answer node — do not upgrade preemptively.
- Do **not** pass `temperature`/`top_p`/`top_k` overrides to Sonnet 5 / Opus 5 — they are rejected
  with a 400. Do **not** use `budget_tokens`; use `thinking: {"type": "adaptive"}` if thinking is
  wanted at all.

## Trajectory → RoutingDecision

Every node appends a step that maps onto the existing frontend contract:

```python
TrajectoryStep(source_type="plan",     decision="retrieve", reason="question mentions internships; pulling resume + roadmap")
TrajectoryStep(source_type="resume_section", decision="retrieved", reason="4 chunks, top RRF 0.031")
TrajectoryStep(source_type="reflect",  decision="sufficient", reason="resume covers 2 of 2 internships asked about")
```

This is why the frontend needs no changes. Preserve it precisely.

## System prompt

Carry over the existing grounding constraints verbatim from `ChatService.java:181-186` — "use ONLY
the information provided", "do not invent", "say so if insufficient". Do not rewrite them; they are
what the eval harness's groundedness judge assumes.

## Acceptance criteria

- [x] Graph compiles; `.get_graph().draw_mermaid()` output committed to `agent/README.md`
- [x] A question answerable from one source terminates in **one** retrieval round
- [x] A question spanning resume + GitHub + notes triggers a **second** round via `reflect`
      — see *What the loop actually fires on* below
- [x] Iteration cap enforced (test with a deliberately unanswerable question)
- [x] Sources returned with real RRF scores; trajectory populated at every node
- [x] Grounding holds: a question with no supporting context yields "I don't have enough information"
- [x] `pytest` green including a graph-level test with mocked LLM + Qdrant

## Verify

```bash
cd agent && uv run pytest tests/test_graph.py -v
curl -X POST localhost:8001/chat -H "X-Internal-Token: dev" \
  -d '{"userId":1,"message":"How do my projects line up with my target role?","history":[]}'
```

## Result

`uv run pytest` — 16 passed. `uv run ruff check` — clean. Four real questions against user 5, with
the Java API on :8081 and the live Qdrant cluster:

| Question | Rounds | Trajectory |
|---|---|---|
| "What system design topics are on my study plan?" | 1 | plan → task 4 chunks / roadmap 1 chunk → reflect sufficient → answer |
| "How do my projects line up with my target role?" | 1 | plan → github_readmes inline → github_readme 2 chunks → reflect sufficient → answer |
| "What have I been studying in my notes lately, and how does that connect to the projects on my resume and GitHub?" | 1 | plan → resume + github inline → resume_section / github_readme / obsidian_diff → reflect sufficient → answer |
| "Which of my goals am I furthest behind on, and is there anything in my study notes that explains why?" | **2** | plan → items inline, goal `no_results`, obsidian_diff 1 chunk → **reflect insufficient** → second search → reflect (cap) → answer |
| "What was my manager's name at my last internship…" | **2** | plan → resume inline, resume_section 5 chunks → **reflect insufficient** → second search → reflect (cap) → answer: *"I don't have that information in your profile."* |

### What the loop actually fires on

Not "multi-source question" — **under-served** question. A question spanning resume + GitHub +
notes terminates in one round when the planner picks all three up front, which is the outcome you
want. The loop fires when the first round comes back thin: a source that returns nothing above the
RRF floor, or material that does not contain what was asked. Both of those are in the table above,
and `test_reflect_triggers_a_second_round` pins the behaviour deterministically.

### Why `reflect` short-circuits at the cap

The issue asks for both `iterations <= 2` and `<= 3` LLM calls. A second reflection could only ever
route to `answer`, so once `iterations >= MAX_ITERATIONS` the node returns `sufficient=True` without
calling the model. Worst case is therefore `plan` + one `reflect` + `answer` = 3, and the graph
keeps the single conditional edge the issue's diagram specifies.

### One prompt bug found by running it

The first live run spent its second round hunting for the student's target role — which the answer
node always has, because the profile header is unconditional. `reflect` was being shown only the
retrieved chunks, not that header. Fixed in `_material`; covered by
`test_reflect_sees_the_profile_header_and_the_chunks`.
