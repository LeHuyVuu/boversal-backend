import json
import os

os.environ.setdefault("GEMINI_API_KEY", "test-key")

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.api.routes import inference_routes, rag_routes


@pytest.fixture()
def client():
    app.dependency_overrides.clear()
    app.dependency_overrides[inference_routes.get_current_user] = lambda: {"sub": "test-user"}
    app.dependency_overrides[rag_routes.get_current_user] = lambda: {"sub": "test-user"}
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()


def test_health_endpoint(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "healthy"
    assert response.json()["service"] == "Intelligen"


def test_gateway_test_endpoint(client):
    response = client.get("/api/gateway-test")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_infer_requires_auth(client):
    app.dependency_overrides.clear()

    response = client.post("/api/ai/infer", json={"prompt": "hello"})

    assert response.status_code == 401
    assert response.json()["detail"] == "Missing token"


def test_infer_returns_engine_result(client, monkeypatch):
    monkeypatch.setattr(inference_routes, "run_inference", lambda prompt, context: "answer")

    response = client.post(
        "/api/ai/infer",
        json={"prompt": "hello", "context": {"project": "demo"}},
    )

    assert response.status_code == 200
    assert response.json() == {"result": "answer", "model": "placeholder"}


def test_infer_validates_prompt(client):
    response = client.post("/api/ai/infer", json={"context": {}})

    assert response.status_code == 422


def test_rag_requires_auth(client):
    app.dependency_overrides.clear()

    response = client.post("/api/ai/rag/chat", json={"question": "hello"})

    assert response.status_code == 401
    assert response.json()["detail"] == "Missing token"


def test_rag_streams_sources_tokens_and_done(client, monkeypatch):
    monkeypatch.setattr(rag_routes, "retrieve_context", lambda question: [("context", "test.md", 0.9)])
    monkeypatch.setattr(rag_routes, "generate_answer_stream", lambda question, rows: iter(["hello", " world"]))

    response = client.post("/api/ai/rag/chat", json={"question": "hello"})

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = [line[6:] for line in response.text.splitlines() if line.startswith("data: ")]
    assert json.loads(events[0]) == {
        "type": "sources",
        "sources": [{"content": "context", "source": "test.md", "similarity": 0.9}],
    }
    assert json.loads(events[1]) == {"type": "token", "text": "hello"}
    assert json.loads(events[2]) == {"type": "token", "text": " world"}
    assert json.loads(events[3]) == {"type": "done"}


def test_rag_validates_question(client):
    response = client.post("/api/ai/rag/chat", json={"question": ""})

    assert response.status_code == 422


def test_registered_api_routes_are_covered():
    routes = {(route.path, tuple(sorted(route.methods or []))) for route in app.routes}

    assert ("/health", ("GET",)) in routes
    assert ("/api/gateway-test", ("GET",)) in routes
    assert ("/api/ai/infer", ("POST",)) in routes
    assert ("/api/ai/rag/chat", ("POST",)) in routes
