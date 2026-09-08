from __future__ import annotations

import re
from enum import Enum
from typing import Any
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field


def to_camel(value: str) -> str:
    parts = value.split("_")
    return parts[0] + "".join(part.capitalize() for part in parts[1:])


class ApiModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel, populate_by_name=True, serialize_by_alias=True, extra="forbid"
    )


class UserRole(str, Enum):
    EMPLOYEE = "EMPLOYEE"
    HR = "HR"
    ADMIN = "ADMIN"


class UserContext(ApiModel):
    username: str = Field(min_length=1, max_length=64)
    employee_id: str = Field(pattern=r"^[A-Za-z0-9_-]{2,32}$")
    role: UserRole
    department: str = Field(default="", max_length=64)


class AgentRunRequest(ApiModel):
    request_id: str = Field(default_factory=lambda: str(uuid4()), min_length=8, max_length=64)
    conversation_id: str = Field(default_factory=lambda: str(uuid4()), min_length=8, max_length=64)
    message: str = Field(min_length=1, max_length=500)
    user_context: UserContext
    model: str | None = Field(default=None, min_length=1, max_length=80)


class ConversationClearRequest(ApiModel):
    request_id: str = Field(min_length=8, max_length=64)
    user_context: UserContext


class ToolExecution(ApiModel):
    name: str
    status: str = "SUCCESS"
    duration_ms: int = 0


class AgentRunResponse(ApiModel):
    request_id: str
    conversation_id: str
    trace_id: str
    answer: str
    intent: str
    tools: list[str] = Field(default_factory=list)
    citations: list[str] = Field(default_factory=list)
    model: str
    status: str = "COMPLETED"


class ModelOption(ApiModel):
    id: str
    label: str
    active: bool = False


class ToolRequest(ApiModel):
    request_id: str
    user_context: UserContext
    arguments: dict[str, Any] = Field(default_factory=dict)


class ToolResponse(ApiModel):
    tool: str
    success: bool
    data: Any = None
    error: dict[str, Any] | None = None


def valid_service_token(value: str) -> bool:
    return bool(re.fullmatch(r"[\x21-\x7e]{8,256}", value))
