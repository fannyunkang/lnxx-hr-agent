import json

import httpx

from hr_agent.api.schemas import UserContext
from hr_agent.config import Settings
from hr_agent.tools.mcp_client import McpToolClient


def user_context() -> UserContext:
    return UserContext.model_validate(
        {
            "username": "employee",
            "employeeId": "E1001",
            "role": "EMPLOYEE",
            "department": "研发部",
        }
    )


async def test_mcp_client_injects_trusted_identity_and_normalizes_citation():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        calls.append(payload)
        if payload["method"] == "initialize":
            return httpx.Response(
                200,
                headers={"Mcp-Session-Id": "session-1"},
                json={"jsonrpc": "2.0", "id": payload["id"], "result": {"capabilities": {}}},
            )
        if payload["method"] == "notifications/initialized":
            assert request.headers["Mcp-Session-Id"] == "session-1"
            return httpx.Response(202)
        assert payload["method"] == "tools/call"
        assert payload["params"]["name"] == "knowledgeSearch"
        assert payload["params"]["arguments"] == {
            "query": "年假制度",
            "employeeId": "E1001",
            "department": "研发部",
            "role": "EMPLOYEE",
        }
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": payload["id"],
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps(
                                [
                                    {
                                        "document_id": "leave-policy",
                                        "version": 2,
                                        "chunk_number": 3,
                                        "content": "年假规则",
                                    }
                                ]
                            ),
                        }
                    ]
                },
            },
        )

    client = McpToolClient(
        Settings(mcp_base_url="http://mcp.test"),
        transport=httpx.MockTransport(handler),
    )
    response = await client.execute(
        "knowledge_search",
        "request-001",
        user_context(),
        {"query": "年假制度", "employeeId": "E9999", "role": "ADMIN"},
    )

    assert response.success is True
    assert response.data[0]["documentId"] == "leave-policy"
    assert response.data[0]["chunkNumber"] == 3
    assert response.data[0]["citation"] == "[KB-leave-policy-2-3]"
    assert [call["method"] for call in calls] == [
        "initialize",
        "notifications/initialized",
        "tools/call",
    ]


async def test_mcp_client_returns_mcp_error_when_server_unavailable():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(503)

    client = McpToolClient(
        Settings(mcp_base_url="http://mcp.test"),
        transport=httpx.MockTransport(handler),
    )

    response = await client.execute("leave_balance", "request-001", user_context(), {})

    assert response.success is False
    assert response.error["code"] == "MCP_CLIENT_ERROR"


async def test_mcp_client_returns_tool_error_without_backend_fallback():
    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        if payload["method"] == "initialize":
            return httpx.Response(
                200,
                headers={"Mcp-Session-Id": "session-1"},
                json={"jsonrpc": "2.0", "id": payload["id"], "result": {"capabilities": {}}},
            )
        if payload["method"] == "notifications/initialized":
            return httpx.Response(202)
        return httpx.Response(
            200,
            json={
                "jsonrpc": "2.0",
                "id": payload["id"],
                "result": {
                    "isError": True,
                    "content": [{"type": "text", "text": "Failed to obtain JDBC Connection"}],
                },
            },
        )

    client = McpToolClient(
        Settings(mcp_base_url="http://mcp.test"),
        transport=httpx.MockTransport(handler),
    )

    response = await client.execute("leave_balance", "request-001", user_context(), {})

    assert response.success is False
    assert response.error["code"] == "MCP_TOOL_ERROR"
