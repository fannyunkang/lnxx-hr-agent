from __future__ import annotations

import json
from typing import Any
from uuid import uuid4

import httpx

from hr_agent.api.schemas import ToolResponse, UserContext
from hr_agent.config import Settings

MCP_TOOL_NAMES = {
    "employee_profile": "employeeProfile",
    "attendance_summary": "attendanceSummary",
    "leave_balance": "leaveBalance",
    "approval_status": "approvalStatus",
    "knowledge_search": "knowledgeSearch",
    "hr_search_employee": "hrSearchEmployee",
    "hr_get_employee_profile": "hrGetEmployeeProfile",
    "hr_get_attendance_summary": "hrGetAttendanceSummary",
    "hr_get_leave_balance": "hrGetLeaveBalance",
    "hr_get_approval_status": "hrGetApprovalStatus",
}


class McpToolClient:
    """Minimal Streamable HTTP MCP client for the Java read-only HR MCP server."""

    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ):
        self._settings = settings
        self._transport = transport
        self._session_id: str | None = None
        self._initialized = False

    async def execute(
        self,
        tool_name: str,
        request_id: str,
        user_context: UserContext,
        arguments: dict[str, Any] | None = None,
    ) -> ToolResponse:
        try:
            mcp_name = MCP_TOOL_NAMES[tool_name]
            mcp_arguments = self._trusted_arguments(tool_name, user_context, arguments or {})
            result = await self._call_mcp("tools/call", {"name": mcp_name, "arguments": mcp_arguments})
            if result.get("isError") is True:
                return ToolResponse(
                    tool=tool_name,
                    success=False,
                    error={"code": "MCP_TOOL_ERROR", "message": self._content_text(result)},
                )
            normalized = self._normalize(tool_name, result)
            return ToolResponse(tool=tool_name, success=True, data=normalized)
        except (KeyError, TypeError, RuntimeError, httpx.HTTPError, json.JSONDecodeError) as exc:
            return ToolResponse(
                tool=tool_name,
                success=False,
                error={"code": "MCP_CLIENT_ERROR", "message": str(exc)},
            )

    async def _call_mcp(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        if not self._initialized:
            await self._initialize()
        response = await self._post_json_rpc(method, params or {})
        if "error" in response:
            raise RuntimeError(response["error"])
        result = response.get("result")
        if not isinstance(result, dict):
            raise TypeError("MCP response did not contain an object result")
        return result

    async def _initialize(self) -> None:
        response = await self._post_json_rpc(
            "initialize",
            {
                "protocolVersion": "2025-03-26",
                "capabilities": {},
                "clientInfo": {"name": "lnxx-hr-agent-service", "version": "0.1.0"},
            },
            include_session=False,
        )
        if "error" in response:
            raise RuntimeError(response["error"])
        self._initialized = True
        await self._post_json_rpc("notifications/initialized", {}, notification=True)

    async def _post_json_rpc(
        self,
        method: str,
        params: dict[str, Any],
        *,
        include_session: bool = True,
        notification: bool = False,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"jsonrpc": "2.0", "method": method, "params": params}
        if not notification:
            payload["id"] = str(uuid4())
        headers = {
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
        }
        if include_session and self._session_id:
            headers["Mcp-Session-Id"] = self._session_id

        async with httpx.AsyncClient(
            base_url=self._settings.mcp_base_url,
            timeout=self._settings.request_timeout_seconds,
            transport=self._transport,
        ) as client:
            response = await client.post(self._settings.mcp_endpoint, headers=headers, json=payload)
            response.raise_for_status()
            self._session_id = response.headers.get("Mcp-Session-Id", self._session_id)
            if notification:
                return {}
            return self._decode_response(response)

    @staticmethod
    def _decode_response(response: httpx.Response) -> dict[str, Any]:
        content_type = response.headers.get("content-type", "")
        if "text/event-stream" not in content_type:
            return response.json()
        for line in response.text.splitlines():
            if line.startswith("data:"):
                return json.loads(line.removeprefix("data:").strip())
        raise RuntimeError("MCP event stream did not contain a data frame")

    @staticmethod
    def _trusted_arguments(
        tool_name: str, user_context: UserContext, arguments: dict[str, Any]
    ) -> dict[str, Any]:
        trusted = {
            "employeeId": user_context.employee_id,
        }
        if tool_name == "hr_search_employee":
            trusted.update(
                {
                    "query": arguments.get("query", ""),
                    "department": arguments.get("department", ""),
                    "role": user_context.role.value,
                }
            )
        if tool_name.startswith("hr_get_"):
            trusted.update(
                {
                    "targetEmployeeId": arguments.get("targetEmployeeId", ""),
                    "role": user_context.role.value,
                }
            )
        if tool_name == "knowledge_search":
            trusted.update(
                {
                    "query": arguments["query"],
                    "department": user_context.department,
                    "role": user_context.role.value,
                }
            )
        return trusted

    def _normalize(self, tool_name: str, result: dict[str, Any]) -> Any:
        value = result.get("structuredContent")
        if value is None:
            value = self._content_value(result)
        if tool_name == "knowledge_search" and isinstance(value, list):
            return [self._normalize_knowledge_row(row) for row in value]
        return value

    @staticmethod
    def _content_value(result: dict[str, Any]) -> Any:
        text = McpToolClient._content_text(result)
        if not text:
            return result
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return text

    @staticmethod
    def _content_text(result: dict[str, Any]) -> str:
        content = result.get("content")
        if not isinstance(content, list):
            return ""
        pieces: list[str] = []
        for item in content:
            if isinstance(item, dict) and isinstance(item.get("text"), str):
                pieces.append(item["text"])
        return "\n".join(pieces)

    @staticmethod
    def _normalize_knowledge_row(row: Any) -> Any:
        if not isinstance(row, dict):
            return row
        document_id = row.get("documentId", row.get("document_id"))
        version = row.get("version", 1)
        chunk_number = row.get("chunkNumber", row.get("chunk_number"))
        normalized = dict(row)
        if "documentId" not in normalized and document_id is not None:
            normalized["documentId"] = document_id
        if "chunkNumber" not in normalized and chunk_number is not None:
            normalized["chunkNumber"] = chunk_number
        if "citation" not in normalized and document_id is not None and chunk_number is not None:
            normalized["citation"] = f"[KB-{document_id}-{version}-{chunk_number}]"
        return normalized
