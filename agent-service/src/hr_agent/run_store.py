from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from redis.asyncio import Redis
from redis.exceptions import RedisError

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class StoredRunEvent:
    sequence: int
    event: str
    data: dict[str, Any]
    created_at: str


class RunStore:
    """Redis-first run event/checkpoint store used for SSE replay and coarse resume."""

    def __init__(self, redis: Redis | None, ttl_seconds: int):
        self._redis = redis
        self._ttl = ttl_seconds
        self._events: dict[str, list[StoredRunEvent]] = {}
        self._checkpoints: dict[str, dict[str, Any]] = {}

    async def append_event(self, run_id: str, event: str, data: dict[str, Any]) -> StoredRunEvent:
        sequence = len(self._events.get(run_id, [])) + 1
        stored = StoredRunEvent(sequence, event, data, datetime.now(timezone.utc).isoformat())
        self._events.setdefault(run_id, []).append(stored)
        if self._redis:
            try:
                key = self._events_key(run_id)
                pipe = self._redis.pipeline()
                pipe.rpush(key, json.dumps(stored.__dict__, ensure_ascii=False))
                pipe.expire(key, self._ttl)
                await pipe.execute()
            except RedisError as exc:
                logger.warning("Redis run event write failed; memory copy retained: %s", exc)
        return stored

    async def events_after(self, run_id: str, after: int = 0) -> list[dict[str, Any]]:
        events = self._events.get(run_id)
        if self._redis:
            try:
                values = await self._redis.lrange(self._events_key(run_id), 0, -1)
                if values:
                    events = [StoredRunEvent(**json.loads(value)) for value in values]
            except (RedisError, json.JSONDecodeError, TypeError) as exc:
                logger.warning("Redis run event read failed; using memory fallback: %s", exc)
        return [event.__dict__ for event in (events or []) if event.sequence > after]

    async def save_checkpoint(self, run_id: str, checkpoint: dict[str, Any]) -> None:
        data = {**checkpoint, "updatedAt": datetime.now(timezone.utc).isoformat()}
        self._checkpoints[run_id] = data
        if self._redis:
            try:
                await self._redis.setex(
                    self._checkpoint_key(run_id),
                    self._ttl,
                    json.dumps(data, ensure_ascii=False),
                )
            except RedisError as exc:
                logger.warning("Redis checkpoint write failed; memory copy retained: %s", exc)

    async def checkpoint(self, run_id: str) -> dict[str, Any] | None:
        if self._redis:
            try:
                value = await self._redis.get(self._checkpoint_key(run_id))
                if value:
                    return json.loads(value)
            except (RedisError, json.JSONDecodeError) as exc:
                logger.warning("Redis checkpoint read failed; using memory fallback: %s", exc)
        return self._checkpoints.get(run_id)

    @staticmethod
    def _events_key(run_id: str) -> str:
        return f"hr-agent:run:{run_id}:events"

    @staticmethod
    def _checkpoint_key(run_id: str) -> str:
        return f"hr-agent:run:{run_id}:checkpoint"
