from hr_agent.api.schemas import AgentRunRequest, ToolResponse
from hr_agent.config import Settings
from hr_agent.graph.builder import HrAgentGraph
from hr_agent.model_client import DemoRuleAgentModelClient, ModelCompleted, ModelDelta, ToolCall


class FakeToolClient:
    async def execute(self, tool_name, request_id, user_context, arguments=None):
        assert user_context.employee_id == "E1001"
        if tool_name in {"leave_balance", "hr_get_leave_balance"}:
            return ToolResponse(
                tool=tool_name,
                success=True,
                data={"annualTotal": 10.0, "annualUsed": 3.0, "annualRemaining": 7.0},
            )
        if tool_name in {"approval_status", "hr_get_approval_status"}:
            return ToolResponse(
                tool=tool_name,
                success=True,
                data=[{"title": "9月年假申请", "status": "APPROVING"}],
            )
        return ToolResponse(tool=tool_name, success=True, data=[])


class FakeModelClient:
    """模拟一个会调用 leave_balance 工具后生成回答的 OpenAI 兼容模型。"""

    def __init__(self):
        self._turns = 0

    async def stream_turn(self, messages, tools, model=None):
        del model
        self._turns += 1
        if self._turns == 1:
            yield ModelDelta("")
            yield ModelCompleted(
                "",
                [ToolCall(call_id="call_1", name="leave_balance", arguments="{}")],
            )
            return
        answer = "你的年假剩余 7.0 天。"
        yield ModelDelta(answer)
        yield ModelCompleted(answer)


async def test_graph_calls_structured_backend_tool():
    graph = HrAgentGraph(Settings(), FakeToolClient(), model_client=FakeModelClient())
    request = AgentRunRequest.model_validate(
        {
            "requestId": "request-001",
            "conversationId": "conversation-001",
            "message": "我还有多少年假？",
            "userContext": {
                "username": "employee",
                "employeeId": "E1001",
                "role": "EMPLOYEE",
                "department": "研发部",
            },
        }
    )
    result = await graph.run(request)
    assert result.intent == "LEAVE_BALANCE"
    assert result.tools == ["leave_balance"]
    assert "剩余 7.0 天" in result.answer


async def test_graph_emits_route_and_queryable_node_trace():
    graph = HrAgentGraph(Settings(redis_enabled=False), FakeToolClient(), model_client=FakeModelClient())
    request = AgentRunRequest.model_validate(
        {"requestId": "request-trace-001", "conversationId": "conversation-trace-001",
         "message": "我还有多少年假？", "userContext": {"username": "employee",
         "employeeId": "E1001", "role": "EMPLOYEE", "department": "研发部"}}
    )
    events = [event async for event in graph.events(request)]
    names = [event.name for event in events]
    assert names.index("status") < names.index("route") < names.index("tool_start")
    trace_event = next(event for event in events if event.name == "trace")
    trace = await graph.trace(trace_event.data["trace_id"])
    assert trace is not None
    assert {node["name"] for node in trace["nodes"]} >= {"load_memory", "route", "model", "tool"}


async def test_graph_stores_run_events_and_checkpoint():
    graph = HrAgentGraph(
        Settings(redis_enabled=False, model_options="demo-rule-agent", model_name="demo-rule-agent"),
        FakeToolClient(),
        model_client=DemoRuleAgentModelClient(),
    )
    request = AgentRunRequest.model_validate(
        {"requestId": "request-run-001", "conversationId": "conversation-run-001",
         "message": "我还有多少年假？", "model": "demo-rule-agent", "userContext": {"username": "employee",
         "employeeId": "E1001", "role": "EMPLOYEE", "department": "研发部"}}
    )
    result = await graph.run(request)
    events = await graph.run_events("request-run-001", after=0)
    checkpoint = await graph.checkpoint("request-run-001")

    assert result.model == "demo-rule-agent"
    assert any(event["event"] == "answer" for event in events)
    assert checkpoint is not None
    assert checkpoint["stage"] == "COMPLETED"


async def test_graph_supervisor_dispatches_multiple_child_agents():
    graph = HrAgentGraph(
        Settings(redis_enabled=False, model_options="demo-rule-agent", model_name="demo-rule-agent"),
        FakeToolClient(),
        model_client=DemoRuleAgentModelClient(),
    )
    request = AgentRunRequest.model_validate(
        {"requestId": "request-multi-001", "conversationId": "conversation-multi-001",
         "message": "我还剩多少年假？顺便查一下我的审批进度。", "model": "demo-rule-agent",
         "userContext": {"username": "employee", "employeeId": "E1001", "role": "EMPLOYEE", "department": "研发部"}}
    )
    events = [event async for event in graph.events(request)]
    answer = next(event.data for event in events if event.name == "answer")
    agent_names = [
        event.data["name"]
        for event in events
        if event.name == "agent_start"
    ]

    assert answer["intent"] == "MULTI_AGENT"
    assert set(agent_names) >= {"LeaveAgent", "ApprovalAgent"}
    assert set(answer["tools"]) >= {"hr_get_leave_balance", "hr_get_approval_status"}
