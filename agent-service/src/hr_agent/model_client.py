from __future__ import annotations

import asyncio
import json
import random
import re
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any, Protocol

import httpx

from hr_agent.config import Settings


@dataclass(frozen=True)
class ToolCall:
    call_id: str
    name: str
    arguments: str


@dataclass(frozen=True)
class ModelDelta:
    text: str


@dataclass(frozen=True)
class ModelCompleted:
    text: str
    tool_calls: list[ToolCall] = field(default_factory=list)


ModelEvent = ModelDelta | ModelCompleted


class ModelClient(Protocol):
    async def stream_turn(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        model: str | None = None,
    ) -> AsyncIterator[ModelEvent]: ...


class ModelClientError(RuntimeError):
    def __init__(self, code: str, message: str, retryable: bool = False):
        super().__init__(message)
        self.code = code
        self.retryable = retryable


class UnavailableModelClient:
    async def stream_turn(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        model: str | None = None,
    ) -> AsyncIterator[ModelEvent]:
        del messages, tools, model
        raise ModelClientError(
            "MODEL_NOT_CONFIGURED",
            "尚未配置模型服务，请设置 HR_AGENT_MODEL_BASE_URL、HR_AGENT_MODEL_API_KEY 和 HR_AGENT_MODEL_NAME",
        )
        yield  # pragma: no cover - keeps this method an async generator


class DemoRuleAgentModelClient:
    """Local deterministic model for demos; still drives the real tool loop."""

    async def stream_turn(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        model: str | None = None,
    ) -> AsyncIterator[ModelEvent]:
        del tools, model
        latest_tool = next((item for item in reversed(messages) if item.get("role") == "tool"), None)
        if latest_tool:
            answer = self._answer_from_tool(messages, str(latest_tool.get("content", "{}")))
            yield ModelDelta(answer)
            yield ModelCompleted(answer)
            return

        user_message = next(
            (str(item.get("content", "")) for item in reversed(messages) if item.get("role") == "user"),
            "",
        )
        tool_name, arguments = self._select_tool(user_message, messages)
        if tool_name:
            yield ModelCompleted(
                "",
                [ToolCall(f"call_demo_{tool_name}", tool_name, json.dumps(arguments, ensure_ascii=False))],
            )
            return
        answer = "你好，我是人力知识 Agent。你可以问我员工档案、考勤、年假、审批、公司制度，也可以让 HR 查询授权范围内的员工信息。"
        yield ModelDelta(answer)
        yield ModelCompleted(answer)

    @staticmethod
    def _select_tool(message: str, messages: list[dict[str, Any]]) -> tuple[str, dict[str, Any]]:
        normalized = message.lower()
        allowed_tools = DemoRuleAgentModelClient._allowed_tool_names(messages)
        previous_target = DemoRuleAgentModelClient._previous_target_employee_id(messages)
        target = DemoRuleAgentModelClient._extract_employee_id(message) or previous_target

        search_requested = any(word in normalized for word in ("搜索", "查找", "列表", "名单", "所有", "search", "list"))
        employee_requested = any(word in normalized for word in ("员工", "人员", "同事", "employee", "staff"))
        if search_requested and employee_requested and "hr_search_employee" in allowed_tools:
            return "hr_search_employee", DemoRuleAgentModelClient._extract_search_arguments(message)
        if any(word in normalized for word in ("年假", "假期", "剩余", "用了多少", "还有多少", "leave")) and "hr_get_leave_balance" in allowed_tools:
            return ("hr_get_leave_balance", {"targetEmployeeId": target} if target else {})
        if any(word in normalized for word in ("考勤", "迟到", "缺勤", "出勤", "attendance")) and "hr_get_attendance_summary" in allowed_tools:
            return ("hr_get_attendance_summary", {"targetEmployeeId": target} if target else {})
        if any(word in normalized for word in ("审批", "申请", "进度", "这个员工", "approval")) and "hr_get_approval_status" in allowed_tools:
            return ("hr_get_approval_status", {"targetEmployeeId": target} if target else {})
        if any(word in normalized for word in ("档案", "信息", "岗位", "部门", "profile", "employee")) and "hr_get_employee_profile" in allowed_tools:
            return ("hr_get_employee_profile", {"targetEmployeeId": target} if target else {})
        if any(word in normalized for word in ("制度", "规定", "流程", "规范", "上班", "弹性", "knowledge", "policy")) and "knowledge_search" in allowed_tools:
            return "knowledge_search", {"query": message}
        return "", {}

    @staticmethod
    def _allowed_tool_names(messages: list[dict[str, Any]]) -> set[str]:
        for item in messages:
            if item.get("role") != "system":
                continue
            content = str(item.get("content") or "")
            marker = "可用工具："
            if marker in content:
                return {name.strip() for name in content.split(marker, 1)[1].split("。", 1)[0].split(",") if name.strip()}
        return {
            "hr_search_employee",
            "hr_get_employee_profile",
            "hr_get_attendance_summary",
            "hr_get_leave_balance",
            "hr_get_approval_status",
            "knowledge_search",
        }

    @staticmethod
    def _extract_employee_id(message: str) -> str:
        match = re.search(r"\bE\d{4,}\b", message, flags=re.IGNORECASE)
        return match.group(0).upper() if match else ""

    @staticmethod
    def _previous_target_employee_id(messages: list[dict[str, Any]]) -> str:
        for item in reversed(messages):
            if item.get("role") != "assistant":
                continue
            for tool_call in item.get("tool_calls") or []:
                try:
                    arguments = json.loads(tool_call.get("function", {}).get("arguments", "{}"))
                except json.JSONDecodeError:
                    continue
                target = str(arguments.get("targetEmployeeId") or "")
                if target:
                    return target.upper()
        for item in reversed(messages):
            content = str(item.get("content") or "")
            target = DemoRuleAgentModelClient._extract_employee_id(content)
            if target:
                return target
        return ""

    @staticmethod
    def _extract_search_arguments(message: str) -> dict[str, str]:
        target = DemoRuleAgentModelClient._extract_employee_id(message)
        if target:
            return {"query": target}
        cleaned = message
        for word in ("搜索", "查找", "查询", "所有", "员工列表", "员工", "人员", "名单", "请", "帮我", "然后"):
            cleaned = cleaned.replace(word, "")
        department = re.search(r"([\u4e00-\u9fa5A-Za-z0-9]+部)", cleaned)
        if department:
            return {"department": department.group(1)}
        return {"query": cleaned.strip() or message}

    @staticmethod
    def _answer_from_tool(messages: list[dict[str, Any]], content: str) -> str:
        try:
            data = json.loads(content)
        except json.JSONDecodeError:
            data = content
        call_name = ""
        for item in reversed(messages):
            if item.get("role") == "assistant" and item.get("tool_calls"):
                call_name = item["tool_calls"][0]["function"]["name"]
                break
        if isinstance(data, dict) and data.get("error"):
            return f"工具调用失败：{data['error']}"
        if call_name.endswith("leave_balance") and isinstance(data, dict):
            return f"年假总额 {data.get('annualTotal')} 天，已使用 {data.get('annualUsed')} 天，剩余 {data.get('annualRemaining')} 天。"
        if call_name.endswith("attendance_summary") and isinstance(data, dict):
            return f"最近考勤月份 {data.get('attendanceMonth')}：应出勤 {data.get('workDays')} 天，实际出勤 {data.get('attendedDays')} 天，迟到 {data.get('lateCount')} 次，缺勤 {data.get('absentCount')} 次。"
        if call_name.endswith("employee_profile") and isinstance(data, dict):
            return f"员工 {data.get('employeeId')}：{data.get('name')}，部门 {data.get('department')}，岗位 {data.get('position')}，邮箱 {data.get('emailMasked')}，手机 {data.get('phoneMasked')}。"
        if call_name.endswith("approval_status") and isinstance(data, list):
            if not data:
                return "当前没有查询到最近审批记录。"
            rows = [f"{item.get('title')}（{item.get('status')}）" for item in data[:5] if isinstance(item, dict)]
            return "最近审批：" + "；".join(rows) + "。"
        if call_name == "hr_search_employee" and isinstance(data, list):
            rows = [
                f"{item.get('employeeId')} {item.get('name')} {item.get('department')} {item.get('position')}"
                for item in data[:10]
                if isinstance(item, dict)
            ]
            return "查询到员工：" + "；".join(rows) if rows else "没有查询到匹配员工。"
        if call_name == "knowledge_search" and isinstance(data, list):
            if not data:
                return "没有检索到匹配的制度内容。"
            first = data[0]
            if isinstance(first, dict):
                return f"{first.get('title')}：{first.get('content')} {first.get('citation', '')}".strip()
        return f"工具返回：{json.dumps(data, ensure_ascii=False)}"


class OpenAICompatibleModelClient:
    """Small, provider-neutral client for OpenAI-compatible chat completions."""

    def __init__(self, settings: Settings, transport: httpx.AsyncBaseTransport | None = None):
        self._settings = settings
        self._timeout = settings.model_timeout_seconds
        self._transport = transport

    async def stream_turn(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]],
        model: str | None = None,
    ) -> AsyncIterator[ModelEvent]:
        endpoint = self._settings.endpoint_for(model)
        if endpoint is None:
            async for event in DemoRuleAgentModelClient().stream_turn(messages, tools, model):
                yield event
            return
        if not endpoint.base_url.strip() or not endpoint.api_key.strip() or not endpoint.model.strip():
            raise ModelClientError(
                "MODEL_NOT_CONFIGURED",
                f"模型 {model or self._settings.model_name} 尚未配置 base_url/api_key/model",
            )
        payload = {
            "model": endpoint.model,
            "messages": messages,
            "tools": tools,
            "tool_choice": "auto",
            "stream": True,
        }
        emitted = False
        for attempt in range(3):
            try:
                async with httpx.AsyncClient(
                    timeout=self._timeout, transport=self._transport
                ) as client, client.stream(
                    "POST",
                    f"{endpoint.base_url.rstrip('/')}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {endpoint.api_key}",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                ) as response:
                    if response.status_code in {401, 403}:
                        raise ModelClientError(
                            "MODEL_AUTH_FAILED", "模型服务鉴权失败，请检查 API Key"
                        )
                    if response.status_code == 429 or response.status_code >= 500:
                        if attempt < 2:
                            await asyncio.sleep(self._retry_delay(response, attempt))
                            continue
                        raise ModelClientError(
                            "MODEL_TEMPORARILY_UNAVAILABLE",
                            "模型服务繁忙，请稍后重试",
                            retryable=True,
                        )
                    if response.status_code >= 400:
                        raise ModelClientError(
                            "MODEL_REQUEST_REJECTED",
                            f"模型服务拒绝请求（HTTP {response.status_code}）",
                        )

                    text_parts: list[str] = []
                    tool_parts: dict[int, dict[str, str]] = {}
                    async for line in response.aiter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line[5:].strip()
                        if not data or data == "[DONE]":
                            continue
                        chunk = json.loads(data)
                        choices = chunk.get("choices") or []
                        if not choices:
                            continue
                        delta = choices[0].get("delta") or {}
                        content = delta.get("content")
                        if isinstance(content, str) and content:
                            emitted = True
                            text_parts.append(content)
                            yield ModelDelta(content)
                        for part in delta.get("tool_calls") or []:
                            index = int(part.get("index", 0))
                            current = tool_parts.setdefault(
                                index, {"id": "", "name": "", "arguments": ""}
                            )
                            if part.get("id"):
                                current["id"] += part["id"]
                            function = part.get("function") or {}
                            current["name"] += function.get("name") or ""
                            current["arguments"] += function.get("arguments") or ""

                    calls = [
                        ToolCall(
                            call_id=value["id"] or f"call_{index}",
                            name=value["name"],
                            arguments=value["arguments"] or "{}",
                        )
                        for index, value in sorted(tool_parts.items())
                    ]
                    yield ModelCompleted("".join(text_parts), calls)
                    return
            except ModelClientError:
                raise
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                if emitted or attempt == 2:
                    raise ModelClientError(
                        "MODEL_CONNECTION_FAILED",
                        "无法连接模型服务，请检查地址和网络",
                        retryable=True,
                    ) from exc
                await asyncio.sleep(0.5 * (2**attempt) + random.uniform(0, 0.15))
            except (json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
                raise ModelClientError(
                    "MODEL_PROTOCOL_ERROR", "模型服务返回了无法解析的流式响应"
                ) from exc

    @staticmethod
    def _retry_delay(response: httpx.Response, attempt: int) -> float:
        retry_after = response.headers.get("retry-after", "")
        try:
            return min(float(retry_after), 5.0) if retry_after else 0.5 * (2**attempt)
        except ValueError:
            return 0.5 * (2**attempt)
