"""Graph-level tests with the LLM and Qdrant mocked out.

Everything the graph reaches for — the three /internal/context fetches, the Qdrant hybrid search,
and the model itself — is replaced, so these assert control flow and prompt assembly rather than
model behaviour.
"""

import pytest
from langchain_core.messages import AIMessage

from agent.graph import build_graph, run_chat
from agent.graph.nodes import GROUNDING_PROMPT, MAX_ITERATIONS, Plan, Reflection
from agent.graph.state import RetrievedChunk
from agent.models import ChatMessage

PROFILE = {
    "profileMarkdown": "Third-year CS student.",
    "targetRole": "Backend Engineer",
    "graduationYear": 2027,
    "axisScores": {"coreCs": 62},
    "leetcodeStats": {"solved": 140},
    "resumeText": "EXPERIENCE\nBackend intern at Acme.",
    "resumeChars": 34,
}
ITEMS = {
    "goals": [
        {
            "title": "Solve 300 problems",
            "currentValue": 140,
            "targetValue": 300,
            "unit": "problems",
            "deadline": "2026-12-01",
            "status": "active",
        }
    ],
    "tasks": [{"axis": "systemDesign", "title": "Study backpressure"}],
    "roadmap": [
        {"title": "Ship a distributed system", "phase": "build", "deadline": None, "status": "todo"}
    ],
}
GITHUB = {
    "repos": [
        {
            "name": "viet/pulse",
            "description": "Career platform",
            "primaryLanguage": "Java",
            "readmeContent": "# Pulse\nSpring Boot + Next.js.",
        }
    ]
}


class Script:
    """The canned model responses, plus a record of every call the graph made."""

    def __init__(self, plan: Plan, reflections: list[Reflection], answer: str = "An answer."):
        self.plan = plan
        self.reflections = reflections
        self.answer = answer
        self.llm_calls: list[tuple[str, list]] = []
        self.searches: list[tuple[str, list[str]]] = []

    def next_reflection(self) -> Reflection:
        # The last entry repeats, so a script can hold "always insufficient".
        return self.reflections.pop(0) if len(self.reflections) > 1 else self.reflections[0]


class FakeStructuredLLM:
    def __init__(self, script: Script, schema: type):
        self.script = script
        self.schema = schema

    async def ainvoke(self, messages):
        if self.schema is Plan:
            self.script.llm_calls.append(("plan", messages))
            return self.script.plan
        self.script.llm_calls.append(("reflect", messages))
        return self.script.next_reflection()


class FakeLLM:
    def __init__(self, script: Script):
        self.script = script

    def with_structured_output(self, schema: type):
        return FakeStructuredLLM(self.script, schema)

    async def ainvoke(self, messages):
        self.script.llm_calls.append(("answer", messages))
        return AIMessage(content=self.script.answer)


@pytest.fixture
def harness(monkeypatch):
    """Wires a Script into the graph and hands back a runner."""

    def install(script: Script, chunks_by_round: list[list[RetrievedChunk]]):
        monkeypatch.setattr("agent.graph.nodes.get_llm", lambda max_tokens: FakeLLM(script))

        async def fake_search(user_id, query, source_types, top_k):
            if not source_types:  # mirrors the real short-circuit in tools.search_knowledge
                return []
            script.searches.append((query, list(source_types)))
            index = len(script.searches) - 1
            return chunks_by_round[index] if index < len(chunks_by_round) else []

        monkeypatch.setattr("agent.graph.tools.search_knowledge", fake_search)

        async def fake_profile(user_id):
            return PROFILE

        async def fake_items(user_id):
            return ITEMS

        async def fake_github(user_id):
            return GITHUB

        monkeypatch.setattr("agent.graph.tools.get_profile", fake_profile)
        monkeypatch.setattr("agent.graph.tools.get_items", fake_items)
        monkeypatch.setattr("agent.graph.tools.get_github_projects", fake_github)

        return build_graph()

    return install


def chunk(source_type: str, key: str, score: float, index: int = 0) -> RetrievedChunk:
    return RetrievedChunk(
        source_type=source_type,
        source_key=key,
        chunk_index=index,
        content=f"content of {key}",
        score=score,
    )


def decisions(response) -> list[tuple[str, str]]:
    return [(step.source_type, step.decision) for step in response.trajectory]


def system_prompt_of(script: Script, kind: str) -> str:
    return next(messages[0].content for name, messages in script.llm_calls if name == kind)


async def test_graph_compiles_and_draws_mermaid(harness):
    graph = harness(Script(Plan(reason="", search_query="q", source_types=[], context_sections=[]),
                           [Reflection(sufficient=True, reason="")]), [])
    mermaid = graph.get_graph().draw_mermaid()
    for node in ("plan", "retrieve", "reflect", "answer"):
        assert node in mermaid


async def test_single_round_when_one_source_answers(harness):
    script = Script(
        Plan(
            reason="question mentions system design; pulling study tasks",
            search_query="system design study tasks",
            source_types=["task"],
            context_sections=[],
        ),
        [Reflection(sufficient=True, reason="tasks cover the topic asked about")],
    )
    graph = harness(script, [[chunk("task", "t1", 0.031), chunk("task", "t2", 0.029)]])

    response = await run_chat(graph, 5, "What am I studying about system design?", [])

    assert len(script.searches) == 1
    assert script.searches[0] == ("system design study tasks", ["task"])
    assert decisions(response) == [
        ("plan", "retrieve"),
        ("task", "retrieved"),
        ("reflect", "sufficient"),
        ("answer", "generated"),
    ]
    assert [step.reason for step in response.trajectory if step.source_type == "task"] == [
        "2 chunks, top RRF 0.031"
    ]
    assert [(s.source_type, s.score) for s in response.sources] == [
        ("task", 0.031),
        ("task", 0.029),
    ]


async def test_reflect_triggers_a_second_round(harness):
    script = Script(
        Plan(
            reason="spans resume, projects and notes",
            search_query="internships and projects",
            source_types=["resume_section"],
            context_sections=["github"],
        ),
        [
            Reflection(
                sufficient=False,
                reason="resume names the internships but not what was built",
                follow_up_query="what I built during the internships",
                source_types=["github_readme", "obsidian_diff"],
            ),
            Reflection(sufficient=True, reason="now covered"),
        ],
    )
    graph = harness(
        script,
        [
            [chunk("resume_section", "experience", 0.031)],
            [chunk("github_readme", "viet/pulse", 0.028)],
        ],
    )

    response = await run_chat(graph, 5, "How do my projects line up with my target role?", [])

    assert len(script.searches) == 2
    assert script.searches[1] == (
        "what I built during the internships",
        ["github_readme", "obsidian_diff"],
    )
    assert decisions(response) == [
        ("plan", "retrieve"),
        ("github_readmes", "inline"),
        ("resume_section", "retrieved"),
        ("reflect", "insufficient"),
        ("github_readme", "retrieved"),
        ("obsidian_diff", "no_results"),
        ("reflect", "sufficient"),
        ("answer", "generated"),
    ]
    # Chunks from both rounds survive, ordered by RRF score.
    assert [s.source_key for s in response.sources] == ["experience", "viet/pulse"]


async def test_iteration_cap_holds_and_costs_three_llm_calls(harness):
    script = Script(
        Plan(
            reason="unanswerable, will keep looking",
            search_query="who is my manager",
            source_types=["obsidian_diff"],
            context_sections=[],
        ),
        [Reflection(sufficient=False, reason="still nothing", follow_up_query="manager name")],
    )
    graph = harness(script, [])

    response = await run_chat(graph, 5, "Who was my manager at my last internship?", [])

    assert len(script.searches) == MAX_ITERATIONS
    # plan + one reflect + answer. The second reflect short-circuits: with the cap reached it can
    # only route to answer, so it is not worth a model call.
    assert [name for name, _ in script.llm_calls] == ["plan", "reflect", "answer"]
    assert decisions(response)[-2:] == [("reflect", "sufficient"), ("answer", "generated")]
    assert "iteration cap" in response.trajectory[-2].reason
    assert response.sources == []


async def test_grounding_constraints_survive_with_no_context(harness):
    script = Script(
        Plan(reason="nothing relevant", search_query="q", source_types=["goal"],
             context_sections=[]),
        [Reflection(sufficient=True, reason="nothing more to find")],
    )
    graph = harness(script, [])

    await run_chat(graph, 5, "What is the capital of France?", [])

    prompt = system_prompt_of(script, "answer")
    assert prompt.startswith(GROUNDING_PROMPT)
    assert "using ONLY the information provided" in prompt
    assert "=== RETRIEVED KNOWLEDGE ===" not in prompt
    assert "=== RESUME ===" not in prompt


async def test_context_sections_are_formatted_into_the_answer_prompt(harness):
    script = Script(
        Plan(reason="asks for everything", search_query="q", source_types=[],
             context_sections=["resume", "items", "github"]),
        [Reflection(sufficient=True, reason="all of it is here")],
    )
    graph = harness(script, [])

    response = await run_chat(graph, 5, "Summarise everything you know about me.", [])

    prompt = system_prompt_of(script, "answer")
    assert "=== STUDENT PROFILE ===" in prompt
    assert "Target Role: Backend Engineer" in prompt
    assert "=== RESUME ===\nEXPERIENCE" in prompt
    assert "=== LEETCODE ===" in prompt
    assert (
        "=== GOALS ===\n- Solve 300 problems (140/300 problems) due 2026-12-01 [active]" in prompt
    )
    assert "=== STUDY PLAN (UNCOMPLETED TASKS) ===\n- [systemDesign] Study backpressure" in prompt
    assert "=== CAREER ROADMAP ===\n- Ship a distributed system (build) [todo]" in prompt
    assert "=== PROJECT: viet/pulse ===" in prompt
    assert decisions(response)[1:4] == [
        ("resume", "inline"),
        ("goals_tasks_roadmap", "inline"),
        ("github_readmes", "inline"),
    ]
    # No source types were planned, so no search ran at all.
    assert script.searches == []


async def test_planner_inventory_reports_sizes_not_content(harness):
    script = Script(
        Plan(reason="", search_query="q", source_types=["goal"], context_sections=[]),
        [Reflection(sufficient=True, reason="")],
    )
    graph = harness(script, [])

    await run_chat(graph, 5, "How am I doing?", [])

    prompt = system_prompt_of(script, "plan")
    assert "Resume on file: 34 characters" in prompt
    assert "Goals: 1" in prompt
    assert "GitHub projects with READMEs: 1 (viet/pulse)" in prompt
    assert "EXPERIENCE" not in prompt


async def test_reflect_sees_the_profile_header_and_the_chunks(harness):
    """The profile header is always in the answer's context, so reflect must see it too — without
    it the model spends the second round hunting for a target role it already has."""
    script = Script(
        Plan(reason="", search_query="q", source_types=["resume_section"], context_sections=[]),
        [Reflection(sufficient=True, reason="")],
    )
    graph = harness(script, [[chunk("resume_section", "experience", 0.031)]])

    await run_chat(graph, 5, "Do my projects fit my target role?", [])

    prompt = system_prompt_of(script, "reflect")
    assert "target role Backend Engineer" in prompt
    assert "[experience (Resume)] content of experience" in prompt


async def test_reflect_is_told_when_the_search_found_nothing(harness):
    script = Script(
        Plan(reason="", search_query="q", source_types=["obsidian_diff"], context_sections=[]),
        [Reflection(sufficient=True, reason="")],
    )
    graph = harness(script, [])

    await run_chat(graph, 5, "Who was my manager?", [])

    assert "the search returned nothing" in system_prompt_of(script, "reflect")


async def test_unknown_source_types_are_dropped(harness):
    script = Script(
        Plan(reason="", search_query="q", source_types=["task", "linkedin_profile"],
             context_sections=["items", "twitter"]),
        [Reflection(sufficient=True, reason="")],
    )
    graph = harness(script, [])

    response = await run_chat(graph, 5, "hi", [])

    assert script.searches[0][1] == ["task"]
    assert ("goals_tasks_roadmap", "inline") in decisions(response)


async def test_history_is_replayed_to_plan_and_answer(harness):
    script = Script(
        Plan(reason="", search_query="q", source_types=[], context_sections=[]),
        [Reflection(sufficient=True, reason="")],
    )
    graph = harness(script, [])
    history = [
        ChatMessage(role="user", content="Tell me about Pulse."),
        ChatMessage(role="assistant", content="It is your career platform."),
    ]

    await run_chat(graph, 5, "What language is it in?", history)

    for kind in ("plan", "answer"):
        messages = next(m for name, m in script.llm_calls if name == kind)
        assert [m.content for m in messages[1:]] == [
            "Tell me about Pulse.",
            "It is your career platform.",
            "What language is it in?",
        ]
