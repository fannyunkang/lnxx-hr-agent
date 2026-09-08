import pytest
from fastapi.testclient import TestClient

from hr_agent.config import Settings
from hr_agent.dependencies import get_agent_graph
from hr_agent.graph.builder import HrAgentGraph
from hr_agent.main import app
from hr_agent.model_client import ModelCompleted, ModelDelta
from hr_agent.tools.backend_client import BackendToolClient

client = TestClient(app)
headers = {"X-Agent-Service-Token": "change-this-in-production"}
payload = {
    "requestId": "request-api-001",
    "conversationId": "conversation-api-001",
    "message": "你好",
    "userContext": {
        "username": "employee",
        "employeeId": "E1001",
        "role": "EMPLOYEE",
        "department": "研发部",
    },
}


class StubModelClient:
    """直接返回问候回答、不调用任何工具的模型。"""

    async def stream_turn(self, messages, tools, model=None):
        del messages, tools, model
        answer = "你好，我是人力资源助手。"
        yield ModelDelta(answer)
        yield ModelCompleted(answer)


def build_stub_graph():
    settings = Settings()
    return HrAgentGraph(
        settings,
        BackendToolClient(settings),
        model_client=StubModelClient(),
    )


@pytest.fixture()
def stub_agent_graph():
    app.dependency_overrides[get_agent_graph] = build_stub_graph
    yield
    app.dependency_overrides.clear()


def test_agent_api_requires_service_token():
    assert client.post("/internal/v1/agent/runs", json=payload).status_code == 401


def test_general_route_and_sse_contract(stub_agent_graph):
    response = client.post("/internal/v1/agent/runs", headers=headers, json=payload)
    assert response.status_code == 200
    body = response.json()
    assert body["intent"] == "GENERAL"
    assert body["conversationId"] == "conversation-api-001"
    assert body["answer"] == "你好，我是人力资源助手。"

    stream = client.post("/internal/v1/agent/runs/stream", headers=headers, json=payload)
    assert stream.status_code == 200
    assert "event: status" in stream.text
    assert "event: answer_delta" in stream.text
    assert "event: answer" in stream.text
    assert "event: done" in stream.text


def test_unconfigured_model_returns_503():
    app.dependency_overrides.clear()
    settings = Settings(model_base_url="", model_api_key="", model_name="", redis_enabled=False)
    app.dependency_overrides[get_agent_graph] = lambda: HrAgentGraph(
        settings, BackendToolClient(settings)
    )
    response = client.post("/internal/v1/agent/runs", headers=headers, json=payload)
    assert response.status_code == 503
    assert response.json()["detail"]["code"] == "MODEL_NOT_CONFIGURED"

    stream = client.post("/internal/v1/agent/runs/stream", headers=headers, json=payload)
    assert stream.status_code == 200
    assert "event: error" in stream.text
    assert "event: done" in stream.text
    app.dependency_overrides.clear()


def test_clear_conversation_contract():
    clear_payload = {
        "requestId": "request-clear-001",
        "userContext": payload["userContext"],
    }
    response = client.post(
        "/internal/v1/agent/conversations/conversation-api-001/clear",
        headers=headers,
        json=clear_payload,
    )
    assert response.status_code == 204
