from typing import Any, TypedDict


class AgentState(TypedDict, total=False):
    request_id: str
    conversation_id: str
    message: str
    user_context: dict[str, Any]
    intent: str
    selected_tool: str
    tool_result: Any
    tools: list[str]
    citations: list[str]
    answer: str
    error: str
