from langgraph.graph import END, START, StateGraph
from langgraph.graph.state import CompiledStateGraph

from agent.graph.nodes import (
    answer_node,
    plan_node,
    reflect_node,
    retrieve_node,
    route_after_reflect,
)
from agent.graph.state import AgentState
from agent.models import ChatMessage, ChatResponse, SourceCitation


def build_graph() -> CompiledStateGraph:
    """plan → retrieve → reflect ⇄ retrieve → answer.

    Written as an explicit StateGraph rather than a prebuilt ReAct agent: the loop is a single
    named conditional edge, which is the whole point of the exercise.
    """
    builder = StateGraph(AgentState)
    builder.add_node("plan", plan_node)
    builder.add_node("retrieve", retrieve_node)
    builder.add_node("reflect", reflect_node)
    builder.add_node("answer", answer_node)

    builder.add_edge(START, "plan")
    builder.add_edge("plan", "retrieve")
    builder.add_edge("retrieve", "reflect")
    builder.add_conditional_edges(
        "reflect", route_after_reflect, {"retrieve": "retrieve", "answer": "answer"}
    )
    builder.add_edge("answer", END)

    return builder.compile()


async def run_chat(
    graph: CompiledStateGraph, user_id: int, message: str, history: list[ChatMessage]
) -> ChatResponse:
    final = await graph.ainvoke(
        {
            "user_id": user_id,
            "message": message,
            "history": history,
            "profile": {},
            "items": {},
            "github": {},
            "plan": "",
            "search_query": message,
            "sources_to_query": [],
            "context_sections": [],
            "retrieved": [],
            "iterations": 0,
            "sufficient": False,
            "trajectory": [],
            "answer": "",
        }
    )
    return ChatResponse(
        message=final["answer"],
        sources=[
            SourceCitation(
                source_type=chunk.source_type, source_key=chunk.source_key, score=chunk.score
            )
            for chunk in final["retrieved"]
        ],
        trajectory=final["trajectory"],
    )
