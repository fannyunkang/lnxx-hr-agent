from __future__ import annotations

import asyncio
import json
import logging
from typing import Any

from redis.asyncio import Redis
from redis.exceptions import RedisError

logger = logging.getLogger(__name__)


class ConversationStore:
    """Redis-first conversation window with an in-process development fallback."""

    def __init__(self, max_messages: int, ttl_seconds: int, redis: Redis | None = None):
        self._max_messages = max_messages
        self._ttl = ttl_seconds
        self._redis = redis
        self._history: dict[str, list[dict[str, Any]]] = {}
        self._locks: dict[str, asyncio.Lock] = {}

    def lock(self, key: str) -> asyncio.Lock:
        return self._locks.setdefault(key, asyncio.Lock())

    async def get(self, key: str) -> list[dict[str, Any]]:
        if self._redis:
            try:
                values = await self._redis.lrange(self._key(key), 0, -1)
                if values:
                    return [json.loads(value) for value in values]
            except (RedisError, json.JSONDecodeError) as exc:
                logger.warning("Redis conversation read failed; using memory fallback: %s", exc)
        return [dict(message) for message in self._history.get(key, [])]

    async def append_turn(self, key: str, user_message: str, answer: str) -> None:
        turn = [{"role": "user", "content": user_message}, {"role": "assistant", "content": answer}]
        messages = self._history.setdefault(key, []) + turn
        self._history[key] = messages[-self._max_messages:]
        if self._redis:
            try:
                redis_key = self._key(key)
                pipe = self._redis.pipeline()
                pipe.rpush(redis_key, *(json.dumps(item, ensure_ascii=False) for item in turn))
                pipe.ltrim(redis_key, -self._max_messages, -1)
                pipe.expire(redis_key, self._ttl)
                await pipe.execute()
            except RedisError as exc:
                logger.warning("Redis conversation write failed; memory copy retained: %s", exc)

    async def clear(self, key: str) -> None:
        self._history.pop(key, None)
        self._locks.pop(key, None)
        if self._redis:
            try:
                await self._redis.delete(self._key(key))
            except RedisError as exc:
                logger.warning("Redis conversation clear failed: %s", exc)

    @staticmethod
    def _key(key: str) -> str:
        return f"hr-agent:conversation:{key}"
