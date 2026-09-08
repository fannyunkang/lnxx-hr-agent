from __future__ import annotations

from typing import Any

import httpx

from hr_agent.api.schemas import ToolRequest, ToolResponse, UserContext
from hr_agent.config import Settings

LEGACY_SELF_TOOL_ALIASES = {
    "hr_get_employee_profile": "employee_profile",
    "hr_get_attendance_summary": "attendance_summary",
    "hr_get_leave_balance": "leave_balance",
    "hr_get_approval_status": "approval_status",
}


class BackendToolClient:
    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._transport = transport

    async def execute(
        self,
        tool_name: str,
        request_id: str,
        user_context: UserContext,
        arguments: dict[str, Any] | None = None,
    ) -> ToolResponse:
        payload = ToolRequest(
            request_id=request_id,
            user_context=user_context,
            arguments=arguments or {},
        )
        async with httpx.AsyncClient(
            base_url=self._settings.backend_base_url,
            timeout=self._settings.request_timeout_seconds,
            transport=self._transport,
        ) as client:
            response = await client.post(
                f"/internal/v1/tools/{tool_name}",
                headers={"X-Agent-Service-Token": self._settings.service_token},
                json=payload.model_dump(by_alias=True),
            )
            response.raise_for_status()
            result = ToolResponse.model_validate(response.json())
            if self._should_retry_legacy_self_tool(tool_name, user_context, arguments or {}, result):
                legacy_response = await client.post(
                    f"/internal/v1/tools/{LEGACY_SELF_TOOL_ALIASES[tool_name]}",
                    headers={"X-Agent-Service-Token": self._settings.service_token},
                    json=ToolRequest(
                        request_id=request_id,
                        user_context=user_context,
                        arguments={},
                    ).model_dump(by_alias=True),
                )
                legacy_response.raise_for_status()
                return ToolResponse.model_validate(legacy_response.json())
            return result

    @staticmethod
    def _should_retry_legacy_self_tool(
        tool_name: str,
        user_context: UserContext,
        arguments: dict[str, Any],
        result: ToolResponse,
    ) -> bool:
        target = str(arguments.get("targetEmployeeId", "")).strip()
        is_self_query = not target or target == user_context.employee_id
        return (
            tool_name in LEGACY_SELF_TOOL_ALIASES
            and is_self_query
            and not result.success
            and isinstance(result.error, dict)
            and result.error.get("code") == "TOOL_NOT_FOUND"
        )
