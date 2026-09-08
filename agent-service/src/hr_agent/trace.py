from __future__ import annotations

import json
import logging
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from time import perf_counter
from typing import Any

from redis.asyncio import Redis
from redis.exceptions import RedisError

logger = logging.getLogger(__name__)


@dataclass
class TraceNode:
    name: str
    started_at: str
    ended_at: str = ""
    duration_ms: int = 0
    status: str = "RUNNING"
    summary: dict[str, Any] = field(default_factory=dict)
    error_code: str | None = None


class NodeSpan:
    def __init__(self, trace: RunTrace, name: str, summary: dict[str, Any] | None = None):
        self._started = perf_counter()
        self.node = TraceNode(name, datetime.now(timezone.utc).isoformat(), summary=summary or {})
        trace.nodes.append(self.node)

    def finish(self, status: str = "SUCCESS", error_code: str | None = None, **summary: Any) -> None:
        self.node.ended_at = datetime.now(timezone.utc).isoformat()
        self.node.duration_ms = int((perf_counter() - self._started) * 1000)
        self.node.status = status
        self.node.error_code = error_code
        self.node.summary.update(summary)


@dataclass
class RunTrace:
    trace_id: str
    request_id: str
    conversation_id: str
    username: str
    runtime: str = "python"
    model: str = ""
    status: str = "RUNNING"
    intent: str = "GENERAL"
    tools: list[str] = field(default_factory=list)
    citations: list[str] = field(default_factory=list)
    retries: int = 0
    error_code: str | None = None
    created_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    nodes: list[TraceNode] = field(default_factory=list)

    def span(self, name: str, **summary: Any) -> NodeSpan:
        return NodeSpan(self, name, summary)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class TraceStore:
    def __init__(self, redis: Redis | None, ttl_seconds: int):
        self._redis = redis
        self._ttl = ttl_seconds
        self._memory: dict[str, dict[str, Any]] = {}

    async def save(self, trace: RunTrace) -> None:
        data = trace.to_dict()
        self._memory[trace.trace_id] = data
        if self._redis:
            try:
                await self._redis.setex(f"hr-agent:trace:{trace.trace_id}", self._ttl, json.dumps(data, ensure_ascii=False))
            except RedisError as exc:
                logger.warning("Redis trace write failed; memory copy retained: %s", exc)

    async def get(self, trace_id: str) -> dict[str, Any] | None:
        if self._redis:
            try:
                value = await self._redis.get(f"hr-agent:trace:{trace_id}")
                if value:
                    return json.loads(value)
            except (RedisError, json.JSONDecodeError) as exc:
                logger.warning("Redis trace read failed; using memory fallback: %s", exc)
        return self._memory.get(trace_id)
