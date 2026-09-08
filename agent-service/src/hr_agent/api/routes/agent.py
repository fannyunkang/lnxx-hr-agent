import json
import logging
from typing import Annotated
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Path, Query, Response, status
from fastapi.responses import StreamingResponse

from hr_agent.api.schemas import (
    AgentRunRequest,
    AgentRunResponse,
    ConversationClearRequest,
    ModelOption,
)
from hr_agent.config import get_settings
from hr_agent.dependencies import get_agent_graph
from hr_agent.graph.builder import AgentRunError, HrAgentGraph
from hr_agent.security import require_service_token

router = APIRouter(
    prefix="/internal/v1/agent",
    tags=["agent"],
    dependencies=[Depends(require_service_token)],
)
logger = logging.getLogger(__name__)
AgentGraphDependency = Annotated[HrAgentGraph, Depends(get_agent_graph)]
ConversationId = Annotated[str, Path(pattern=r"^[A-Za-z0-9_-]{8,64}$")]


@router.post("/runs", response_model=AgentRunResponse)
async def run_agent(request: AgentRunRequest, graph: AgentGraphDependency) -> AgentRunResponse:
    try:
        return await graph.run(request)
    except AgentRunError as exc:
        logger.exception("Agent run failed trace_id=%s code=%s", exc.trace_id, exc.code)
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=exc.event_data()) from exc


@router.get("/models", response_model=list[ModelOption])
async def list_models() -> list[ModelOption]:
    settings = get_settings()
    return [
        ModelOption(id=model, label=model, active=model == settings.model_name)
        for model in settings.available_models
    ]


@router.post("/runs/stream")
async def stream_agent(
    request: AgentRunRequest,
    graph: AgentGraphDependency,
) -> StreamingResponse:
    async def events():
        try:
            async for event in graph.events(request):
                yield sse(event.name, event.data)
        except AgentRunError as exc:
            logger.exception("Agent stream failed trace_id=%s code=%s", exc.trace_id, exc.code)
            yield sse("error", exc.event_data())
            yield sse("done", {"status": "FAILED", "traceId": exc.trace_id})
        except Exception:
            trace_id = str(uuid4())
            logger.exception("Unexpected agent stream failure trace_id=%s", trace_id)
            yield sse(
                "error",
                {
                    "code": "AGENT_EXECUTION_FAILED",
                    "message": "Agent 服务暂时不可用",
                    "retryable": True,
                    "traceId": trace_id,
                },
            )
            yield sse("done", {"status": "FAILED", "traceId": trace_id})

    return StreamingResponse(events(), media_type="text/event-stream")


@router.post(
    "/conversations/{conversation_id}/clear",
    status_code=status.HTTP_204_NO_CONTENT,
)
async def clear_conversation(
    conversation_id: ConversationId,
    request: ConversationClearRequest,
    graph: AgentGraphDependency,
) -> Response:
    await graph.clear(request.user_context, conversation_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/traces/{trace_id}")
async def get_trace(trace_id: str, graph: AgentGraphDependency) -> dict:
    trace = await graph.trace(trace_id)
    if trace is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="trace not found")
    return trace


@router.get("/runs/{run_id}/events")
async def get_run_events(
    run_id: str,
    graph: AgentGraphDependency,
    after: int = Query(default=0, ge=0),
) -> list[dict]:
    return await graph.run_events(run_id, after)


@router.get("/runs/{run_id}/checkpoint")
async def get_run_checkpoint(run_id: str, graph: AgentGraphDependency) -> dict:
    checkpoint = await graph.checkpoint(run_id)
    if checkpoint is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="checkpoint not found")
    return checkpoint


def sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
