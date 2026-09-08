from functools import lru_cache

from redis.asyncio import Redis

from hr_agent.config import get_settings
from hr_agent.conversation import ConversationStore
from hr_agent.graph.builder import HrAgentGraph
from hr_agent.model_client import OpenAICompatibleModelClient
from hr_agent.run_store import RunStore
from hr_agent.tools.backend_client import BackendToolClient
from hr_agent.tools.mcp_client import McpToolClient
from hr_agent.trace import TraceStore


@lru_cache
def get_agent_graph() -> HrAgentGraph:
    settings = get_settings()
    model_client = OpenAICompatibleModelClient(settings)
    redis = Redis.from_url(settings.redis_url, decode_responses=True) if settings.redis_enabled else None
    conversations = ConversationStore(
        settings.conversation_max_messages, settings.conversation_ttl_seconds, redis
    )
    traces = TraceStore(redis, settings.trace_ttl_seconds)
    runs = RunStore(redis, settings.trace_ttl_seconds)
    backend_tools = BackendToolClient(settings)
    tool_client = (
        McpToolClient(settings)
        if settings.tool_transport.strip().lower() == "mcp"
        else backend_tools
    )
    return HrAgentGraph(settings, tool_client, model_client, conversations, traces, runs)
