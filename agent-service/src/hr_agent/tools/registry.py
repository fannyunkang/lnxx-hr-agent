from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from time import perf_counter
from typing import Any

import httpx
from pydantic import ValidationError

from hr_agent.api.schemas import UserContext
from hr_agent.model_client import ToolCall
from hr_agent.tools.backend_client import BackendToolClient


@dataclass(frozen=True)
class RegisteredTool:
    name: str
    description: str
    parameters: dict[str, Any]
    intent: str

    def api_definition(self) -> dict[str, Any]:
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": self.parameters,
            },
        }


@dataclass(frozen=True)
class ToolExecutionResult:
    call: ToolCall
    success: bool
    output: Any
    duration_ms: int


EMPTY_PARAMETERS = {
    "type": "object",
    "properties": {},
    "additionalProperties": False,
}

TARGET_EMPLOYEE_PARAMETERS = {
    "type": "object",
    "properties": {
        "targetEmployeeId": {
            "type": "string",
            "description": "目标员工编号。普通员工只能为空或本人；HR/ADMIN 可指定其他员工。",
            "maxLength": 32,
        }
    },
    "additionalProperties": False,
}

EMPLOYEE_SEARCH_PARAMETERS = {
    "type": "object",
    "properties": {
        "query": {"type": "string", "minLength": 1, "maxLength": 100},
        "department": {"type": "string", "maxLength": 64},
    },
    "additionalProperties": False,
}


class ToolRegistry:
    def __init__(self, client: BackendToolClient):
        self._client = client
        definitions = (
            RegisteredTool("employee_profile", "查询当前登录员工的个人档案。", EMPTY_PARAMETERS, "EMPLOYEE_PROFILE"),
            RegisteredTool("attendance_summary", "查询当前登录员工的当月考勤汇总。", EMPTY_PARAMETERS, "ATTENDANCE"),
            RegisteredTool("leave_balance", "查询当前登录员工的年假余额。", EMPTY_PARAMETERS, "LEAVE_BALANCE"),
            RegisteredTool("approval_status", "查询当前登录员工最近的审批进度。", EMPTY_PARAMETERS, "APPROVAL_STATUS"),
            RegisteredTool("hr_search_employee", "HR/ADMIN 按员工编号、姓名、部门搜索员工。普通员工不可用。", EMPLOYEE_SEARCH_PARAMETERS, "HR_EMPLOYEE_SEARCH"),
            RegisteredTool("hr_get_employee_profile", "查询员工档案。普通员工只能查自己，HR/ADMIN 可按 targetEmployeeId 查询他人。", TARGET_EMPLOYEE_PARAMETERS, "EMPLOYEE_PROFILE"),
            RegisteredTool("hr_get_attendance_summary", "查询员工最近考勤。普通员工只能查自己，HR/ADMIN 可按 targetEmployeeId 查询他人。", TARGET_EMPLOYEE_PARAMETERS, "ATTENDANCE"),
            RegisteredTool("hr_get_leave_balance", "查询员工年假余额。普通员工只能查自己，HR/ADMIN 可按 targetEmployeeId 查询他人。", TARGET_EMPLOYEE_PARAMETERS, "LEAVE_BALANCE"),
            RegisteredTool("hr_get_approval_status", "查询员工最近审批。普通员工只能查自己，HR/ADMIN 可按 targetEmployeeId 查询他人。", TARGET_EMPLOYEE_PARAMETERS, "APPROVAL_STATUS"),
            RegisteredTool(
                "knowledge_search",
                "按当前用户的角色和部门权限搜索公司制度。只在回答公司制度时调用。",
                {
                    "type": "object",
                    "properties": {
                        "query": {"type": "string", "minLength": 1, "maxLength": 500}
                    },
                    "required": ["query"],
                    "additionalProperties": False,
                },
                "KNOWLEDGE_SEARCH",
            ),
        )
        self._tools = {tool.name: tool for tool in definitions}

    @property
    def api_definitions(self) -> list[dict[str, Any]]:
        return [tool.api_definition() for tool in self._tools.values()]

    def api_definitions_for(self, names: tuple[str, ...] | list[str]) -> list[dict[str, Any]]:
        return [self._tools[name].api_definition() for name in names if name in self._tools]

    def intent_for(self, names: list[str]) -> str:
        intents = {self._tools[name].intent for name in names if name in self._tools}
        if not intents:
            return "GENERAL"
        return next(iter(intents)) if len(intents) == 1 else "MULTI_TOOL"

    async def execute_many(
        self,
        calls: list[ToolCall],
        request_id: str,
        user_context: UserContext,
    ) -> list[ToolExecutionResult]:
        return list(
            await asyncio.gather(
                *(self._execute(call, request_id, user_context) for call in calls)
            )
        )

    async def _execute(
        self, call: ToolCall, request_id: str, user_context: UserContext
    ) -> ToolExecutionResult:
        started = perf_counter()
        try:
            arguments = self._validate_arguments(call)
            result = await self._client.execute(
                call.name, request_id, user_context, arguments
            )
            output = result.data if result.success else {"error": result.error or "tool failed"}
            return ToolExecutionResult(
                call, result.success, output, int((perf_counter() - started) * 1000)
            )
        except (
            httpx.HTTPError,
            TypeError,
            ValueError,
            json.JSONDecodeError,
            ValidationError,
        ) as exc:
            return ToolExecutionResult(
                call,
                False,
                {
                    "error": {
                        "code": "INVALID_TOOL_CALL"
                        if isinstance(exc, (TypeError, ValueError, json.JSONDecodeError))
                        else "TOOL_EXECUTION_FAILED",
                        "message": str(exc),
                    }
                },
                int((perf_counter() - started) * 1000),
            )

    def _validate_arguments(self, call: ToolCall) -> dict[str, Any]:
        if call.name not in self._tools:
            raise ValueError(f"工具 {call.name} 未注册")
        arguments = json.loads(call.arguments)
        if not isinstance(arguments, dict):
            raise TypeError("工具参数必须是 JSON 对象")
        if call.name == "knowledge_search":
            if set(arguments) != {"query"}:
                raise ValueError("knowledge_search 只接受 query 参数")
            query = arguments.get("query")
            if not isinstance(query, str) or not query.strip() or len(query) > 500:
                raise ValueError("query 必须是 1 到 500 字的字符串")
            return {"query": query.strip()}
        if call.name == "hr_search_employee":
            allowed = {"query", "department"}
            if not set(arguments).issubset(allowed):
                raise ValueError("hr_search_employee 只接受 query 和 department 参数")
            query = arguments.get("query", "")
            department = arguments.get("department", "")
            if query and not isinstance(query, str):
                raise ValueError("query 必须是字符串")
            if department and not isinstance(department, str):
                raise ValueError("department 必须是字符串")
            return {key: value.strip() for key, value in {"query": query, "department": department}.items() if value and value.strip()}
        if call.name.startswith("hr_get_"):
            if set(arguments) - {"targetEmployeeId"}:
                raise ValueError(f"{call.name} 只接受 targetEmployeeId 参数")
            target = arguments.get("targetEmployeeId", "")
            if target and not isinstance(target, str):
                raise ValueError("targetEmployeeId 必须是字符串")
            return {"targetEmployeeId": target.strip()} if target and target.strip() else {}
        if arguments:
            raise ValueError(f"{call.name} 不接受参数")
        return {}
