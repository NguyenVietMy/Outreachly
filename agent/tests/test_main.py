import pytest
from fastapi.testclient import TestClient

from agent.models import ChatResponse, SourceCitation, TrajectoryStep


@pytest.fixture(autouse=True)
def stub_graph(monkeypatch):
    """These tests are about the HTTP contract; the graph itself is covered by test_graph.py."""

    async def fake_run_chat(graph, user_id, message, history):
        return ChatResponse(
            message="answer",
            sources=[
                SourceCitation(source_type="resume_section", source_key="experience", score=0.031)
            ],
            trajectory=[TrajectoryStep(source_type="plan", decision="retrieve", reason="because")],
        )

    monkeypatch.setattr("agent.main.run_chat", fake_run_chat)


def test_health_is_open_and_ok(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_chat_requires_token(client: TestClient) -> None:
    response = client.post("/chat", json={"userId": 1, "message": "hi"})
    assert response.status_code == 401


def test_chat_rejects_wrong_token(client: TestClient) -> None:
    response = client.post(
        "/chat",
        headers={"X-Internal-Token": "nope"},
        json={"userId": 1, "message": "hi"},
    )
    assert response.status_code == 401


def test_chat_returns_contract_shape(client: TestClient, token: str) -> None:
    response = client.post(
        "/chat",
        headers={"X-Internal-Token": token},
        json={
            "userId": 1,
            "message": "what did I ship last week?",
            "history": [{"role": "user", "content": "hi"}],
        },
    )
    assert response.status_code == 200

    body = response.json()
    assert set(body) == {"message", "sources", "trajectory"}
    assert isinstance(body["message"], str)
    assert set(body["sources"][0]) == {"sourceType", "sourceKey", "score"}
    assert set(body["trajectory"][0]) == {"sourceType", "decision", "reason"}


def test_chat_rejects_malformed_body(client: TestClient, token: str) -> None:
    response = client.post("/chat", headers={"X-Internal-Token": token}, json={"message": "hi"})
    assert response.status_code == 422
